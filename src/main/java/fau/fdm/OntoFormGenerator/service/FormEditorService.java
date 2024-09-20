package fau.fdm.OntoFormGenerator.service;

import com.google.gson.Gson;
import fau.fdm.OntoFormGenerator.data.Constraint;
import fau.fdm.OntoFormGenerator.data.FormField;
import fau.fdm.OntoFormGenerator.data.OntologyClass;
import fau.fdm.OntoFormGenerator.data.OntologyProperty;
import fau.fdm.OntoFormGenerator.tdb.GeneralTDBService;
import fau.fdm.OntoFormGenerator.tdb.IndividualService;
import fau.fdm.OntoFormGenerator.tdb.PropertyService;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.ontapi.model.OntIndividual;
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
import java.util.Map;

@Service
public class FormEditorService {

    private final Logger logger;

    private final IndividualService individualService;

    private final PropertyService propertyService;
    private final GeneralTDBService generalTDBService;
    private final OntologyConstraintService ontologyConstraintService;

    @Value("${ontoformgenerator.ontologyDirectory}")
    private String ontologyDirectory;

    public FormEditorService(IndividualService individualService, PropertyService propertyService, GeneralTDBService generalTDBService, OntologyConstraintService ontologyConstraintService) {
        this.individualService = individualService;
        this.propertyService = propertyService;
        this.logger = LoggerFactory.getLogger(OntologyOverviewService.class);
        this.generalTDBService = generalTDBService;
        this.ontologyConstraintService = ontologyConstraintService;
    }

    public OntologyClass getSelectedEditorClass(String formName) {
        Dataset dataset = TDB2Factory.connectDataset(ontologyDirectory);
        dataset.begin(ReadWrite.READ);
        try {
            var form = individualService.getIndividualByString(dataset, "forms", formName);
            var classValue = propertyService.getObjectPropertyValueFromIndividual(dataset,
                    "forms", form, "targetsClass");
            if (classValue == null) return null;
            return new OntologyClass(classValue.getLocalName(), classValue.getURI());
        } finally {
            dataset.end();
        }
    }

    public List<FormField> getAllAdditionalElementsOfDraft(String formName, String ontologyName,
                                                           String individualName) {
        // TODO: properly add maximumValues and required to drafts
        Dataset dataset = TDB2Factory.connectDataset(ontologyDirectory);
        dataset.begin(ReadWrite.READ);
        try {
            var individual = individualService.findOntIndividualInOntology(dataset, "forms", individualName);
            var draft = propertyService.getDatatypePropertyValueFromIndividual(dataset, "forms", individual, "hasDraft");
            var gson = new Gson();
            var draftMap = gson.fromJson(draft.getString(), Map.class);
            var formFields = new ArrayList<FormField>();
            var fields = (Map) draftMap.get("additionalFields");
            // var targetField = propertyService.getObjectPropertyValueFromIndividual(dataset,
            //         "forms", individual, "targetsClass");
            for (var field : fields.keySet()) {
                var fieldName = (String) field;

                var property = propertyService.getPropertyFromOntology(dataset, ontologyName, fieldName);
                var isObjectProperty = generalTDBService.checkIfObjectProperty(dataset, ontologyName, property.getURI());
                if (isObjectProperty) {
                    var objectRangeProp = propertyService.getPropertyFromOntologyByIRI(dataset, ontologyName, property.getURI()).getRange();
                    var objectRange = new OntologyClass(objectRangeProp.getLocalName(), objectRangeProp.getURI());
                    formFields.add(new FormField(
                            new OntologyProperty(fieldName,
                                    new OntologyClass(null, null),
                                    true, objectRange, null), "ObjectSelect", fieldName,
                            1, 1, true));
                } else {
                    var dataRangeProp = propertyService.getPropertyFromOntologyByIRI(dataset, ontologyName, property.getURI()).getRange();
                    formFields.add(new FormField(
                            new OntologyProperty(fieldName, new OntologyClass(null, null),
                                    false, null, dataRangeProp.getLocalName()),
                            getFormType(dataRangeProp.getLocalName()), fieldName, 1, 1, true));
                }
            }
            return formFields;
        } finally {
            dataset.end();
        }
    }

