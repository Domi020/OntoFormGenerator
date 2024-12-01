package fau.fdm.OntoFormGenerator.tdb;

import fau.fdm.OntoFormGenerator.service.OntologyOverviewService;
import org.apache.jena.datatypes.RDFDatatype;
import org.apache.jena.ontology.Individual;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.ontology.OntProperty;
import org.apache.jena.query.Dataset;
import org.apache.jena.rdf.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Service for handling properties in the ontology.
 */
@Service
public class PropertyService {

    private final GeneralTDBService generalTDBService;

    @Value("${ontoformgenerator.ontologies.baseIRI}")
    private String baseIRI;

    private final Logger logger = LoggerFactory.getLogger(PropertyService.class);

    public PropertyService(GeneralTDBService generalTDBService) {
        this.generalTDBService = generalTDBService;
    }


    // Retrieval methods

    /**
     * Get the datatype property value of a set property of an individual.
     * @param dataset The dataset to use.
     * @param ontologyName The name of the ontology.
     * @param domainIndividual The individual to get the property value from.
     * @param propertyName The name of the property.
     * @return The value of the property as Literal (has to be cast to the wished datatype).
     */
    public Literal getDatatypePropertyValueFromIndividual(Dataset dataset,
                                                          String ontologyName,
                                                          Resource domainIndividual,
                                                          String propertyName) {
        OntModel ontModel = generalTDBService.getOntModel(dataset.getNamedModel(ontologyName));
        var prop = domainIndividual.getProperty(ontModel.getProperty(baseIRI + "/" + ontologyName + "#" + propertyName));
        if (prop == null) return null;
        return prop.getObject().asLiteral();
    }

    /**
     * Get the object property value of a set property of an individual.
     * Should only be used if there only exists one value.
     * @param dataset The dataset to use.
     * @param ontologyName The name of the ontology.
     * @param domainIndividual The individual to get the property value from.
     * @param propertyName The name of the property.
     * @return The value of the property as Resource.
     */
    public Resource getObjectPropertyValueFromIndividual(Dataset dataset,
                                                         String ontologyName,
                                                         Resource domainIndividual,
                                                         String propertyName) {
        var ontModel = generalTDBService.getOntModel(dataset.getNamedModel(ontologyName));
        var prop = domainIndividual.getPropertyResourceValue(ontModel.getProperty(baseIRI + "/" + ontologyName + "#" + propertyName));
        if (prop == null) {
            return null;
        } else {
            return prop.asResource();
        }
    }

    /**
     * Get the label of an individual.
     * @param dataset The dataset to use.
     * @param ontologyName The name of the ontology.
     * @param uri The URI of the individual.
     * @return The label of the individual.
     */
    public String getLabelOfIndividual(Dataset dataset,
                                       String ontologyName,
                                       String uri) {
        OntModel ontModel = generalTDBService.getOntModel(dataset.getNamedModel(ontologyName));
        var individual = ontModel.getIndividual(uri);
        return individual.getLabel(null);
    }

    /**
     * Get all object property values of a set property of an individual.
     * @param dataset The dataset to use.
     * @param ontologyName The name of the ontology.
     * @param domainIndividual The individual to get the property value from.
     * @param propertyName The name of the property.
     * @return The values of the property as Resources.
     */
    public List<Resource> getMultipleObjectPropertyValuesFromIndividual(Dataset dataset,
                                                                        String ontologyName,
                                                                        Individual domainIndividual,
                                                                        String propertyName) {
        var ontModel = generalTDBService.getOntModel(dataset.getNamedModel(ontologyName));
        var values = domainIndividual.listProperties(ontModel.getProperty(baseIRI + "/" + ontologyName + "#" + propertyName));
        List<Resource> resources = new ArrayList<>();
        values.forEachRemaining(val -> resources.add(val.getObject().asResource()));
        return resources;
    }

    /**
     * Get the property from an ontology by its local name.
     * @param dataset The dataset to use.
     * @param ontologyName The name of the ontology.
     * @param propertyName The name of the property.
     * @return The property with the given name.
     */
    public Property getPropertyFromOntology(Dataset dataset,
                                            String ontologyName,
                                            String propertyName) {
        OntModel ontModel = generalTDBService.getOntModel(dataset.getNamedModel(ontologyName));
        return ontModel.listAllOntProperties().filterKeep(property -> property.getLocalName().equals(propertyName)).next();
    }

    /**
     * Get the property from an ontology by its IRI.
     * @param dataset The dataset to use.
     * @param ontologyName The name of the ontology.
     * @param propertyIRI The IRI of the property.
     * @return The property with the given IRI.
     */
    public OntProperty getPropertyFromOntologyByIRI(Dataset dataset,
                                                    String ontologyName,
                                                    String propertyIRI) {
        OntModel ontModel = generalTDBService.getOntModel(dataset.getNamedModel(ontologyName));
        return ontModel.getOntProperty(propertyIRI);
    }






    // ----------------------------------------------------------------------------------------------------------------
    // ----------------------------------------------------------------------------------------------------------------

    // Creation methods

