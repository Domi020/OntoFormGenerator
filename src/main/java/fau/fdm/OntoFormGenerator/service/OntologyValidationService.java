package fau.fdm.OntoFormGenerator.service;

import fau.fdm.OntoFormGenerator.data.OntologyClass;
import fau.fdm.OntoFormGenerator.data.OntologyProperty;
import fau.fdm.OntoFormGenerator.data.ValidationResult;
import fau.fdm.OntoFormGenerator.tdb.GeneralTDBService;
import fau.fdm.OntoFormGenerator.tdb.PropertyService;
import fau.fdm.OntoFormGenerator.tdb.TDBConnection;
import fau.fdm.OntoFormGenerator.validation.FactValidator;
import fau.fdm.OntoFormGenerator.validation.HermitValidator;
import fau.fdm.OntoFormGenerator.validation.Validator;
import fau.fdm.OntoFormGenerator.validation.ValidatorMode;
import lombok.Getter;
import lombok.Setter;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.ontology.OntModelSpec;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.ModelFactory;
import org.semanticweb.owlapi.model.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for validating ontologies.
 */
@Service
public class OntologyValidationService {

    private final PropertyService propertyService;

    private final RestTemplate restTemplate;
    private final GeneralTDBService generalTDBService;

    @Value("${ontoformgenerator.ontologyDirectory}")
    private String ontologyDirectory;

    @Value("${ontoformgenerator.validator.mode}")
    private ValidatorMode mode;

    public OntologyValidationService(PropertyService propertyService, GeneralTDBService generalTDBService) {
        this.propertyService = propertyService;
        this.restTemplate = new RestTemplate();
        this.generalTDBService = generalTDBService;
    }

    /**
     * Check if a property name is valid - i.e. does not contain any filtered words.
     * @param newPropertyName The name of the property to check.
     * @return A result object containing the property name and whether it is valid.
     */
    public PropertyNamingValidationResult checkNaming(String newPropertyName) {
        // var toCheck = newPropertyName.toLowerCase();
        var wordFilters = List.of("And", "Or", "Other", "Miscellaneous");
        var result = new PropertyNamingValidationResult();
        result.setNewPropertyName(newPropertyName);
        for (var filter : wordFilters) {
            if (newPropertyName.contains(filter)) {
                result.setValid(false);
                result.setFilteredWord(filter);
                return result;
            }
        }
        result.setValid(true);
        return result;
    }

    /**
     * Check if a URI is already used in an ontology.
     * @param dataset The dataset to use.
     * @param ontologyName The name of the ontology.
     * @param URI The URI to check.
     * @return True if the URI is already used, false otherwise.
     */
    public boolean checkIfURIisUsed(Dataset dataset, String ontologyName,
                                 String URI) {
        var ontModel = generalTDBService.getOntModel(dataset.getNamedModel(ontologyName));
        var resource = ontModel.getOntResource(URI);
        return resource != null;
    }

    /**
     * Check if a new property name follows the naming schema of the ontology.
     * @param dataset The dataset to use.
     * @param ontologyName The name of the ontology.
     * @param newPropertyName The name of the new property.
     * @return A result object containing the result of the validation - including the naming schemata and whether they match.
     */
    public NamingSchemaValidationResult checkNamingSchema(Dataset dataset, String ontologyName,
                             String newPropertyName) {
        var ontologySchema = getOntologyNamingSchema(dataset, ontologyName);
        var newPropertySchema = getNamingSchema(newPropertyName);
        var result = new NamingSchemaValidationResult();
        result.setNewPropertyName(newPropertyName);
        result.setNewPropertyNamingSchema(newPropertySchema);
        result.setOntologyNamingSchema(ontologySchema);
        result.setValid(ontologySchema == newPropertySchema);
        return result;
    }

    private NamingSchema getNamingSchema(String name) {
        if (name.matches("^[A-Z]+$")) {
            return NamingSchema.ALL_CAPS;
        } else if (name.matches("^[a-z]+$")) {
            return NamingSchema.ALL_LOWER;
        } else if (name.matches("^([a-z]+[A-Z][a-z])+$")) {
            return NamingSchema.CAMEL_CASE;
        } else if (name.matches("^([a-z]+_[a-z])+$")) {
            return NamingSchema.SNAKE_CASE;
        } else {
            return NamingSchema.CAMEL_CASE;
        }
    }

