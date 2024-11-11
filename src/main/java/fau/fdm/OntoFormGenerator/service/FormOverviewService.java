package fau.fdm.OntoFormGenerator.service;

import fau.fdm.OntoFormGenerator.data.Form;
import fau.fdm.OntoFormGenerator.data.Individual;
import fau.fdm.OntoFormGenerator.data.Ontology;
import fau.fdm.OntoFormGenerator.data.OntologyClass;
import fau.fdm.OntoFormGenerator.tdb.GeneralTDBService;
import fau.fdm.OntoFormGenerator.tdb.IndividualService;
import fau.fdm.OntoFormGenerator.tdb.PropertyService;
import fau.fdm.OntoFormGenerator.tdb.TDBConnection;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.ReadWrite;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class FormOverviewService {

    private final Logger logger;
    private final GeneralTDBService generalTDBService;

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
        try (TDBConnection connection = new TDBConnection(ReadWrite.READ, ontologyName)) {
            var targetClassIndiv = individualService.findIndividualInOntology(connection.getDataset(), ontologyName, targetClass);

            var allForms = individualService.getAllIndividualsOfClass(connection.getDataset(), "forms", "Form");
            return allForms.stream()
                    .filter(form -> propertyService.getObjectPropertyValueFromIndividual(connection.getDataset(), "forms", form, "targetsClass")
                    .getLocalName().equals(targetClass))
                    .map(form -> new Form(form.getLocalName(), ontologyName,
                            new OntologyClass(targetClass, targetClassIndiv.getURI())))
                    .toList();
        }
    }

    public void addNewForm(String formName, String ontologyName, String ontologyURI,
                           String targetClass) {
        try (TDBConnection connection = new TDBConnection(ReadWrite.WRITE, ontologyName)) {
            var dataset = connection.getDataset();
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

            connection.commit();
        }
    }

    public List<Form> getAllForms() {
        List<Form> forms = new ArrayList<>();
        try (TDBConnection connection = new TDBConnection(ReadWrite.READ, null)) {
            var individuals = individualService.getAllIndividualsOfClass(connection.getDataset(), "forms", "Form");
            for (var individual : individuals) {
                var formName = individual.getLocalName();
                var ontologyName = propertyService.getObjectPropertyValueFromIndividual(connection.getDataset(),
                        "forms", individual, "targetsOntology").getLocalName();
                var targetClass = propertyService.getObjectPropertyValueFromIndividual(connection.getDataset(),
                        "forms", individual, "targetsClass");
                var form = new Form(formName, ontologyName, new OntologyClass(targetClass.getLocalName(),
                        targetClass.getURI()));
                forms.add(form);
            }
            return forms;
        }
    }

    public List<Individual> getAllIndividualsOfForm(String formName) {
        try (TDBConnection connection = new TDBConnection(ReadWrite.READ, null)) {
            var dataset = connection.getDataset();
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
        try (TDBConnection connection = new TDBConnection(ReadWrite.READ, null)) {
            return getAllDraftsOfForm(connection.getDataset(), formName);
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
            try {
                result.add(new Individual(individual.getLocalName(),
                        propertyService.getLabelOfIndividual(dataset, "forms", individual.getURI()),
                        individual.getURI(),
                        new OntologyClass("test", "test"), true));
            } catch (NullPointerException ignored) {}

        }
        return result;
    }

    //TODO: generell mehr Stabilität => Hauptseite lädt trotz Fehler in Form/Ontologie/Individual

    public void deleteForm(String formName) {
        try (TDBConnection connection = new TDBConnection(ReadWrite.WRITE, null)) {
            deleteForm(connection.getDataset(), formName);
            connection.commit();
        }
    }

    public void deleteForm(Dataset dataset, String formName) {
        for (var draft : getAllDraftsOfForm(dataset, formName)) {
            individualService.deleteIndividualByIri(dataset, "forms", draft.getIri());
        }
        var formElements = propertyService.getMultipleObjectPropertyValuesFromIndividual(dataset,
                "forms", individualService.getIndividualByString(dataset, "forms", formName),
                "hasFormElement");
        for (var formElement : formElements) {
            individualService.deleteIndividualByIri(dataset, "forms",
                    formElement.getURI());
        }
        individualService.deleteIndividual(dataset, "forms", formName);
    }

    public Ontology getOntologyOfForm(String formName) {
        try (TDBConnection connection = new TDBConnection(ReadWrite.READ, null)) {
            var formIndividual = individualService.getIndividualByString(connection.getDataset(), "forms", formName);
            var ontologyIndividual = propertyService.getObjectPropertyValueFromIndividual(connection.getDataset(),
                    "forms", formIndividual, "targetsOntology");
            return new Ontology(ontologyIndividual.getLocalName(), ontologyIndividual.getURI());
        }
    }
}
