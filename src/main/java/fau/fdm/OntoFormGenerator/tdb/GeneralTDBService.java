package fau.fdm.OntoFormGenerator.tdb;

import org.apache.jena.query.Dataset;
import org.springframework.stereotype.Service;

@Service
public class GeneralTDBService {
    public String getOntologyURIByOntologyName(Dataset dataset, String ontologyName) {
        return dataset.getNamedModel(ontologyName).getNsPrefixURI("");
    }
}