    public List<FormField> getAllFormElementsOfForm(String formName) {
        Dataset dataset = TDB2Factory.connectDataset(ontologyDirectory);
        dataset.begin(ReadWrite.READ);
        try {
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
                var maximumValues = propertyService.getDatatypePropertyValueFromIndividual(dataset,
                        "forms", formElementIndividual, "hasMaximumValues").getInt();
                var minimumValues = propertyService.getDatatypePropertyValueFromIndividual(dataset,
                        "forms", formElementIndividual, "hasMinimumValues").getInt();
                var required = propertyService.getDatatypePropertyValueFromIndividual(dataset,
                        "forms", formElementIndividual, "required").getBoolean();
                var domain = new OntologyClass(targetField.getLocalName(), targetField.getURI());
                if (isObjectProperty) {
                    var objectRangeProp = propertyService.getPropertyFromOntologyByIRI(dataset, ontologyName, targetField.getURI()).getRange();
                    var objectRange = new OntologyClass(objectRangeProp.getLocalName(), objectRangeProp.getURI());
                    formFields.set(position, new FormField(
                            new OntologyProperty(targetField.getLocalName(), domain, true, objectRange, null),
                            fieldType, fieldName, maximumValues, minimumValues, required));
                } else {
                    var dataRangeProp = propertyService.getPropertyFromOntologyByIRI(dataset, ontologyName, targetField.getURI()).getRange();
                    formFields.set(position, new FormField(
                            new OntologyProperty(targetField.getLocalName(), domain, false, null,
                                    dataRangeProp.getLocalName()), fieldType, fieldName, maximumValues,
                            minimumValues, required));
                }
            }
            for (int i = 0; i < formFields.size(); i++) {
                if (formFields.get(i) == null) {
                    formFields.remove(i);
                    i--;
                }
            }
            return formFields;
        } finally {
            dataset.end();
        }
    }

    public void updateForm(String formName, MultiValueMap<String, String> formInput) {
        Dataset dataset = TDB2Factory.connectDataset(ontologyDirectory);
        dataset.begin(ReadWrite.WRITE);
        try {
            var form = individualService.getIndividualByString(dataset, "forms", formName);
            var ontology = propertyService.getObjectPropertyValueFromIndividual(dataset, "forms",
                    form, "targetsOntology");

            // Set targetsClass
            var classIri = individualService.findIriOfClass(dataset, formInput.getFirst("ontologyName"),
                    formInput.getFirst("ontologyClass"));
            var classIndividual = individualService.getOntIndividualByIri(dataset, classIri);
            if (classIndividual == null) {
                classIndividual = individualService.addIndividualWithURI(dataset, "TargetClass", classIri);
                propertyService.addObjectPropertyToIndividual(dataset, "forms",
                        individualService.getOntIndividualByIri(dataset, ontology.getURI()),
                        "hasTargetClass", classIndividual.getURI());
            }
            form.addProperty(
                    propertyService.getPropertyFromOntology(dataset, "forms", "targetsClass"),
                    classIndividual
            );
            var constraints = ontologyConstraintService.getConstraints(dataset, formInput.getFirst("ontologyName"),
                    classIri, null);

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
                    var fieldType = getFormType(formInput.get("propertyRange").get(i));
                    field = individualService.addIndividual(dataset, fieldType, fieldName);
                    propertyService.addDatatypePropertyToIndividual(dataset, "forms",
                            field, "isObjectProperty", "false", XSDDatatype.XSDboolean);
                }
                propertyService.addObjectPropertyToIndividual(dataset, "forms",
                        field, "targetsField", targetField.getURI());
                propertyService.addObjectPropertyToIndividual(dataset, "forms",
                        form, "hasFormElement", field.getURI());
                propertyService.addDatatypePropertyToIndividual(dataset, "forms",
                        field, "hasPositionInForm", String.valueOf(i), XSDDatatype.XSDint);
                var maximumValues = formInput.get("maximumValues").get(i);
                if (maximumValues == null || maximumValues.isEmpty()) {
                    maximumValues = "1";
                }
                var minimumValues = formInput.get("minimumValues").get(i);
                if (minimumValues == null || minimumValues.isEmpty()) {
                    minimumValues = "1";
                }
                if (Integer.parseInt(maximumValues) < Integer.parseInt(minimumValues)) {
                    throw new RuntimeException("Maximum values must be greater than minimum values");
                }
                var relevantConstraints = constraints.stream().filter(
                        constraint -> constraint.getOnProperty().getName().equals(propertyName)).toList();
                var maxConstraint = relevantConstraints.stream().filter(constraint -> constraint.getConstraintType() == Constraint.ConstraintType.MAX).findFirst();
                if (maxConstraint.isPresent() && Integer.parseInt(maximumValues) > (int) maxConstraint.get().getValue()) {
                    throw new RuntimeException("Maximum values must be less than or equal to " + maxConstraint.get().getValue());
                }
                var minConstraint = relevantConstraints.stream().filter(constraint -> constraint.getConstraintType() == Constraint.ConstraintType.MIN).findFirst();
                if (minConstraint.isPresent() && Integer.parseInt(minimumValues) < (int) minConstraint.get().getValue()) {
                    throw new RuntimeException("Minimum values must be greater than or equal to " + minConstraint.get().getValue());
                }
                propertyService.addDatatypePropertyToIndividual(dataset, "forms",
                        field, "hasMaximumValues", maximumValues,
                        XSDDatatype.XSDpositiveInteger);
                propertyService.addDatatypePropertyToIndividual(dataset, "forms",
                        field, "hasMinimumValues", minimumValues,
                        XSDDatatype.XSDinteger);
                String checked = "false";
                for (var val : formInput.get("required-checkbox")) {
                    if (val.equals("required-checkbox-" + i)) {
                        checked = "true";
                        break;
                    }
                }
                propertyService.addDatatypePropertyToIndividual(dataset, "forms",
                        field, "required", checked, XSDDatatype.XSDboolean);
            }

            // delete all old form elements that are not in the new form
            for (var alreadyInsertedElement : alreadyInsertedElements) {
                if (alreadyInsertedElement == null) continue;
                individualService.deleteIndividual(dataset, "forms", alreadyInsertedElement
                        .getLocalName());
            }
            dataset.commit();
        } catch (Exception e) {
            logger.error("Error while updating form", e);
            dataset.abort();
        } finally {
            dataset.end();
        }
    }

    private String getFormType(String datatype) {
        switch (datatype) {
            case "string" -> {
                return "Input";
            }
            case "boolean" -> {
                return "Select";
            }
            case "date" -> {
                return "Date";
            }
            case "dateTime", "dateTimeStamp" -> {
                return "Datetime";
            }
            case "int", "integer" -> {
                return "Number";
            }
            default -> {
                return null;
            }
        }
    }
}
