package fau.fdm.OntoFormGenerator.service;

import fau.fdm.OntoFormGenerator.data.*;
import fau.fdm.OntoFormGenerator.tdb.GeneralTDBService;
import fau.fdm.OntoFormGenerator.tdb.IndividualService;
import fau.fdm.OntoFormGenerator.tdb.PropertyService;
import fau.fdm.OntoFormGenerator.tdb.TDBConnection;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.ontology.Individual;
import org.apache.jena.ontology.impl.OntResourceImpl;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.impl.LiteralImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;

import java.util.*;

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
        try (TDBConnection connection = new TDBConnection(ReadWrite.READ, ontologyDirectory, null)) {
            var form = individualService.getIndividualByLocalName(connection.getDataset(), "forms", formName);
            var classValue = propertyService.getObjectPropertyValueFromIndividual(connection.getDataset(),
                    "forms", form, "targetsClass");
            if (classValue == null) return null;
            return new OntologyClass(classValue.getLocalName(), classValue.getURI());
        }
    }

    public List<FormField> getAllFormElementsOfForm(String formName) {
        try (TDBConnection connection = new TDBConnection(ReadWrite.READ, ontologyDirectory,"forms")) {
            var dataset = connection.getDataset();
            var form = individualService.getIndividualByLocalName(dataset, "forms", formName);
            var formElements = propertyService.getMultipleObjectPropertyValuesFromIndividual(dataset,
                    "forms", form, "hasFormElement");
            var ontologyName = propertyService.getObjectPropertyValueFromIndividual(dataset,
                    "forms", form, "targetsOntology").getLocalName();
            List<FormField> formFields = new ArrayList<>(Collections.nCopies(formElements.size() + 50, null));
            for (var formElement : formElements) {
                var fieldName = propertyService.getLabelOfIndividual(dataset, "forms", formElement.getURI());
                var formElementIndividual = individualService.getIndividualByIri(dataset, "forms", formElement.getURI());
                var fieldType = formElementIndividual.getOntClass().getLocalName();
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
                    OntologyClass objectRange;
                    var objectRangeProp = property.getRange();
                    if (objectRangeProp != null) {
                        objectRange = new OntologyClass(objectRangeProp.getLocalName(), objectRangeProp.getURI());
                    } else {
                        objectRange = new OntologyClass("Thing", "http://www.w3.org/2002/07/owl#Thing");
                    }
                    formFields.set(position, new FormField(
                            new OntologyProperty(targetField.getLocalName(), domain, property.getURI(), true, objectRange, null),
                            fieldType, fieldName, maximumValues, minimumValues, required));
                } else {
                    // var property = propertyService.getPropertyFromOntologyByIRI(dataset, ontologyName, targetField.getURI());
                    var dataRangeProp = property.getRange();
                    String dataRange;
                    if (dataRangeProp != null) {
                        dataRange = dataRangeProp.getLocalName();
                    } else {
                        dataRange = "string";
                    }
                    formFields.set(position, new FormField(
                            new OntologyProperty(targetField.getLocalName(), domain, property.getURI(), false, null,
                                    dataRange), fieldType, fieldName, maximumValues,
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
        try (TDBConnection connection = new TDBConnection(ReadWrite.WRITE, ontologyDirectory,"forms")) {
            var dataset = connection.getDataset();
            var form = individualService.getIndividualByLocalName(dataset, "forms", formName);
            var ontology = propertyService.getObjectPropertyValueFromIndividual(dataset, "forms",
                    form, "targetsOntology");

            // Consistency check
            if (formInput.get("fieldName").size() != formInput.get("fieldName").stream().distinct().count()) {
                throw new RuntimeException("Field names must be unique");
            }

            if (formInput.get("propertyName").size() != formInput.get("propertyName").stream().distinct().count()) {
                throw new RuntimeException("Properties must be unique");
            }

            // Set targetsClass
            var classIri = generalTDBService.getClassURIInOntology(dataset, formInput.getFirst("ontologyName"),
                    formInput.getFirst("ontologyClass"));
            var classIndividual = individualService.getIndividualByIri(dataset, "forms", classIri);
            if (classIndividual == null) {
                classIndividual = individualService.addIndividualWithUniqueIRI(dataset, "TargetClass", classIri);
                propertyService.addObjectPropertyToIndividual(dataset, "forms",
                        individualService.getIndividualByIri(dataset, "forms", ontology.getURI()),
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
                            propertyService.getLabelOfIndividual(dataset, "forms", alreadyInsertedElements.get(j).getURI())
                                    .equals(fieldName)) {
                        foundElement = alreadyInsertedElements.get(j);
                        alreadyInsertedElements.set(j, null);
                        break;
                    }
                }

                // Remove old data
                if (foundElement != null) {
                    var oldField = individualService.getIndividualByIri(dataset, "forms", foundElement.getURI());
                    individualService.deleteIndividualByLocalName(dataset, "forms", oldField.getLocalName());
                }

                // set targetsField for each field
                var propertyName = formInput.get("propertyName").get(i);
                var property = propertyService.getPropertyFromOntology(dataset, ontology.getLocalName(), propertyName);
                var targetField = individualService.addIndividualWithUniqueIRI(dataset, "TargetField",
                        property.getURI());
                Individual field;
                var fieldUri = "http://www.semanticweb.org/fau/ontologies/2024/ontoformgenerator/forms#" + fieldName +
                        UUID.randomUUID();
                if (formInput.get("isObjectProperty").get(i).equals("true")) {
                    // object property
                    field = individualService.addIndividualWithUniqueIRI(dataset, "ObjectSelect", fieldUri);
                    propertyService.addDatatypePropertyToIndividual(dataset, "forms",
                            field, "isObjectProperty", "true", XSDDatatype.XSDboolean);
                } else {
                    // datatype property
                    var fieldType = getFormType(formInput.get("propertyRange").get(i));
                    field = individualService.addIndividualWithUniqueIRI(dataset, fieldType, fieldUri);
                    propertyService.addDatatypePropertyToIndividual(dataset, "forms",
                            field, "isObjectProperty", "false", XSDDatatype.XSDboolean);
                }
                field.addLabel(connection.getModel().createTypedLiteral(fieldName));
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
                individualService.deleteIndividualByLocalName(dataset, "forms", alreadyInsertedElement
                        .getLocalName());
            }
            connection.commit();
        } catch (Exception e) {
            logger.error("Error while updating form", e);
            throw e;
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
