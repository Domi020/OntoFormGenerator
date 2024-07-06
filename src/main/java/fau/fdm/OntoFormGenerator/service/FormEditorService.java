package fau.fdm.OntoFormGenerator.service;

import fau.fdm.OntoFormGenerator.data.FormField;
import fau.fdm.OntoFormGenerator.data.OntologyClass;
import fau.fdm.OntoFormGenerator.data.OntologyProperty;
import fau.fdm.OntoFormGenerator.tdb.IndividualService;
import fau.fdm.OntoFormGenerator.tdb.PropertyService;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.ontapi.model.OntIndividual;
import org.apache.jena.ontology.Individual;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.tdb2.TDB2Factory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class FormEditorService {

    private final Logger logger;

    private final IndividualService individualService;

    private final PropertyService propertyService;

    @Value("${ontoformgenerator.ontologyDirectory}")
    private String ontologyDirectory;

    public FormEditorService(IndividualService individualService, PropertyService propertyService) {
        this.individualService = individualService;
        this.propertyService = propertyService;
        this.logger = LoggerFactory.getLogger(OntologyOverviewService.class);
    }

    public String getSelectedEditorClass(String formName) {
        Dataset dataset = TDB2Factory.connectDataset(ontologyDirectory);
        dataset.begin(ReadWrite.READ);
        try {
            var form = individualService.getIndividualByString(dataset, "forms", formName);
            var classValue = propertyService.getObjectPropertyValueFromIndividual(dataset,
                    "forms", form, "targetsClass");
            if (classValue == null) return null;
            return classValue.getLocalName();
        } finally {
            dataset.end();
        }
    }

    public List<FormField> getAllFormElementsOfForm(String formName) {
        Dataset dataset = TDB2Factory.connectDataset(ontologyDirectory);
        dataset.begin(ReadWrite.READ);
        var form = individualService.getIndividualByString(dataset, "forms", formName);
        var formElements = propertyService.getMultipleObjectPropertyValuesFromIndividual(dataset,
                "forms", form, "hasFormElement");
        var ontologyName = propertyService.getObjectPropertyValueFromIndividual(dataset,
                "forms", form, "targetsOntology").getLocalName();
        List<FormField> formFields = new ArrayList<>(Collections.nCopies(formElements.size() + 50, null));
        for (var formElement : formElements) {
            var fieldName = formElement.getLocalName();
            var formElementIndividual = individualService.getIndividualByIri(dataset, formElement.getURI());
            var fieldType = formElementIndividual.ontClass().get().getLocalName();
            var isObjectProperty = propertyService.getDatatypePropertyValueFromIndividual(dataset,
                    "forms", formElementIndividual, "isObjectProperty").getBoolean();
            var position = propertyService.getDatatypePropertyValueFromIndividual(dataset,
                    "forms", formElementIndividual, "hasPositionInForm").getInt();
            var targetField = propertyService.getObjectPropertyValueFromIndividual(dataset,
                    "forms", formElementIndividual, "targetsField");
            var domain = new OntologyClass(targetField.getLocalName(), targetField.getURI());
            if (isObjectProperty) {
                var objectRangeProp = propertyService.getPropertyFromOntologyByIRI(dataset, ontologyName, targetField.getURI()).getRange();
                var objectRange = new OntologyClass(objectRangeProp.getLocalName(), objectRangeProp.getURI());
                formFields.set(position, new FormField(
                        new OntologyProperty(targetField.getLocalName(), domain, true, objectRange, null),
                        fieldType, fieldName));
            } else {
                var dataRangeProp = propertyService.getPropertyFromOntologyByIRI(dataset, ontologyName, targetField.getURI()).getRange();
                formFields.set(position, new FormField(
                        new OntologyProperty(targetField.getLocalName(), domain, false, null,
                        dataRangeProp.getLocalName()), fieldType, fieldName));
            }
        }
        for (int i = 0; i < formFields.size(); i++) {
            if (formFields.get(i) == null) {
                formFields.remove(i);
                i--;
            }
        }
        dataset.end();
        return formFields;
    }

    public void updateForm(String formName, MultiValueMap<String, String> formInput) {
        Dataset dataset = TDB2Factory.connectDataset(ontologyDirectory);
        dataset.begin(ReadWrite.WRITE);
        var form = individualService.getIndividualByString(dataset, "forms", formName);
        var ontology = propertyService.getObjectPropertyValueFromIndividual(dataset, "forms",
                form, "targetsOntology");

        // Set targetsClass
        var classIri = individualService.findIriOfClass(dataset, formInput.getFirst("ontologyName"),
                formInput.getFirst("ontologyClass"));
        var classIndividual = individualService.getOrAddIndividualByString(dataset, classIri, "TargetClass");
        form.addProperty(
                propertyService.getPropertyFromOntology(dataset, "forms", "targetsClass"),
                classIndividual
        );
        // Get all already existing form elements
        var alreadyInsertedElements = propertyService.getMultipleObjectPropertyValuesFromIndividual(dataset,
                "forms", form, "hasFormElement");


        for (int i = 0; i < formInput.get("fieldName").size(); i++) {
            // Check if field already exists
            //TODO: Aktuell wird nur anhand Feldnamen geprüft, ob ein Feld schon existiert
            var fieldName = formInput.get("fieldName").get(i);
            boolean foundElement = false;
            for (int j = 0; j < alreadyInsertedElements.size(); j++) {
                if (alreadyInsertedElements.get(j) != null &&
                        alreadyInsertedElements.get(j).getLocalName().equals(fieldName)) {
                    alreadyInsertedElements.set(j, null);
                    foundElement = true;
                    break;
                }
            }
            if (foundElement) continue;

            // set targetsField for each field
            var propertyName = formInput.get("propertyName").get(i);
            var property = propertyService.getPropertyFromOntology(dataset, ontology.getLocalName(), propertyName);
            var targetField = individualService.addIndividualWithURI(dataset, "TargetField",
                    property.getURI());
            OntIndividual field;
            if (formInput.get("isObjectProperty").get(i).equals("true")) {
                // object property
                field = individualService.addIndividual(dataset, "ObjectSelect", fieldName);
                propertyService.addDatatypePropertyToIndividual(dataset, "forms",
                        field, "isObjectProperty", "true", XSDDatatype.XSDboolean);
            } else {
                // datatype property
                field = individualService.createDatatypeFormElement(dataset, fieldName,
                        formInput.get("propertyRange").get(i));
                propertyService.addDatatypePropertyToIndividual(dataset, "forms",
                        field, "isObjectProperty", "false", XSDDatatype.XSDboolean);
            }
            propertyService.addObjectPropertyToIndividual(dataset, "forms",
                    field, "targetsField", targetField.getURI());
            propertyService.addObjectPropertyToIndividual(dataset, "forms",
                    form, "hasFormElement", field.getURI());
            propertyService.addDatatypePropertyToIndividual(dataset, "forms",
                    field, "hasPositionInForm", String.valueOf(i), XSDDatatype.XSDint);
        }

        // delete all old form elements that are not in the new form
        for (var alreadyInsertedElement : alreadyInsertedElements) {
            if (alreadyInsertedElement == null) continue;
            individualService.deleteIndividual(dataset, "forms", alreadyInsertedElement
                    .getLocalName());
        }
        dataset.commit();
        dataset.end();
    }
}
