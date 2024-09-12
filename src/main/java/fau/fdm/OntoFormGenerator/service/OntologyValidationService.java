package fau.fdm.OntoFormGenerator.service;

import fau.fdm.OntoFormGenerator.data.OntologyClass;
import fau.fdm.OntoFormGenerator.data.OntologyProperty;
import fau.fdm.OntoFormGenerator.tdb.PropertyService;
import lombok.Getter;
import lombok.Setter;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.ontology.OntModelSpec;
import org.apache.jena.query.Dataset;
import org.apache.jena.rdf.model.ModelFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class OntologyValidationService {

    private final PropertyService propertyService;
    @Value("${ontoformgenerator.ontologyDirectory}")
    private String ontologyDirectory;

    private final RestTemplate restTemplate;

    public OntologyValidationService(PropertyService propertyService) {
        this.propertyService = propertyService;
        this.restTemplate = new RestTemplate();
    }

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

    public List<OntologyProperty> findPotentialSimilarProperties(Dataset dataset,
                                                                 String ontologyName,
                                                                 String domainName,
                                                                 String newPropertyName) {
        List<OntologyProperty> resultList = new ArrayList<>();
        String url = "https://api.datamuse.com/words?rel_trg=" + newPropertyName;
        var response = restTemplate.getForObject(url, List.class);
        var synonyms = (List<Map<String, Object>>) response;
        assert synonyms != null;
        for (var synonym : synonyms) {
            var props = propertyService.searchProperties(dataset, ontologyName, domainName, synonym.get("word").toString());
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

    @Getter
    @Setter
    public class NamingSchemaValidationResult {
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

