package fau.fdm.OntoFormGenerator.service;

import fau.fdm.OntoFormGenerator.data.Ontology;
import fau.fdm.OntoFormGenerator.tdb.IndividualService;
import org.apache.jena.ontology.OntClass;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.ontology.OntModelSpec;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.tdb2.TDB2Factory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
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
        Dataset dataset = TDB2Factory.connectDataset(ontologyDirectory);
        dataset.begin(ReadWrite.WRITE);
        OntModel ontModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM);
        var model = dataset.getNamedModel("forms");
        OntModel formsModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM, model);
        try {
            var fis = new FileInputStream(owlFile);
            ontModel.read(fis, null);
            fis.close();
            var fisTwo = new FileInputStream(owlFile);
            formsModel.read(fisTwo, null);
            fisTwo.close();
        } catch (Exception e) {
            logger.error("Error reading file while importing new ontology", e);
            dataset.abort();
            dataset.end();
            return false;
        }
        if (!dataset.getNamedModel(ontologyName).isEmpty()) {
            logger.error("Ontology with name {} already exists", ontologyName);
            dataset.abort();
            dataset.end();
            return false;
        }
        dataset.addNamedModel(ontologyName, ontModel);
        String ontURI = ontModel.getNsPrefixURI("");
        if (ontURI.charAt(ontURI.length() - 1) == '#' || ontURI.charAt(ontURI.length() - 1) == '/') {
            ontURI = ontURI.substring(0, ontURI.length() - 1);
        }
        individualService.addIndividualWithURI(dataset, "Ontology", ontURI);
        dataset.commit();
        dataset.end();
        logger.info("Ontology {} imported successfully", ontologyName);
        return true;
    }

    public List<Ontology> getImportedOntologies() {
        List<Ontology> ontologies = new ArrayList<>();
        Dataset dataset = TDB2Factory.connectDataset(ontologyDirectory);
        dataset.begin(ReadWrite.READ);
        OntClass ontologyClass = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM,
                dataset.getNamedModel("forms")).getOntClass(formsIRI + "#Ontology");
        ontologyClass.listInstances().forEach(
                res -> {
                    var ontName = res.getLocalName();
                    var ontIRI = res.getURI();
                    ontologies.add(new Ontology(ontName, ontIRI));
                }
        );
        dataset.end();
        return ontologies;
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
        //RDFDataMgr.write(outputStream, ontModel, org.apache.jena.riot.RDFFormat.RDFXML);
        ontModel.write(outputStream, "RDF/XML");
        ByteArrayResource resource = new ByteArrayResource(outputStream.toByteArray());
        dataset.end();
        return resource;
    }

}
