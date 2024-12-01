package fau.fdm.OntoFormGenerator.service;

import fau.fdm.OntoFormGenerator.data.*;
import fau.fdm.OntoFormGenerator.tdb.GeneralTDBService;
import fau.fdm.OntoFormGenerator.tdb.IndividualService;
import fau.fdm.OntoFormGenerator.tdb.PropertyService;
import fau.fdm.OntoFormGenerator.tdb.TDBConnection;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.ReadWrite;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Service for getting basic form information especially for the index page.
 */
@Service
public class FormOverviewService {

    private final Logger logger;
    private final GeneralTDBService generalTDBService;

    private final IndividualService individualService;

    private final PropertyService propertyService;

    @Value("${ontoformgenerator.ontologyDirectory}")
    private String ontologyDirectory;

    @Autowired
    public FormOverviewService(IndividualService individualService, PropertyService propertyService, GeneralTDBService generalTDBService) {
        this.propertyService = propertyService;
        this.logger = LoggerFactory.getLogger(FormOverviewService.class);
        this.individualService = individualService;
        this.generalTDBService = generalTDBService;
    }

    /**
     * Get all forms that target a specific class in a specific ontology.
     * @param ontologyName The name of the ontology.
     * @param targetClass The class the forms target.
     * @return A list of forms that target the specified class.
     */
    public List<Form> getFormsWithTargetClass(String ontologyName, String targetClass) {
        try (TDBConnection connection = new TDBConnection(ReadWrite.READ, ontologyDirectory, ontologyName)) {
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

    /**
     * Add a new form to the database.
     * @param formName The name of the form.
     * @param ontologyName The name of the ontology the form targets.
     * @param ontologyURI The URI of the ontology the form targets.
     * @param targetClass The class the form targets.
     */
    public void addNewForm(String formName, String ontologyName, String ontologyURI,
                           String targetClass) {
        logger.info("Adding new form: " + formName + " targeting ontology: " + ontologyName);
        try (TDBConnection connection = new TDBConnection(ReadWrite.WRITE, ontologyDirectory, ontologyName)) {
            var dataset = connection.getDataset();
            var individual = individualService.addIndividualByLocalName(dataset, "Form", formName);
            propertyService.addObjectPropertyToIndividual(dataset,
                    "forms", individual, "targetsOntology", ontologyURI);

            // Set targetsClass
            var classIri = generalTDBService.getClassURIInOntology(dataset, ontologyName, targetClass);
            var ontologyUri = generalTDBService.getOntologyURIByOntologyName(dataset, ontologyName);
            var classIndividual = individualService.getIndividualByIri(dataset, "forms", classIri);
            if (classIndividual == null) {
                classIndividual = individualService.addIndividualWithUniqueIRI(dataset, "TargetClass", classIri);
                propertyService.addObjectPropertyToIndividual(dataset, "forms",
                        individualService.getIndividualByIri(dataset, "forms", ontologyUri),
                        "hasTargetClass", classIndividual.getURI());
            }
            individual.addProperty(
                    propertyService.getPropertyFromOntology(dataset, "forms", "targetsClass"),
                    classIndividual
            );
            logger.info("Form added: " + formName);
            connection.commit();
        } catch (Exception e) {
            logger.error("Error adding form: " + formName, e);
            throw e;
        }
    }

    /**
     * Get all forms of all ontologies in the database.
     * @return A list of all forms in the database.
     */
    public List<Form> getAllForms() {
        List<Form> forms = new ArrayList<>();
        try (TDBConnection connection = new TDBConnection(ReadWrite.READ, ontologyDirectory, null)) {
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

    /**
     * Get all created individuals of a form.
     * @param formName The name of the form.
     * @return A list of all created individuals of the form.
     */
    public List<Individual> getAllIndividualsOfForm(String formName) {
        try (TDBConnection connection = new TDBConnection(ReadWrite.READ, ontologyDirectory, null)) {
            var dataset = connection.getDataset();
            var formIndividual = individualService.getIndividualByLocalName(dataset, "forms", formName);
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

    /**
     * Get all drafts of a form list.
     * @param forms The list of forms for which the drafts should be retrieved.
     * @return A list drafts for each form in the list, corresponding to the order of the input list.
     */
    public List<List<Draft>> getAllDraftsOfForms(List<Form> forms) {
        List<List<Draft>> drafts = new ArrayList<>();
        for (var form : forms) {
            drafts.add(getAllDraftsOfForm(form.getFormName()));
        }
        return drafts;
    }

    /**
     * Get all drafts of a form.
     * @param formName The name of the form.
     * @return A list of all drafts of the form.
     */
    public List<Draft> getAllDraftsOfForm(String formName) {
        try (TDBConnection connection = new TDBConnection(ReadWrite.READ, ontologyDirectory, null)) {
            return getAllDraftsOfForm(connection.getDataset(), formName);
        }
    }

    /**
     * Get all drafts of a form.
     * @param dataset The opened TDB dataset to use.
     * @param formName The name of the form.
     * @return A list of all drafts of the form.
     */
    private List<Draft> getAllDraftsOfForm(Dataset dataset, String formName) {
        var formIndividual = individualService.getIndividualByLocalName(dataset, "forms", formName);
        var individuals = propertyService.getMultipleObjectPropertyValuesFromIndividual(dataset,
                "forms", formIndividual, "created");
        List<Draft> result = new ArrayList<>();
        for (var individual : individuals) {
            var isDraft = propertyService.getDatatypePropertyValueFromIndividual(dataset, "forms",
                    individual, "isDraft");
            if (isDraft == null || !isDraft.getBoolean()) continue;
            var currentDraftName = propertyService.getDatatypePropertyValueFromIndividual(dataset, "forms",
                    individual, "hasDraftName").getString();
            try {
                result.add(new Draft(currentDraftName,
                        propertyService.getLabelOfIndividual(dataset, "forms", individual.getURI()),
                        individual.getURI(),
                        new OntologyClass("test", "test"), true, individual.getLocalName()));
            } catch (NullPointerException ignored) {}

        }
        return result;
    }

    /**
     * Delete a form from the database.
     * @param formName The name of the form to delete.
     */
    public void deleteForm(String formName) {
        try (TDBConnection connection = new TDBConnection(ReadWrite.WRITE, ontologyDirectory, null)) {
            deleteForm(connection.getDataset(), formName);
            connection.commit();
        }
    }

    /**
     * Delete a form from the database.
     * @param dataset The opened TDB dataset to use.
     * @param formName The name of the form to delete.
     */
    public void deleteForm(Dataset dataset, String formName) {
        logger.info("Deleting form: " + formName);
        for (var draft : getAllDraftsOfForm(dataset, formName)) {
            individualService.deleteIndividualByIri(dataset, "forms", draft.getIri());
        }
        var formElements = propertyService.getMultipleObjectPropertyValuesFromIndividual(dataset,
                "forms", individualService.getIndividualByLocalName(dataset, "forms", formName),
                "hasFormElement");
        for (var formElement : formElements) {
            individualService.deleteIndividualByIri(dataset, "forms",
                    formElement.getURI());
        }
        individualService.deleteIndividualByLocalName(dataset, "forms", formName);
        logger.info("Form deleted: " + formName);
    }

    /**
     * Get the ontology of a form.
     * @param formName The name of the form.
     * @return The ontology the form targets.
     */
    public Ontology getOntologyOfForm(String formName) {
        try (TDBConnection connection = new TDBConnection(ReadWrite.READ, ontologyDirectory, null)) {
            var formIndividual = individualService.getIndividualByLocalName(connection.getDataset(), "forms", formName);
            var ontologyIndividual = propertyService.getObjectPropertyValueFromIndividual(connection.getDataset(),
                    "forms", formIndividual, "targetsOntology");
            return new Ontology(ontologyIndividual.getLocalName(), ontologyIndividual.getURI());
        }
    }
}
