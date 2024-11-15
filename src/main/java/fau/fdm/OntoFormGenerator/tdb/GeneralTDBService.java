package fau.fdm.OntoFormGenerator.tdb;

import org.apache.jena.ontology.OntModel;
import org.apache.jena.ontology.OntModelSpec;
import org.apache.jena.query.*;
import org.apache.jena.rdf.model.Model;
import org.springframework.stereotype.Service;

import org.apache.jena.rdf.model.ModelFactory;


/**
 * Low-level Service for general TDB lookup operations.
 * This service is used to perform low-level operations on the TDB,
 * such as looking up URIs of classes, properties, and individuals.
 * Therefore, it does not create TDB transactions itself, but the methods expect to be called within a transaction
 * (by requiring a transactional dataset as parameter).
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

    /**
     * Get the prefix URI of an ontology by its name.
     * @param dataset The TDB production dataset of OntoFormGenerator
     * @param ontologyName The name of the to be searched ontology
     * @return The URI of the ontology
     */
    public String getOntologyURIByOntologyName(Dataset dataset, String ontologyName) {
        var model = getOntModel(dataset.getNamedModel("forms"));
        var ont = model.listIndividuals().filterKeep(ontIndividual -> ontIndividual.getOntClass().getURI().equals(
                "http://www.semanticweb.org/fau/ontologies/2024/ontoformgenerator/forms#Ontology"
        ) && ontIndividual.getLocalName().equals(ontologyName)).next();
        return ont.getURI();
    }

    /**
     * Get the URI auf a specific class in an ontology.
     * @param dataset The TDB production dataset of OntoFormGenerator
     * @param ontologyName The name of the ontology, where the class appears
     * @param className The local name of the class
     * @return The URI of the class
     */
    public String getClassURIInOntology(Dataset dataset, String ontologyName, String className) {
        var ontModel = getOntModel(dataset.getNamedModel(ontologyName));
        var namedClass = ontModel.listClasses().filterKeep(ontClass -> ontClass.getLocalName() != null &&
                ontClass.getLocalName().equals(className)).next();
        return namedClass.getURI();
    }

    // TODO: eventuell Low Level Services nur in laufenden Transaktionen nutzbar?

    /**
     * Get the local name of a class in an ontology by its URI.
     * @param dataset The TDB production dataset of OntoFormGenerator
     * @param ontologyName The name of the ontology, where the class appears
     * @param classURI The URI of the class
     * @return The local name of the class
     */
    public String getClassNameInOntology(Dataset dataset, String ontologyName, String classURI) {
        var ontmodel = getOntModel(dataset.getNamedModel(ontologyName));
        var namedClass = ontmodel.listClasses().filterKeep(ontClass -> ontClass.getURI() != null &&
                ontClass.getURI().equals(classURI)).next();
        return namedClass.getLocalName();
    }

    /**
     * Get the URI of a property in an ontology by its local name.
     * @param dataset The TDB production dataset of OntoFormGenerator
     * @param ontologyName The name of the ontology, where the property appears
     * @param propertyName The local name of the property
     * @return The URI of the property
     */
    public String getPropertyURIInOntology(Dataset dataset, String ontologyName, String propertyName) {
        var ontmodel = getOntModel(dataset.getNamedModel(ontologyName));
        var namedProperty = ontmodel.listAllOntProperties().filterKeep(ontProperty -> ontProperty.getLocalName() != null &&
                ontProperty.getLocalName().equals(propertyName)).next();
        return namedProperty.getURI();
    }

    /**
     * Get the URI of an individual in an ontology by its local name.
     * @param dataset The TDB production dataset of OntoFormGenerator
     * @param ontologyName The name of the ontology, where the individual appears
     * @param individualName The local name of the individual
     * @return The URI of the individual
     */
    public String getIndividualURIInOntology(Dataset dataset, String ontologyName, String individualName) {
        var ontmodel = getOntModel(dataset.getNamedModel(ontologyName));
        var namedIndividual = ontmodel.listIndividuals().filterKeep(ontIndividual -> ontIndividual.getLocalName().equals(individualName)).next();
        return namedIndividual.getURI();
    }

    /**
     * Check if the given property is an object property in the given ontology.
     * @param dataset The TDB production dataset of OntoFormGenerator
     * @param ontologyName The name of the ontology, where the property appears
     * @param propertyURI The URI of the property
     * @return true, if the property is an object property, false otherwise (e.g. datatype property or annotation property)
     */
    public boolean checkIfObjectProperty(Dataset dataset, String ontologyName, String propertyURI) {
        var ontmodel = getOntModel(dataset.getNamedModel(ontologyName));
        var namedProperty = ontmodel.listObjectProperties().filterKeep(ontProperty -> ontProperty.getURI().equals(propertyURI));
        return namedProperty.hasNext();
    }

    /**
     * Check if the given property is an annotation property in the given ontology.
     * This method checks the pre-defined OWL annotation properties like rdfs:label, rdfs:comment, etc.
     * as well as all custom annotation properties in the ontology.
     * @param dataset The TDB production dataset of OntoFormGenerator
     * @param ontologyName The name of the ontology, where the property appears
     * @param propertyURI The URI of the property
     * @return true, if the property is an annotation property, false otherwise (e.g. object property or datatype property)
     */
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
