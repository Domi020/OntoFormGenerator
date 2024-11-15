package fau.fdm.OntoFormGenerator.tdb;

import org.apache.jena.ontapi.OntSpecification;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.ontology.OntModelSpec;
import org.apache.jena.query.*;
import org.apache.jena.rdf.model.Model;
import org.springframework.stereotype.Service;
import org.apache.jena.ontapi.OntModelFactory;

import org.apache.jena.rdf.model.ModelFactory;


/**
 * Low-level Service for general TDB lookup operations.
 */
@Service
public class GeneralTDBService {

    /**
     * Factory method to construct an ontology model from a general unspecified model extracted from TDB.
     * @param model The TDB model to be converted to an ontology model.
     * @return The ontology model.
     */
    public OntModel getOntModel(Model model) {
        // return OntModelFactory.createModel(model.getGraph(), OntSpecification.OWL2_DL_MEM);
        return ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM, model);
    }

    public String getOntologyURIByOntologyName(Dataset dataset, String ontologyName) {
        var model = getOntModel(dataset.getNamedModel("forms"));
        var ont = model.listIndividuals().filterKeep(ontIndividual -> ontIndividual.getOntClass().getURI().equals(
                "http://www.semanticweb.org/fau/ontologies/2024/ontoformgenerator/forms#Ontology"
        ) && ontIndividual.getLocalName().equals(ontologyName)).next();
        return ont.getURI();
    }

    public String getClassURIInOntology(Dataset dataset, String ontologyName, String className) {
        var ontModel = getOntModel(dataset.getNamedModel(ontologyName));
        var namedClass = ontModel.listClasses().filterKeep(ontClass -> ontClass.getLocalName() != null &&
                ontClass.getLocalName().equals(className)).next();
        return namedClass.getURI();
    }

    // TODO: eventuell Low Level Services nur in laufenden Transaktionen nutzbar?

    public String getClassURIInOntology(String ontologyName, String className) {
        try (TDBConnection connection = new TDBConnection(ReadWrite.READ, ontologyName)) {
            return getClassURIInOntology(connection.getDataset(), ontologyName, className);
        }
    }

    public String getClassNameInOntology(Dataset dataset, String ontologyName, String classURI) {
        var ontmodel = getOntModel(dataset.getNamedModel(ontologyName));
        var namedClass = ontmodel.listClasses().filterKeep(ontClass -> ontClass.getURI() != null &&
                ontClass.getURI().equals(classURI)).next();
        return namedClass.getLocalName();
    }

    public String getPropertyURIInOntology(Dataset dataset, String ontologyName, String propertyName) {
        var ontmodel = getOntModel(dataset.getNamedModel(ontologyName));
        var namedProperty = ontmodel.listAllOntProperties().filterKeep(ontProperty -> ontProperty.getLocalName() != null &&
                ontProperty.getLocalName().equals(propertyName)).next();
        return namedProperty.getURI();
    }

    public String getIndividualURIInOntology(Dataset dataset, String ontologyName, String individualName) {
        var ontmodel = getOntModel(dataset.getNamedModel(ontologyName));
        var namedIndividual = ontmodel.listIndividuals().filterKeep(ontIndividual -> ontIndividual.getLocalName().equals(individualName)).next();
        return namedIndividual.getURI();
    }

    public boolean checkIfObjectProperty(Dataset dataset, String ontologyName, String propertyURI) {
        var ontmodel = getOntModel(dataset.getNamedModel(ontologyName));
        var namedProperty = ontmodel.listObjectProperties().filterKeep(ontProperty -> ontProperty.getURI().equals(propertyURI));
        return namedProperty.hasNext();
    }

    public boolean checkIfAnnotationProperty(Dataset dataset, String ontologyName, String propertyURI) {
        if (propertyURI.equals("http://www.w3.org/2000/01/rdf-schema#label") ||
                propertyURI.equals("http://www.w3.org/2000/01/rdf-schema#comment") ||
                propertyURI.equals("http://www.w3.org/2000/01/rdf-schema#isDefinedBy") ||
                propertyURI.equals("http://www.w3.org/2000/01/rdf-schema#seeAlso") ||
                propertyURI.equals("http://www.w3.org/2002/07/owl#versionInfo") ||
                propertyURI.equals("http://www.w3.org/2002/07/owl#priorVersion") ||
                propertyURI.equals("http://www.w3.org/2002/07/owl#backwardCompatibleWith") ||
                propertyURI.equals("http://www.w3.org/2002/07/owl#incompatibleWith") ||
                propertyURI.equals("http://www.w3.org/2002/07/owl#deprecated")){
            return true;
        }
        var ontmodel = getOntModel(dataset.getNamedModel(ontologyName));
        var namedProperty = ontmodel.listAnnotationProperties().filterKeep(ontProperty -> ontProperty.getURI().equals(propertyURI));
        return namedProperty.hasNext();
    }
}
