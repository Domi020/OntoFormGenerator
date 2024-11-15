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
import java.util.ArrayList;
import java.util.List;

@Service
public class OntologyOverviewService {

    private final Logger logger;
    private final FormOverviewService formOverviewService;

    @Value("${ontoformgenerator.ontologies.forms}")
    private String formsIRI;

    private final IndividualService individualService;

    private final GeneralTDBService generalTDBService;

    @Autowired
    public OntologyOverviewService(IndividualService individualService, GeneralTDBService generalTDBService, FormOverviewService formOverviewService) {
        this.generalTDBService = generalTDBService;
        this.logger = LoggerFactory.getLogger(OntologyOverviewService.class);
        this.individualService = individualService;
        this.formOverviewService = formOverviewService;
    }

    public OntologyOverviewService(String ontologyDirectory, Logger logger, PropertyService propertyService, FormOverviewService formOverviewService) {
        this.logger = logger;
        this.individualService = null;
        this.generalTDBService = null;
        this.formOverviewService = formOverviewService;
    }

    public boolean importOntology(File owlFile, String ontologyName) {
        try (TDBConnection connection = new TDBConnection(ReadWrite.WRITE, ontologyName)) {
            var dataset = connection.getDataset();
            var newOntURI = "http://www.ontoformgenerator.de/ontologies/" + ontologyName;
            var newModel = OntModelFactory.createModel(newOntURI, GraphRepository.createGraphDocumentRepositoryMem());
            var ontModel = OntModelFactory.createModel(OntSpecification.OWL2_DL_MEM);
            var formModel = OntModelFactory.createModel(OntSpecification.OWL2_DL_MEM);
            // var ontology = newModel.setID(newOntURI);

            try {
                var fis = new FileInputStream(owlFile);
                ontModel.read(fis, null);
                newModel.addImport(ontModel);
                fis.close();
                var formFis = new FileInputStream("owl/forms.rdf");
                formModel.read(formFis, null);
                newModel.addImport(formModel);
            } catch (Exception e) {
                logger.error("Error reading file while importing new ontology", e);
                return false;
            }
            dataset.addNamedModel(ontologyName, newModel);
            var ontIndiv = individualService.addIndividualWithUniqueIRI(dataset, "Ontology", newOntURI);
            connection.commit();
            logger.info("Ontology {} imported successfully", ontologyName);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public List<Ontology> getImportedOntologies() {
        List<Ontology> ontologies = new ArrayList<>();
        try (TDBConnection connection = new TDBConnection(ReadWrite.READ, "forms")) {
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

    public Ontology getOntologyByName(String ontologyName) {
        var ontologyList = getImportedOntologies();
        return ontologyList.stream().filter(ontology -> ontology.getName().equals(ontologyName)).findFirst().orElse(null);
    }

    public void deleteOntology(String ontologyName) {
        try (TDBConnection connection = new TDBConnection(ReadWrite.WRITE, ontologyName)) {
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
            connection.commit();
        }
    }

    public ByteArrayResource downloadOntology(String ontologyName) {
        try (TDBConnection connection = new TDBConnection(ReadWrite.READ, ontologyName)) {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            connection.getModel().write(outputStream, "RDF/XML");
            return new ByteArrayResource(outputStream.toByteArray());
        }
    }
}
