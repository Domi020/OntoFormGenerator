package fau.fdm.OntoFormGenerator.service;

import com.google.gson.Gson;
import fau.fdm.OntoFormGenerator.data.*;
import fau.fdm.OntoFormGenerator.tdb.GeneralTDBService;
import fau.fdm.OntoFormGenerator.tdb.IndividualService;
import fau.fdm.OntoFormGenerator.tdb.PropertyService;
import fau.fdm.OntoFormGenerator.tdb.TDBConnection;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.ontapi.model.OntIndividual;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    public FormEditorService(IndividualService individualService, PropertyService propertyService, GeneralTDBService generalTDBService, OntologyConstraintService ontologyConstraintService) {
        this.individualService = individualService;
        this.propertyService = propertyService;
        this.logger = LoggerFactory.getLogger(OntologyOverviewService.class);
        this.generalTDBService = generalTDBService;
        this.ontologyConstraintService = ontologyConstraintService;
    }

    public OntologyClass getSelectedEditorClass(String formName) {
        try (TDBConnection connection = new TDBConnection(ReadWrite.READ, null)) {
            var form = individualService.getIndividualByString(connection.getDataset(), "forms", formName);
            var classValue = propertyService.getObjectPropertyValueFromIndividual(connection.getDataset(),
                    "forms", form, "targetsClass");
            if (classValue == null) return null;
            return new OntologyClass(classValue.getLocalName(), classValue.getURI());
        }
    }

    public List<FormField> getAllAdditionalElementsOfDraft(String formName, String ontologyName,
                                                           String individualName) {
        // TODO: properly add maximumValues and required to drafts
        try (TDBConnection connection = new TDBConnection(ReadWrite.READ, ontologyName)) {
            var individual = individualService.findOntIndividualInOntology(connection.getDataset(), "forms", individualName);
            var draft = propertyService.getDatatypePropertyValueFromIndividual(connection.getDataset(), "forms", individual, "hasDraft");
            var gson = new Gson();
            var draftMap = gson.fromJson(draft.getString(), Map.class);
            var formFields = new ArrayList<FormField>();
            var fields = (Map) draftMap.get("additionalFields");
            // var targetField = propertyService.getObjectPropertyValueFromIndividual(dataset,
            //         "forms", individual, "targetsClass");
            for (var field : fields.keySet()) {
                var fieldName = (String) field;

                var property = propertyService.getPropertyFromOntology(connection.getDataset(), ontologyName, fieldName);
                var isObjectProperty = generalTDBService.checkIfObjectProperty(connection.getDataset(), ontologyName, property.getURI());
                if (isObjectProperty) {
                    var objectRangeProp = propertyService.getPropertyFromOntologyByIRI(connection.getDataset(), ontologyName, property.getURI()).getRange();
                    var objectRange = new OntologyClass(objectRangeProp.getLocalName(), objectRangeProp.getURI());
                    formFields.add(new FormField(
                            new OntologyProperty(fieldName,
                                    new OntologyClass(null, null), property.getURI(),
                                    true, objectRange, null), "ObjectSelect", fieldName,
                            1, 1, true));
                } else {
                    var dataRangeProp = propertyService.getPropertyFromOntologyByIRI(connection.getDataset(), ontologyName, property.getURI()).getRange();
                    formFields.add(new FormField(
                            new OntologyProperty(fieldName, new OntologyClass(null, null), property.getURI(),
                                    false, null, dataRangeProp.getLocalName()),
                            getFormType(dataRangeProp.getLocalName()), fieldName, 1, 1, true));
                }
            }
            return formFields;
        }
    }

    public List<FormField> getAllFormElementsOfForm(String formName) {
        try (TDBConnection connection = new TDBConnection(ReadWrite.READ, null)) {
            var dataset = connection.getDataset();
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
                var property = propertyService.getPropertyFromOntologyByIRI(dataset, ontologyName, targetField.getURI());
                if (isObjectProperty) {
                    // var property = propertyService.getPropertyFromOntologyByIRI(dataset, ontologyName, targetField.getURI());
                    var objectRangeProp = property.getRange();
                    var objectRange = new OntologyClass(objectRangeProp.getLocalName(), objectRangeProp.getURI());
                    formFields.set(position, new FormField(
                            new OntologyProperty(targetField.getLocalName(), domain, property.getURI(), true, objectRange, null),
                            fieldType, fieldName, maximumValues, minimumValues, required));
                } else {
                    // var property = propertyService.getPropertyFromOntologyByIRI(dataset, ontologyName, targetField.getURI());
                    var dataRangeProp = property.getRange();
                    formFields.set(position, new FormField(
                            new OntologyProperty(targetField.getLocalName(), domain, property.getURI(), false, null,
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
        }
    }

    public void updateForm(String formName, MultiValueMap<String, String> formInput) {
        try (TDBConnection connection = new TDBConnection(ReadWrite.WRITE, null)) {
            var dataset = connection.getDataset();
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
                var fieldName = formInput.get("fieldName").get(i);
                Resource foundElement = null;
                for (int j = 0; j < alreadyInsertedElements.size(); j++) {
                    if (alreadyInsertedElements.get(j) != null &&
                            alreadyInsertedElements.get(j).getLocalName().equals(fieldName)) {
                        foundElement = alreadyInsertedElements.get(j);
                        alreadyInsertedElements.set(j, null);
                        break;
                    }
                }

                // Remove old data
                if (foundElement != null) {
                    var oldField = individualService.getIndividualByIri(dataset, foundElement.getURI());
                    individualService.deleteIndividual(dataset, "forms", oldField.getLocalName());
                }

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
                if (formInput.containsKey("required-checkbox")) {
                    for (var val : formInput.get("required-checkbox")) {
                        if (val.equals("required-checkbox-" + i)) {
                            checked = "true";
                            break;
                        }
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
            connection.commit();
        } catch (Exception e) {
            logger.error("Error while updating form", e);
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
