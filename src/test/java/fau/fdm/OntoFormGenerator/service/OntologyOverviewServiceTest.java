package fau.fdm.OntoFormGenerator.service;

import org.apache.commons.io.FileUtils;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.RiotException;
import org.apache.jena.tdb2.TDB2Factory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.Logger;

import java.io.File;
import java.io.FileNotFoundException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class OntologyOverviewServiceTest {

    @Mock
    private Logger logger;

    private OntologyOverviewService ontologyOverviewService;

    //TODO: Include new FormOntology in tests

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);
        this.ontologyOverviewService = new OntologyOverviewService("ontologies/test/uploadedOntologies",
                logger);
    }

    @AfterEach
    public void cleanup() {
        FileUtils.deleteQuietly(new File("ontologies/test/uploadedOntologies"));
    }

    @Test
    public void importOntology_returnsTrueWhenFileExistsAndIsReadable() {
        File mockFile = new File("src/test/resources/ontology-files/wine.owl");

        var result = ontologyOverviewService.importOntology(mockFile, "testOntology2");

        assertTrue(result);
        Dataset dataset = TDB2Factory.connectDataset("ontologies/test/uploadedOntologies");
        dataset.begin(ReadWrite.READ);
        Model nullModel = dataset.getNamedModel("testOntologys");
        Model model = dataset.getNamedModel("testOntology2");
        assertTrue(nullModel.isEmpty());
        assertFalse(model.isEmpty());
        dataset.end();
    }

    @Test
    public void importOntology_returnsFalseWhenFileDoesNotExist() {
        File mockFile = new File("src/test/resources/ontology-files/doesnotexist/noOntology.owl");

        var result = ontologyOverviewService.importOntology(mockFile, "testOntology");

        assertFalse(result);
        verify(logger).error(anyString(), any(FileNotFoundException.class));
    }

    @Test
    public void importOntology_returnsFalseWhenFileIsNotAnOntology() {
        File mockFile = new File("src/test/resources/ontology-files/noOntology.owl");

        var result = ontologyOverviewService.importOntology(mockFile, "testOntology");

        assertFalse(result);
        verify(logger).error(anyString(), any(RiotException.class));
    }

    @Test
    public void importOntology_returnsFalseWhenOntologyNameAlreadyExists() {
        File mockFile = new File("src/test/resources/ontology-files/wine.owl");

        var resultOne = ontologyOverviewService.importOntology(mockFile, "testOntology");
        var resultTwo = ontologyOverviewService.importOntology(mockFile, "testOntology");

        assertTrue(resultOne);
        assertFalse(resultTwo);
        verify(logger).error("Ontology with name {} already exists", "testOntology");
    }
}