    /**
     * Add new object property value to an individual.
     * @param dataset The dataset to use.
     * @param ontologyName The name of the ontology.
     * @param domainIndividual The individual to add the property to.
     * @param propertyName The name of the property.
     * @param otherIndividualURI The URI of the other individual (object value).
     */
    public void addObjectPropertyToIndividual(Dataset dataset,
                                              String ontologyName,
                                              Individual domainIndividual,
                                              String propertyName,
                                              String otherIndividualURI) {
        var ontModel = generalTDBService.getOntModel(dataset.getNamedModel(ontologyName));
        var otherIndividual = ontModel.getIndividual(otherIndividualURI);
        domainIndividual.addProperty(ontModel.getProperty(baseIRI + "/" + ontologyName + "#" + propertyName),
                otherIndividual);
    }

    /**
     * Add new datatype property value to an individual.
     * @param dataset The dataset to use.
     * @param ontologyName The name of the ontology.
     * @param domainIndividual The individual to add the property to.
     * @param propertyName The name of the property.
     * @param value The value of the property.
     * @param datatype The datatype of the property, so that the value is correctly cast in the knowledge base.
     */
    public void addDatatypePropertyToIndividual(Dataset dataset,
                                                String ontologyName,
                                                Individual domainIndividual,
                                                String propertyName,
                                                String value, RDFDatatype datatype) {
        var ontModel = generalTDBService.getOntModel(dataset.getNamedModel(ontologyName));
        domainIndividual.addProperty(ontModel.getProperty(baseIRI + "/" + ontologyName + "#" + propertyName), value,
                datatype);
    }

    /**
     * Create a new annotation property in the ontology.
     * @param dataset The dataset to use.
     * @param ontologyName The name of the ontology.
     * @param propertyName The local name of the property.
     * @param label The label of the property.
     * @param comment The comment of the property.
     */
    public void createAnnotationProperty(Dataset dataset,
                                         String ontologyName,
                                         String propertyName,
                                         String label,
                                         String comment) {
        var ontModel = generalTDBService.getOntModel(dataset.getNamedModel(ontologyName));
        var property = ontModel.createAnnotationProperty(baseIRI + "/" + ontologyName + "#" + propertyName);
        property.addLabel(label, null);
        property.addComment(comment, null);
    }



    // ----------------------------------------------------------------------------------------------------------------
    // ----------------------------------------------------------------------------------------------------------------

    // Deletion methods

    /**
     * Remove an object property value from an individual.
     * @param dataset The dataset to use.
     * @param ontologyName The name of the ontology.
     * @param domainIndividual The individual to remove the property from.
     * @param propertyName The name of the property.
     */
    public void removePropertyValueFromIndividual(Dataset dataset,
                                                  String ontologyName,
                                                  Individual domainIndividual,
                                                  String propertyName) {
        var ontModel = generalTDBService.getOntModel(dataset.getNamedModel(ontologyName));
        var prop = domainIndividual.getProperty(ontModel.getProperty(baseIRI + "/" + ontologyName + "#" + propertyName));
        if (prop != null) {
            prop.remove();
        }
    }

    /**
     * Remove an object property value from an individual.
     * Removes a specific value if multiple values exist.
     * @param dataset The dataset to use.
     * @param ontologyName The name of the ontology.
     * @param domainIndividual The individual to remove the property from.
     * @param propertyName The name of the property.
     * @param value The specific value to remove.
     */
    public void removePropertyValueFromIndividual(Dataset dataset,
                                                  String ontologyName,
                                                  Individual domainIndividual,
                                                  String propertyName,
                                                  Object value) {
        var ontModel = generalTDBService.getOntModel(dataset.getNamedModel(ontologyName));
        var prop = getPropertyFromOntology(dataset, ontologyName, propertyName);
        ontModel.listStatements(domainIndividual.asResource(), prop, (RDFNode) null).forEach(
                stmt -> {
                    if (stmt.getObject().isLiteral()) {
                        if (stmt.getObject().asLiteral().getValue().equals(value)) {
                            stmt.remove();
                        }
                    } else {
                        if (stmt.getObject().asResource().getLocalName().equals(value)) {
                            stmt.remove();
                        }
                    }
                }
        );
    }





    // ----------------------------------------------------------------------------------------------------------------
    // ----------------------------------------------------------------------------------------------------------------

    // Other methods

    /**
     * Search for properties in an ontology by a search query.
     * @param dataset The dataset to use.
     * @param ontologyName The name of the ontology.
     * @param domainIri The IRI of the domain class.
     * @param query The search query.
     * @return A list of properties that match the search query.
     */
    public List<OntProperty> searchProperties(Dataset dataset, String ontologyName, String domainIri, String query) {
        OntModel ontModel = generalTDBService.getOntModel(dataset.getNamedModel(ontologyName));
        return ontModel.listAllOntProperties().filterKeep(property -> {
            var ontDomain = property.getDomain();
            String q = query.toLowerCase();
            var label = property.getLabel(null);
            var comment = property.getComment(null);
            return  (ontDomain == null || ontDomain.getURI().equals(domainIri)) && (
                    label != null && label.toLowerCase().contains(q) ||
                    comment != null && comment.toLowerCase().contains(q) ||
                    property.getLocalName().toLowerCase().contains(q));
        }).toList();
    }
}
