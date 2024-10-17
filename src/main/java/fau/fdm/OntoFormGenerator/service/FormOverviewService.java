package fau.fdm.OntoFormGenerator.service;

import fau.fdm.OntoFormGenerator.data.Form;
import fau.fdm.OntoFormGenerator.data.Individual;
import fau.fdm.OntoFormGenerator.data.Ontology;
import fau.fdm.OntoFormGenerator.data.OntologyClass;
import fau.fdm.OntoFormGenerator.tdb.GeneralTDBService;
import fau.fdm.OntoFormGenerator.tdb.IndividualService;
import fau.fdm.OntoFormGenerator.tdb.PropertyService;
import org.apache.jena.ontapi.model.OntIndividual;
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
    private final GeneralTDBService generalTDBService;

    @Value("${ontoformgenerator.ontologyDirectory}")
    private String ontologyDirectory;

    @Value("${ontoformgenerator.ontologies.forms}")
    private String formsIRI;

    private final IndividualService individualService;

    private final PropertyService propertyService;

    @Autowired
    public FormOverviewService(IndividualService individualService, PropertyService propertyService, GeneralTDBService generalTDBService) {
        this.propertyService = propertyService;
        this.logger = LoggerFactory.getLogger(OntologyOverviewService.class);
        this.individualService = individualService;
        this.generalTDBService = generalTDBService;
    }

    public List<Form> getFormsWithTargetClass(String ontologyName, String targetClass) {
        Dataset dataset = TDB2Factory.connectDataset(ontologyDirectory);
        dataset.begin(ReadWrite.READ);
        try {
            var targetClassIndiv = individualService.findIndividualInOntology(dataset, ontologyName, targetClass);

            var allForms = individualService.getAllIndividualsOfClass(dataset, "forms", "Form");
            return allForms.stream()
                    .filter(form -> propertyService.getObjectPropertyValueFromIndividual(dataset, "forms", form, "targetsClass")
                    .getLocalName().equals(targetClass))
                    .map(form -> new Form(form.getLocalName(), ontologyName,
                            new OntologyClass(targetClass, targetClassIndiv.getURI())))
                    .toList();
        } finally {
            dataset.end();
        }
    }

    public void addNewForm(String formName, String ontologyName, String ontologyURI,
                           String targetClass) {
        Dataset dataset = TDB2Factory.connectDataset(ontologyDirectory);
        dataset.begin(ReadWrite.WRITE);
        try {
            var individual = individualService.addIndividual(dataset, "Form", formName);
            propertyService.addObjectPropertyToIndividual(dataset,
                    "forms", individual, "targetsOntology", ontologyURI);

            // Set targetsClass
            var classIri = individualService.findIriOfClass(dataset, ontologyName, targetClass);
            var ontologyUri = generalTDBService.getOntologyURIByOntologyName(dataset, ontologyName);
            var classIndividual = individualService.getOntIndividualByIri(dataset, classIri);
            if (classIndividual == null) {
                classIndividual = individualService.addIndividualWithURI(dataset, "TargetClass", classIri);
                propertyService.addObjectPropertyToIndividual(dataset, "forms",
                        individualService.getOntIndividualByIri(dataset, ontologyUri),
                        "hasTargetClass", classIndividual.getURI());
            }
            individual.addProperty(
                    propertyService.getPropertyFromOntology(dataset, "forms", "targetsClass"),
                    classIndividual
            );

            dataset.commit();
        } catch (Exception e) {
            dataset.abort();
        } finally {
            dataset.end();
        }
    }

    public List<Form> getAllForms() {
        List<Form> forms = new ArrayList<>();
        Dataset dataset = TDB2Factory.connectDataset(ontologyDirectory);
        dataset.begin(ReadWrite.READ);
        try {
            var individuals = individualService.getAllIndividualsOfClass(dataset, "forms", "Form");
            for (var individual : individuals) {
                var formName = individual.getLocalName();
                var ontologyName = propertyService.getObjectPropertyValueFromIndividual(dataset,
                        "forms", individual, "targetsOntology").getLocalName();
                var targetClass = propertyService.getObjectPropertyValueFromIndividual(dataset,
                        "forms", individual, "targetsClass");
                var form = new Form(formName, ontologyName, new OntologyClass(targetClass.getLocalName(),
                        targetClass.getURI()));
                forms.add(form);
            }
            return forms;
        } finally {
            dataset.end();
        }
    }

    public List<Individual> getAllIndividualsOfForm(String formName) {
        Dataset dataset = TDB2Factory.connectDataset(ontologyDirectory);
        dataset.begin(ReadWrite.READ);
        try {
            var formIndividual = individualService.getIndividualByString(dataset, "forms", formName);
            var individuals = propertyService.getMultipleObjectPropertyValuesFromIndividual(dataset,
                    "forms", formIndividual, "created");
            var ontologyName = propertyService.getObjectPropertyValueFromIndividual(dataset,
                    "forms", formIndividual, "targetsOntology").getLocalName();
            List<Individual> result = new ArrayList<>();
            for (var individual : individuals) {
                var isDraft = propertyService.getDatatypePropertyValueFromIndividual(dataset, "forms",
                        individual, "isDraft");
                if (isDraft != null && isDraft.getBoolean()) continue;
                result.add(new Individual(individual.getLocalName(),
                        propertyService.getLabelOfIndividual(dataset, ontologyName, individual.getURI()),
                        individual.getURI(),
                        new OntologyClass("test", "test"), true));
            }
            return result;
        } finally {
            dataset.end();
        }
    }

    public List<List<Individual>> getAllDraftsOfForms(List<Form> forms) {
        List<List<Individual>> drafts = new ArrayList<>();
        for (var form : forms) {
            drafts.add(getAllDraftsOfForm(form.getFormName()));
        }
        return drafts;
    }

    public List<Individual> getAllDraftsOfForm(String formName) {
        Dataset dataset = TDB2Factory.connectDataset(ontologyDirectory);
        dataset.begin(ReadWrite.READ);
        try {
            return getAllDraftsOfForm(dataset, formName);
        } finally {
            dataset.end();
        }
    }

    private List<Individual> getAllDraftsOfForm(Dataset dataset, String formName) {
        var formIndividual = individualService.getIndividualByString(dataset, "forms", formName);
        var ontologyName = propertyService.getObjectPropertyValueFromIndividual(dataset,
                "forms", formIndividual, "targetsOntology").getLocalName();
        var individuals = propertyService.getMultipleObjectPropertyValuesFromIndividual(dataset,
                "forms", formIndividual, "created");
        List<Individual> result = new ArrayList<>();
        for (var individual : individuals) {
            var isDraft = propertyService.getDatatypePropertyValueFromIndividual(dataset, "forms",
                    individual, "isDraft");
            if (isDraft == null || !isDraft.getBoolean()) continue;
            result.add(new Individual(individual.getLocalName(),
                    propertyService.getLabelOfIndividual(dataset, ontologyName, individual.getURI()),
                    individual.getURI(),
                    new OntologyClass("test", "test"), true));
        }
        return result;
    }

    public void deleteForm(String formName) {
        Dataset dataset = TDB2Factory.connectDataset(ontologyDirectory);
        dataset.begin(ReadWrite.WRITE);
        try {
            for (var draft : getAllDraftsOfForm(dataset, formName)) {
                individualService.deleteIndividualByIri(dataset, "forms", draft.getIri());
            }

            individualService.deleteIndividual(dataset, "forms", formName);

            dataset.commit();
        } catch (Exception e) {
            dataset.abort();
        } finally {
            dataset.end();
        }
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
