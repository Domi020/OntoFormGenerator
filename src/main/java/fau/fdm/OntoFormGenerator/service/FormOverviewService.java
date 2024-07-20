package fau.fdm.OntoFormGenerator.service;

import fau.fdm.OntoFormGenerator.data.Form;
import fau.fdm.OntoFormGenerator.data.Individual;
import fau.fdm.OntoFormGenerator.data.Ontology;
import fau.fdm.OntoFormGenerator.data.OntologyClass;
import fau.fdm.OntoFormGenerator.tdb.IndividualService;
import fau.fdm.OntoFormGenerator.tdb.PropertyService;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.tdb2.TDB2Factory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class FormOverviewService {

    private final Logger logger;

    @Value("${ontoformgenerator.ontologyDirectory}")
    private String ontologyDirectory;

    @Value("${ontoformgenerator.ontologies.forms}")
    private String formsIRI;

    private final IndividualService individualService;

    private final PropertyService propertyService;

    @Autowired
    public FormOverviewService(IndividualService individualService, PropertyService propertyService) {
        this.propertyService = propertyService;
        this.logger = LoggerFactory.getLogger(OntologyOverviewService.class);
        this.individualService = individualService;
    }

    public void addNewForm(String formName, String ontologyName, String ontologyURI) {
        Dataset dataset = TDB2Factory.connectDataset(ontologyDirectory);
        dataset.begin(ReadWrite.WRITE);
        var individual = individualService.addIndividual(dataset, "Form", formName);
        propertyService.addObjectPropertyToIndividual(dataset,
                "forms", individual, "targetsOntology", ontologyURI);
        dataset.commit();
        dataset.end();
    }

    public List<Form> getAllForms() {
        List<Form> forms = new ArrayList<>();
        Dataset dataset = TDB2Factory.connectDataset(ontologyDirectory);
        dataset.begin(ReadWrite.READ);
        var individuals = individualService.getAllIndividualsOfClass(dataset, "forms", "Form");
        for (var individual : individuals) {
            var formName = individual.getLocalName();
            var ontologyName = propertyService.getObjectPropertyValueFromIndividual(dataset,
                    "forms", individual, "targetsOntology").getLocalName();
            var form = new Form(formName, ontologyName);
            forms.add(form);
        }
        dataset.end();
        return forms;
    }

    public List<Individual> getAllIndividualsOfForm(String formName) {
        Dataset dataset = TDB2Factory.connectDataset(ontologyDirectory);
        dataset.begin(ReadWrite.READ);
        var formIndividual = individualService.getIndividualByString(dataset, "forms", formName);
        var individuals = propertyService.getMultipleObjectPropertyValuesFromIndividual(dataset,
                "forms", formIndividual, "created");
        List<Individual> result = new ArrayList<>();
        for (var individual : individuals) {
            var isDraft = propertyService.getDatatypePropertyValueFromIndividual(dataset, "forms",
                    individual, "isDraft");
            if (isDraft != null && isDraft.getBoolean()) continue;
            result.add(new Individual(individual.getLocalName(), individual.getURI(),
                    new OntologyClass("test", "test")));
        }
        dataset.end();
        return result;
    }

    public List<Individual> getAllDraftsOfForm(String formName) {
        Dataset dataset = TDB2Factory.connectDataset(ontologyDirectory);
        dataset.begin(ReadWrite.READ);
        var formIndividual = individualService.getIndividualByString(dataset, "forms", formName);
        var individuals = propertyService.getMultipleObjectPropertyValuesFromIndividual(dataset,
                "forms", formIndividual, "created");
        List<Individual> result = new ArrayList<>();
        for (var individual : individuals) {
            var isDraft = propertyService.getDatatypePropertyValueFromIndividual(dataset, "forms",
                    individual, "isDraft");
            if (isDraft == null || !isDraft.getBoolean()) continue;
            result.add(new Individual(individual.getLocalName(), individual.getURI(),
                    new OntologyClass("test", "test")));
        }
        dataset.end();
        return result;
    }

    public Ontology getOntologyOfForm(String formName) {
        Dataset dataset = TDB2Factory.connectDataset(ontologyDirectory);
        try {
            dataset.begin(ReadWrite.READ);
            var formIndividual = individualService.getIndividualByString(dataset, "forms", formName);
            var ontologyIndividual = propertyService.getObjectPropertyValueFromIndividual(dataset,
                    "forms", formIndividual, "targetsOntology");
            return new Ontology(ontologyIndividual.getLocalName(), ontologyIndividual.getURI());
        } finally {
            dataset.end();
        }
    }
}
