package fau.fdm.OntoFormGenerator.tdb;

import lombok.Getter;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.ontology.OntModelSpec;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.tdb2.TDB2Factory;

public class TDBConnection implements AutoCloseable {

    private final String ontologyDirectory = "ontologies/production";

    @Getter
    Dataset dataset;
    @Getter
    OntModel model;

    private boolean commit = false;

    public TDBConnection(ReadWrite accessMode, String ontologyName) {
        dataset = TDB2Factory.connectDataset(ontologyDirectory);
        dataset.begin(accessMode);
        if (ontologyName != null) {
            model = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM,
                    dataset.getNamedModel(ontologyName));
        }
    }

    public void commit() {
        dataset.commit();
        commit = true;
    }

    @Override
    public void close() {
        if (!commit) {
            dataset.abort();
        }
        dataset.end();
    }
}
