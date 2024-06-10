package fau.fdm.OntoFormGenerator.service;

import fau.fdm.OntoFormGenerator.tdb.IndividualService;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.ontology.OntModelSpec;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.tdb2.TDB2Factory;
import org.checkerframework.checker.units.qual.A;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Service
public class OntologyOverviewService {

    private final Logger logger;

    @Value("${ontoformgenerator.ontologyDirectory}")
    private String ontologyDirectory;

    @Value("${ontoformgenerator.ontologies.forms}")
    private String formsIRI;

    private final IndividualService individualService;

    @Autowired
    public OntologyOverviewService(IndividualService individualService) {
        this.logger = LoggerFactory.getLogger(OntologyOverviewService.class);
        this.individualService = individualService;
    }

    public OntologyOverviewService(IndividualService individualService, String ontologyDirectory, Logger logger) {
        this.ontologyDirectory = ontologyDirectory;
        this.logger = logger;
        this.individualService = individualService;
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
        if (!dataset.getNamedModel(ontologyName).isEmpty()) {
            logger.error("Ontology with name {} already exists", ontologyName);
            dataset.abort();
            dataset.end();
            return false;
        }
        dataset.addNamedModel(ontologyName, ontModel);
        individualService.addIndividual(dataset, "Ontology", ontologyName);
        dataset.commit();
        dataset.end();
        logger.info("Ontology {} imported successfully", ontologyName);
        return true;
    }

    public List<String> getNamesOfImportedOntologies() {
        //TODO: read from form ontology instead of iterating over all models
        Dataset dataset = TDB2Factory.connectDataset(ontologyDirectory);
        dataset.begin(ReadWrite.READ);
        Iterator<String> ontologyNamesIterator = dataset.listNames();
        List<String> ontologyNames = new ArrayList<>();
        ontologyNamesIterator.forEachRemaining(ontologyNames::add);
        dataset.end();
        ontologyNames.removeIf(name -> name.equals("forms"));
        return ontologyNames;
    }

    public void deleteOntology(String ontologyName) {
        Dataset dataset = TDB2Factory.connectDataset(ontologyDirectory);
        dataset.begin(ReadWrite.WRITE);
        dataset.removeNamedModel(ontologyName);
        individualService.selectIndividualsInSPARQLQuery(dataset, "forms",
                """
                        PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                            PREFIX owl: <http://www.w3.org/2002/07/owl#>
                            PREFIX form: <http://www.semanticweb.org/fau/ontologies/2024/ontoformgenerator/forms#>

                            SELECT ?f WHERE {
                          ?f form:targetsOntology form:%s .
                            }
                        """.formatted(ontologyName))
                .forEach(individual -> individualService.deleteIndividual(dataset, "forms",
                        individual.getLocalName()));
        individualService.deleteIndividual(dataset, "forms", ontologyName);
        dataset.commit();
        dataset.end();
    }

    public ByteArrayResource downloadOntology(String ontologyName) {
        Dataset dataset = TDB2Factory.connectDataset(ontologyDirectory);
        dataset.begin(ReadWrite.READ);
        OntModel ontModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM, dataset.getNamedModel(ontologyName));
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        RDFDataMgr.write(outputStream, ontModel, org.apache.jena.riot.RDFFormat.RDFXML);
        ByteArrayResource resource = new ByteArrayResource(outputStream.toByteArray());
        dataset.end();
        return resource;
    }

}
