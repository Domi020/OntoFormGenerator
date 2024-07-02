package fau.fdm.OntoFormGenerator.tdb;

import org.apache.jena.ontapi.OntSpecification;
import org.apache.jena.query.Dataset;
import org.springframework.stereotype.Service;
import org.apache.jena.ontapi.OntModelFactory;

@Service
public class GeneralTDBService {
    public String getOntologyURIByOntologyName(Dataset dataset, String ontologyName) {
        return dataset.getNamedModel(ontologyName).getNsPrefixURI("");
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
