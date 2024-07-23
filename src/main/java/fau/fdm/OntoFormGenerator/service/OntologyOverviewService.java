package fau.fdm.OntoFormGenerator.service;

import fau.fdm.OntoFormGenerator.data.Ontology;
import fau.fdm.OntoFormGenerator.tdb.GeneralTDBService;
import fau.fdm.OntoFormGenerator.tdb.IndividualService;
import fau.fdm.OntoFormGenerator.tdb.PropertyService;
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
import java.util.UUID;

@Service
public class OntologyOverviewService {

    private final Logger logger;
    private final PropertyService propertyService;

    @Value("${ontoformgenerator.ontologyDirectory}")
    private String ontologyDirectory;

    @Value("${ontoformgenerator.ontologies.forms}")
    private String formsIRI;

    private final IndividualService individualService;

    private final GeneralTDBService generalTDBService;

    @Autowired
    public OntologyOverviewService(IndividualService individualService, GeneralTDBService generalTDBService, PropertyService propertyService) {
        this.generalTDBService = generalTDBService;
        this.logger = LoggerFactory.getLogger(OntologyOverviewService.class);
        this.individualService = individualService;
        this.propertyService = propertyService;
    }

    public OntologyOverviewService(String ontologyDirectory, Logger logger, PropertyService propertyService) {
        this.ontologyDirectory = ontologyDirectory;
        this.logger = logger;
        this.propertyService = propertyService;
        this.individualService = null;
        this.generalTDBService = null;
    }

    public boolean importOntology(File owlFile, String ontologyName) {
        Dataset dataset = TDB2Factory.connectDataset(ontologyDirectory);
        dataset.begin(ReadWrite.WRITE);
        try {
            OntModel ontModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM);
            OntModel newModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM);
            var modelName = ontologyName + "_" + UUID.randomUUID();
            var newOntURI = "http://www.ontoformgenerator.de/ontologies/" + modelName;

            try {
                var fis = new FileInputStream(owlFile);
                ontModel.read(fis, null);
                fis.close();
                var fisNew = new FileInputStream(owlFile);
                newModel.read(fisNew, null);
                fisNew.close();
            } catch (Exception e) {
                logger.error("Error reading file while importing new ontology", e);
                dataset.abort();
                return false;
            }
            var ontology = newModel.createOntology(newOntURI);
            dataset.addNamedModel(modelName, newModel.getBaseModel());
            var ontIndiv = individualService.addIndividualWithURI(dataset, "Ontology", newOntURI);
            dataset.commit();
            dataset.end();
            logger.info("Ontology {} imported successfully", modelName);
            return true;
        } catch (Exception e) {
            dataset.abort();
            return false;
        } finally {
            dataset.end();
        }
    }

    public List<Ontology> getImportedOntologies() {
        List<Ontology> ontologies = new ArrayList<>();
        Dataset dataset = TDB2Factory.connectDataset(ontologyDirectory);
        dataset.begin(ReadWrite.READ);
        try {
            OntClass ontologyClass = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM,
                    dataset.getNamedModel("forms")).getOntClass(formsIRI + "#Ontology");
            ontologyClass.listInstances().forEach(
                    res -> {
                        var ontName = res.getLocalName();
                        var ontIRI = res.getURI();
                        ontologies.add(new Ontology(ontName, ontIRI));
                    }
            );
            return ontologies;
        } finally {
            dataset.end();
        }
    }

    public void deleteOntology(String ontologyName) {
        Dataset dataset = TDB2Factory.connectDataset(ontologyDirectory);
        dataset.begin(ReadWrite.WRITE);
        try {
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
            var ontologyIri = generalTDBService.getIndividualURIInOntology(dataset, "forms", ontologyName);
            individualService.deleteIndividualByIri(dataset, "forms", ontologyIri);
            dataset.commit();
        } catch (Exception e) {
            dataset.abort();
        } finally {
            dataset.end();
        }
    }

    public ByteArrayResource downloadOntology(String ontologyName) {
        Dataset dataset = TDB2Factory.connectDataset(ontologyDirectory);
        dataset.begin(ReadWrite.READ);
        try {
            OntModel ontModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM, dataset.getNamedModel(ontologyName));
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            ontModel.write(outputStream, "RDF/XML");
            return new ByteArrayResource(outputStream.toByteArray());
        } finally {
            dataset.end();
        }
    }

}
