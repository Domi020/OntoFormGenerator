package fau.fdm.OntoFormGenerator.service;

import org.apache.jena.ontology.OntModel;
import org.apache.jena.ontology.OntModelSpec;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.tdb2.TDB2Factory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;

@Service
public class OntologyOverviewService {

    private final Logger logger;

    private final String ontologyDirectory;

    public OntologyOverviewService() {
        this.ontologyDirectory = "ontologies/uploadedOntologies";
        this.logger = LoggerFactory.getLogger(OntologyOverviewService.class);
    }

    public OntologyOverviewService(String ontologyDirectory, Logger logger) {
        this.ontologyDirectory = ontologyDirectory;
        this.logger = logger;
    }

    public boolean importOntology(File owlFile, String ontologyName) {
        OntModel ontModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM);
        try {
            var fis = new FileInputStream(owlFile);
            ontModel.read(fis, null);
        } catch (Exception e) {
            logger.error("Error reading file while importing new ontology", e);
            return false;
        }
        Dataset dataset = TDB2Factory.connectDataset(ontologyDirectory);
        dataset.begin(ReadWrite.WRITE);
        dataset.addNamedModel(ontologyName, ontModel);
        dataset.commit();
        dataset.end();
        logger.info("Ontology {} imported successfully", ontologyName);
        return true;
    }
}
