package fau.fdm.OntoFormGenerator.tdb;

import org.apache.jena.ontapi.OntSpecification;
import org.apache.jena.ontology.OntModelSpec;
import org.apache.jena.query.*;
import org.apache.jena.rdf.model.Model;
import org.springframework.stereotype.Service;
import org.apache.jena.ontapi.OntModelFactory;

import org.apache.jena.rdf.model.ModelFactory;

@Service
public class GeneralTDBService {

    public org.apache.jena.ontapi.model.OntModel getOntModel(Model model) {
        return OntModelFactory.createModel(model.getGraph(), OntSpecification.OWL2_DL_MEM);
    }

    public String getOntologyURIByOntologyName(Dataset dataset, String ontologyName) {
        var model = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM, dataset.getNamedModel("forms"));
        var ont = model.listIndividuals().filterKeep(ontIndividual -> ontIndividual.getOntClass().getURI().equals(
                "http://www.semanticweb.org/fau/ontologies/2024/ontoformgenerator/forms#Ontology"
        ) && ontIndividual.getLocalName().equals(ontologyName)).next();
        return ont.getURI();
    }

    public String getClassURIInOntology(Dataset dataset, String ontologyName, String className) {
        var ontmodel = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM, dataset.getNamedModel(ontologyName));
        var namedClass = ontmodel.listClasses().filterKeep(ontClass -> ontClass.getLocalName() != null &&
                ontClass.getLocalName().equals(className)).next();
        return namedClass.getURI();
    }

    public String getPropertyURIInOntology(Dataset dataset, String ontologyName, String propertyName) {
        var ontmodel = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM, dataset.getNamedModel(ontologyName));
        var namedProperty = ontmodel.listAllOntProperties().filterKeep(ontProperty -> ontProperty.getLocalName() != null &&
                ontProperty.getLocalName().equals(propertyName)).next();
        return namedProperty.getURI();
    }

    public String getIndividualURIInOntology(Dataset dataset, String ontologyName, String individualName) {
        var ontmodel = OntModelFactory.createModel(dataset.getNamedModel(ontologyName).getGraph(),
                OntSpecification.OWL2_DL_MEM);
        var namedIndividual = ontmodel.individuals().filter(ontIndividual -> ontIndividual.getLocalName().equals(individualName)).findFirst().orElseThrow();
        return namedIndividual.getURI();
    }

    public boolean checkIfObjectProperty(Dataset dataset, String ontologyName, String propertyURI) {
        var ontmodel = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM, dataset.getNamedModel(ontologyName));
        var namedProperty = ontmodel.listObjectProperties().filterKeep(ontProperty -> ontProperty.getURI().equals(propertyURI));
        return namedProperty.hasNext();
    }
}
