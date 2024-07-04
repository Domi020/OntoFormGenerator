package fau.fdm.OntoFormGenerator.tdb;

import org.apache.jena.ontapi.OntSpecification;
import org.apache.jena.query.*;
import org.apache.jena.rdf.model.Model;
import org.springframework.stereotype.Service;
import org.apache.jena.ontapi.OntModelFactory;

@Service
public class GeneralTDBService {

    public org.apache.jena.ontapi.model.OntModel getOntModel(Model model) {
        return OntModelFactory.createModel(model.getGraph(), OntSpecification.OWL2_DL_MEM);
    }

    public String getOntologyURIByOntologyName(Dataset dataset, String ontologyName) {
        var model = getOntModel(dataset.getNamedModel("forms"));
        var ont = model.individuals().filter(ontIndividual -> ontIndividual.ontClass().get().getURI().equals(
                "http://www.semanticweb.org/fau/ontologies/2024/ontoformgenerator/forms#Ontology"
        ) && ontIndividual.getLocalName().equals(ontologyName)).findFirst().get();
        return ont.getProperty(model.getDataProperty
                ("http://www.semanticweb.org/fau/ontologies/2024/ontoformgenerator/forms#hasOntologyIRI")).getObject().asLiteral().getString();
    }

    public String getClassURIInOntology(Dataset dataset, String ontologyName, String className) {
        var ontmodel = OntModelFactory.createModel(dataset.getNamedModel(ontologyName).getGraph(),
                OntSpecification.OWL2_DL_MEM);
        var namedClass = ontmodel.classes().filter(ontClass -> ontClass.getLocalName().equals(className)).findFirst().orElseThrow();
        return namedClass.getURI();
    }

    public String getPropertyURIInOntology(Dataset dataset, String ontologyName, String propertyName) {
        var ontmodel = OntModelFactory.createModel(dataset.getNamedModel(ontologyName).getGraph(),
                OntSpecification.OWL2_DL_MEM);
        var namedProperty = ontmodel.properties().filter(ontProperty -> ontProperty.getLocalName().equals(propertyName)).findFirst().orElseThrow();
        return namedProperty.getURI();
    }

    public String getIndividualURIInOntology(Dataset dataset, String ontologyName, String individualName) {
        var ontmodel = OntModelFactory.createModel(dataset.getNamedModel(ontologyName).getGraph(),
                OntSpecification.OWL2_DL_MEM);
        var namedIndividual = ontmodel.individuals().filter(ontIndividual -> ontIndividual.getLocalName().equals(individualName)).findFirst().orElseThrow();
        return namedIndividual.getURI();
    }

    public boolean checkIfObjectProperty(Dataset dataset, String ontologyName, String propertyURI) {
        var ontmodel = OntModelFactory.createModel(dataset.getNamedModel(ontologyName).getGraph(),
                OntSpecification.OWL2_DL_MEM);
        var namedProperty = ontmodel.objectProperties().filter(ontProperty -> ontProperty.getURI().equals(propertyURI));
        return namedProperty.findAny().isPresent();
    }
}
