package fau.fdm.OntoFormGenerator.service;

import fau.fdm.OntoFormGenerator.data.Ontology;
import fau.fdm.OntoFormGenerator.tdb.GeneralTDBService;
import fau.fdm.OntoFormGenerator.tdb.IndividualService;
import fau.fdm.OntoFormGenerator.tdb.PropertyService;
import fau.fdm.OntoFormGenerator.tdb.TDBConnection;
import org.apache.jena.ontapi.GraphRepository;
import org.apache.jena.ontapi.OntModelFactory;
import org.apache.jena.ontapi.OntSpecification;
import org.apache.jena.ontology.OntClass;
import org.apache.jena.query.ReadWrite;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for managing imported ontologies.
 */
@Service
public class OntologyOverviewService {

    private final Logger logger;
    private final FormOverviewService formOverviewService;

    @Value("${ontoformgenerator.ontologies.forms}")
    private String formsIRI;

    private final IndividualService individualService;

    private final GeneralTDBService generalTDBService;
    private PropertyService propertyService;

    @Value("${ontoformgenerator.ontologyDirectory}")
    private String ontologyDirectory;

    @Autowired
    public OntologyOverviewService(IndividualService individualService, GeneralTDBService generalTDBService, FormOverviewService formOverviewService, PropertyService propertyService) {
        this.generalTDBService = generalTDBService;
        this.logger = LoggerFactory.getLogger(OntologyOverviewService.class);
        this.individualService = individualService;
        this.formOverviewService = formOverviewService;
        this.propertyService = propertyService;
    }

    public OntologyOverviewService(Logger logger, FormOverviewService formOverviewService) {
        this.logger = logger;
        this.individualService = null;
        this.generalTDBService = null;
        this.formOverviewService = formOverviewService;
    }

    /**
     * Imports an ontology from an OWL file.
     * @param owlFile The OWL file to import.
     * @param ontologyName The name of the ontology under which it should be stored.
     * @return True if the import was successful, false otherwise.
     * @throws IOException If an error occurs while reading the file - for example if the file uses an unsupported format.
     */
    public boolean importOntology(File owlFile, String ontologyName) throws IOException {
        logger.info("Importing ontology {} from file {}", ontologyName, owlFile.getPath());
        try (TDBConnection connection = new TDBConnection(ReadWrite.WRITE, ontologyDirectory, ontologyName)) {
            var dataset = connection.getDataset();
            var newOntURI = "http://www.ontoformgenerator.de/ontologies/" + ontologyName;
            var newModel = OntModelFactory.createModel(newOntURI, GraphRepository.createGraphDocumentRepositoryMem());
            var ontModel = OntModelFactory.createModel(OntSpecification.OWL2_DL_MEM);
            // var formModel = OntModelFactory.createModel(OntSpecification.OWL2_DL_MEM);
            // var ontology = newModel.setID(newOntURI);

            try {
                var fis = new FileInputStream(owlFile);
                ontModel.read(fis, null);
                newModel.addImport(ontModel);
                fis.close();
                // var formFis = new FileInputStream("owl/forms.rdf");
                // formModel.read(formFis, null);
                // newModel.addImport(formModel);
            } catch (Exception e) {
                logger.error("Error reading file while importing new ontology", e);
                throw e;
            }
            dataset.addNamedModel(ontologyName, newModel);
            var ontIndiv = individualService.addIndividualWithUniqueIRI(dataset, "Ontology", newOntURI);
            propertyService.createAnnotationProperty(dataset, "general", "isUserDefined", "isUserDefined",
                    "Indicates whether this entity was created by OntoFormGenerator.");
            connection.commit();
            logger.info("Ontology {} imported successfully", ontologyName);
            return true;
        } catch (Exception e) {
            logger.error("Error reading file while importing new ontology", e);
            throw e;
        }
    }

    /**
     * Get a list of all imported ontologies.
     * @return A list of all imported ontologies.
     */
    public List<Ontology> getImportedOntologies() {
        List<Ontology> ontologies = new ArrayList<>();
        try (TDBConnection connection = new TDBConnection(ReadWrite.READ, ontologyDirectory, "forms")) {
            OntClass ontologyClass = connection.getModel().getOntClass(formsIRI + "#Ontology");
            ontologyClass.listInstances().forEachRemaining(
                    res -> {
                        var ontName = res.getLocalName();
                        var ontIRI = res.getURI();
                        ontologies.add(new Ontology(ontName, ontIRI));
                    }
            );
            return ontologies;
        }
    }

    /**
     * Get an ontology by its name.
     * @param ontologyName The name of the ontology.
     * @return The ontology with the given name, or null if no such ontology exists.
     */
    public Ontology getOntologyByName(String ontologyName) {
        var ontologyList = getImportedOntologies();
        return ontologyList.stream().filter(ontology -> ontology.getName().equals(ontologyName)).findFirst().orElse(null);
    }

    /**
     * Delete an ontology by its name.
     * @param ontologyName The name of the ontology to delete.
     */
    public void deleteOntology(String ontologyName) {
        logger.info("Deleting ontology {}", ontologyName);
        try (TDBConnection connection = new TDBConnection(ReadWrite.WRITE, ontologyDirectory, ontologyName)) {
            var dataset = connection.getDataset();
            dataset.removeNamedModel(ontologyName);
            individualService.selectIndividualsInSPARQLQuery(dataset, "forms",
                            """
                                    PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                                    PREFIX owl: <http://www.w3.org/2002/07/owl#>
                                    PREFIX ont: <http://www.ontoformgenerator.de/ontologies/>
                                    PREFIX form: <http://www.semanticweb.org/fau/ontologies/2024/ontoformgenerator/forms#>
                                              \s
                                    SELECT ?f WHERE {
                                    	?f form:targetsOntology ont:%s .
                                    }
                                   \s
                                   \s""".formatted(ontologyName))
                    .forEach(form -> {
                        if (form != null) formOverviewService.deleteForm(connection.getDataset(), form.getLocalName());
                    });
            var ontologyIri = generalTDBService.getIndividualURIInOntology(dataset, "forms", ontologyName);
            individualService.deleteIndividualByIri(dataset, "forms", ontologyIri);
            logger.info("Ontology {} deleted successfully", ontologyName);
            connection.commit();
        } catch (Exception e) {
            logger.error("Error deleting ontology", e);
        }
    }

    /**
     * Download an ontology and its knowledge base including all created individuals by its name.
     * @param ontologyName The name of the ontology to download.
     * @return A byte array resource containing the ontology.
     */
    public ByteArrayResource downloadOntology(String ontologyName) {
        try (TDBConnection connection = new TDBConnection(ReadWrite.READ, ontologyDirectory, ontologyName)) {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            connection.getModel().write(outputStream, "RDF/XML");
            return new ByteArrayResource(outputStream.toByteArray());
        }
    }
}