    private NamingSchema getOntologyNamingSchema(Dataset dataset, String ontologyName) {
        OntModel ontModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM,
                dataset.getNamedModel(ontologyName));
        Map<NamingSchema, Integer> counter = new HashMap<>();
        ontModel.listAllOntProperties().forEachRemaining(property -> {
            var propNamingSchema = getNamingSchema(property.getLocalName());
            counter.put(propNamingSchema, counter.getOrDefault(propNamingSchema, 0) + 1);
        });
        var maxValue = counter.entrySet().stream().max(Map.Entry.comparingByValue());
        if (maxValue.isPresent()) {
            return maxValue.get().getKey();
        } else {
            return NamingSchema.CAMEL_CASE;
        }
    }

    /**
     * Find potential similar properties to a new property in an ontology - then it should not be added, probably.
     * @param dataset The dataset to use.
     * @param ontologyName The name of the ontology.
     * @param domainUri The URI of the domain class.
     * @param newPropertyName The name of the new property.
     * @return A list of potential similar properties - or an empty list if none were found.
     */
    public List<OntologyProperty> findPotentialSimilarProperties(Dataset dataset,
                                                                 String ontologyName,
                                                                 String domainUri,
                                                                 String newPropertyName) {
        List<OntologyProperty> resultList = new ArrayList<>();
        String url = "https://api.datamuse.com/words?rel_trg=" + newPropertyName;
        var response = restTemplate.getForObject(url, List.class);
        var synonyms = (List<Map<String, Object>>) response;
        assert synonyms != null;
        for (var synonym : synonyms) {
            var props = propertyService.searchProperties(dataset, ontologyName, domainUri, synonym.get("word").toString());
            for (var property : props) {
                var ontologyProperty = new OntologyProperty();
                ontologyProperty.setName(property.getLocalName());
                if (property.getDomain() != null) {
                    ontologyProperty.setDomain(new OntologyClass(property.getDomain().getLocalName(), property.getDomain().getURI()));
                }
                ontologyProperty.setObjectProperty(property.isObjectProperty());
                if (property.isObjectProperty()) {
                    if (property.getRange() != null)
                        ontologyProperty.setObjectRange(new OntologyClass(property.getRange().getLocalName(), property.getRange().getURI()));
                } else {
                    ontologyProperty.setDatatypeRange(property.getRange().getLocalName());
                }
                ontologyProperty.setRdfsLabel(property.getLabel(null));
                ontologyProperty.setRdfsComment(property.getComment(null));
                resultList.add(ontologyProperty);
            }
        }
        return resultList;
    }

    /**
     * Start a reasoner validation for an ontology.
     * @param ontologyName The name of the ontology.
     * @return The result of the validation.
     */
    public ValidationResult validateOntologyWithReasoner(String ontologyName) {
        try (TDBConnection connection = new TDBConnection(ReadWrite.READ, ontologyDirectory, ontologyName)) {
            return validateOntologyWithReasoner(connection.getDataset(), ontologyName);
        } catch (OWLOntologyCreationException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Start a reasoner validation for an ontology.
     * @param dataset The dataset to use.
     * @param ontologyName The name of the ontology.
     * @return The result of the validation.
     * @throws OWLOntologyCreationException If OWL API could not import the knowledge base for a reason.
     */
    public ValidationResult validateOntologyWithReasoner(Dataset dataset, String ontologyName)
            throws OWLOntologyCreationException {
        var tdbModel = dataset.getNamedModel(ontologyName);
        Validator validator = null;
        if (mode == ValidatorMode.HERMIT) {
            validator = new HermitValidator();
        } else if (mode == ValidatorMode.JFACT) {
            validator = new FactValidator();
        }
        return validator.validate(tdbModel);
    }

    @Getter
    @Setter
    public static class PropertyNamingValidationResult {
        String newPropertyName;
        boolean valid;
        String filteredWord = null;
    }

    @Getter
    @Setter
    public static class NamingSchemaValidationResult {
        String newPropertyName;
        NamingSchema newPropertyNamingSchema;
        NamingSchema ontologyNamingSchema;
        boolean valid;
    }

    public enum NamingSchema {
        ALL_CAPS,
        ALL_LOWER,
        CAMEL_CASE,
        SNAKE_CASE
    }
}

