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

@Service
public class PropertyService {

    private final GeneralTDBService generalTDBService;

    @Value("${ontoformgenerator.ontologies.baseIRI}")
    private String baseIRI;

    private final Logger logger = LoggerFactory.getLogger(OntologyOverviewService.class);

    public PropertyService(GeneralTDBService generalTDBService) {
        this.generalTDBService = generalTDBService;
    }


    // Retrieval methods

    public Literal getDatatypePropertyValueFromIndividual(Dataset dataset,
                                                          String ontologyName,
                                                          Resource domainIndividual,
                                                          String propertyName) {
        OntModel ontModel = generalTDBService.getOntModel(dataset.getNamedModel(ontologyName));
        var prop = domainIndividual.getProperty(ontModel.getProperty(baseIRI + "/" + ontologyName + "#" + propertyName));
        if (prop == null) return null;
        return prop.getObject().asLiteral();
    }

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

    public String getLabelOfIndividual(Dataset dataset,
                                       String ontologyName,
                                       String uri) {
        OntModel ontModel = generalTDBService.getOntModel(dataset.getNamedModel(ontologyName));
        var individual = ontModel.getIndividual(uri);
        return individual.getLabel(null);
    }


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

    public Property getPropertyFromOntology(Dataset dataset,
                                            String ontologyName,
                                            String propertyName) {
        OntModel ontModel = generalTDBService.getOntModel(dataset.getNamedModel(ontologyName));
        return ontModel.listAllOntProperties().filterKeep(property -> property.getLocalName().equals(propertyName)).next();
    }

    public OntProperty getPropertyFromOntologyByIRI(Dataset dataset,
                                                    String ontologyName,
                                                    String propertyIRI) {
        OntModel ontModel = generalTDBService.getOntModel(dataset.getNamedModel(ontologyName));
        return ontModel.getOntProperty(propertyIRI);
    }






    // ----------------------------------------------------------------------------------------------------------------
    // ----------------------------------------------------------------------------------------------------------------

    // Creation methods

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

    public void addDatatypePropertyToIndividual(Dataset dataset,
                                                String ontologyName,
                                                Individual domainIndividual,
                                                String propertyName,
                                                String value, RDFDatatype datatype) {
        var ontModel = generalTDBService.getOntModel(dataset.getNamedModel(ontologyName));
        domainIndividual.addProperty(ontModel.getProperty(baseIRI + "/" + ontologyName + "#" + propertyName), value,
                datatype);
    }

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
