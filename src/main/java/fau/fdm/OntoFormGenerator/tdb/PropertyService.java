package fau.fdm.OntoFormGenerator.tdb;

import fau.fdm.OntoFormGenerator.service.OntologyOverviewService;
import org.apache.jena.datatypes.RDFDatatype;
import org.apache.jena.ontapi.model.OntIndividual;
import org.apache.jena.ontology.Individual;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.ontology.OntModelSpec;
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

    @Value("${ontoformgenerator.ontologyDirectory}")
    private String ontologyDirectory;

    @Value("${ontoformgenerator.ontologies.baseIRI}")
    private String baseIRI;

    private final Logger logger = LoggerFactory.getLogger(OntologyOverviewService.class);

    public PropertyService(GeneralTDBService generalTDBService) {
        this.generalTDBService = generalTDBService;
    }

    public OntIndividual addObjectPropertyToIndividual(Dataset dataset,
                                                    String ontologyName,
                                                    OntIndividual domainIndividual,
                                                    String propertyName,
                                                    String otherIndividualURI) {
        var ontModel = generalTDBService.getOntModel(dataset.getNamedModel(ontologyName));
        var otherIndividual = ontModel.getIndividual(otherIndividualURI);
        domainIndividual.addProperty(ontModel.getProperty(baseIRI + "/" + ontologyName + "#" + propertyName),
                otherIndividual);
        return domainIndividual;
    }

    public OntIndividual addDatatypePropertyToIndividual(Dataset dataset,
                                                      String ontologyName,
                                                      OntIndividual domainIndividual,
                                                      String propertyName,
                                                      String value, RDFDatatype datatype) {
        var ontModel = generalTDBService.getOntModel(dataset.getNamedModel(ontologyName));
        domainIndividual.addProperty(ontModel.getProperty(baseIRI + "/" + ontologyName + "#" + propertyName), value,
                datatype);
        return domainIndividual;
    }

    public Literal getDatatypePropertyValueFromIndividual(Dataset dataset,
                                                          String ontologyName,
                                                          Resource domainIndividual,
                                                          String propertyName) {
        OntModel ontModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM,
                dataset.getNamedModel(ontologyName));
        var prop = domainIndividual.getProperty(ontModel.getProperty(baseIRI + "/" + ontologyName + "#" + propertyName));
        if (prop == null) return null;
        return prop.getObject().asLiteral();
    }

    public Resource getObjectPropertyValueFromIndividual(Dataset dataset,
                                                         String ontologyName,
                                                         OntIndividual domainIndividual,
                                                         String propertyName) {
        var ontModel = generalTDBService.getOntModel(dataset.getNamedModel(ontologyName));
        var prop = domainIndividual.getPropertyResourceValue(ontModel.getProperty(baseIRI + "/" + ontologyName + "#" + propertyName));
        if (prop == null) {
            return null;
        } else {
            return prop.asResource();
        }
    }

    public boolean checkIfPropertyValueExists(Dataset dataset,
                                              String ontologyName,
                                              OntIndividual domainIndividual,
                                              String propertyName) {
        var ontModel = generalTDBService.getOntModel(dataset.getNamedModel(ontologyName));
        var prop = domainIndividual.getPropertyResourceValue(ontModel.getProperty(baseIRI + "/" + ontologyName + "#" + propertyName));
        return prop != null;
    }

    public void removeAllPropertyValuesFromIndividual(Individual domainIndividual) {
        var props = domainIndividual.listProperties();
        props.forEachRemaining(Statement::remove);
    }

    public void removePropertyValueFromIndividual(Dataset dataset,
                                                 String ontologyName,
                                                 OntIndividual domainIndividual,
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


    public List<Resource> getMultipleObjectPropertyValuesFromIndividual(Dataset dataset,
                                                                        String ontologyName,
                                                                        OntIndividual domainIndividual,
                                                                        String propertyName) {
        //OntModel ontModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM,
        //        dataset.getNamedModel(ontologyName));
        var ontModel = generalTDBService.getOntModel(dataset.getNamedModel(ontologyName));
        var values = domainIndividual.listProperties(ontModel.getProperty(baseIRI + "/" + ontologyName + "#" + propertyName));
        List<Resource> resources = new ArrayList<>();
        values.forEachRemaining(val -> resources.add(val.getObject().asResource()));
        return resources;
    }

    public Property getPropertyFromOntology(Dataset dataset,
                                            String ontologyName,
                                            String propertyName) {
        OntModel ontModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM,
                dataset.getNamedModel(ontologyName));
        return ontModel.listAllOntProperties().filterKeep(property -> property.getLocalName().equals(propertyName)).next();
    }

    public Property getPropertyFromOntology(Dataset dataset,
                                            String ontologyName,
                                            String ontologyURI,
                                            String propertyName) {
        OntModel ontModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM,
                dataset.getNamedModel(ontologyName));
        return ontModel.getProperty(ontologyURI + "#" + propertyName);
    }

    public OntProperty getPropertyFromOntologyByIRI(Dataset dataset,
                                                    String propertyIRI) {
        return getPropertyFromOntologyByIRI(dataset, "forms", propertyIRI);
    }

    public OntProperty getPropertyFromOntologyByIRI(Dataset dataset,
                                                    String ontologyName,
                                                    String propertyIRI) {
        OntModel ontModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM,
                dataset.getNamedModel(ontologyName));
        return ontModel.getOntProperty(propertyIRI);
    }

    public List<OntProperty> searchProperties(Dataset dataset, String ontologyName, String domain, String query) {
        OntModel ontModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM,
                dataset.getNamedModel(ontologyName));
        return ontModel.listAllOntProperties().filterKeep(property -> {
            var ontDomain = property.getDomain();
            String q = query.toLowerCase();
            var label = property.getLabel(null);
            var comment = property.getComment(null);
            return  (ontDomain == null || ontDomain.getLocalName().equals(domain)) && (
                    label != null && label.toLowerCase().contains(q) ||
                    comment != null && comment.toLowerCase().contains(q) ||
                    property.getLocalName().toLowerCase().contains(q));
        }).toList();
    }
}
