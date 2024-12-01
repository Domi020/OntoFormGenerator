package fau.fdm.OntoFormGenerator.service;

import com.google.gson.Gson;
import fau.fdm.OntoFormGenerator.data.FormField;
import fau.fdm.OntoFormGenerator.data.OntologyClass;
import fau.fdm.OntoFormGenerator.data.OntologyProperty;
import fau.fdm.OntoFormGenerator.data.SetField;
import fau.fdm.OntoFormGenerator.tdb.GeneralTDBService;
import fau.fdm.OntoFormGenerator.tdb.IndividualService;
import fau.fdm.OntoFormGenerator.tdb.PropertyService;
import fau.fdm.OntoFormGenerator.tdb.TDBConnection;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.ontology.OntModelSpec;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.ModelFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service for all functions of the form filling process.
 */
@Service
public class FormFillService {

    private final Logger logger;

    private final IndividualService individualService;

    private final GeneralTDBService generalTDBService;
    private final PropertyService propertyService;

    @Value("${ontoformgenerator.ontologyDirectory}")
    private String ontologyDirectory;

    @Value("${ontoformgenerator.ontologies.forms}")
    private String formsOntologyIri;

    public FormFillService(IndividualService individualService,
                           GeneralTDBService generalTDBService, PropertyService propertyService) {
        this.individualService = individualService;
        this.logger = LoggerFactory.getLogger(FormFillService.class);
        this.generalTDBService = generalTDBService;
        this.propertyService = propertyService;
    }

    /**
     * Deletes an individual from the ontology with the given URI.
     * @param ontologyName The name of the ontology the individual is in.
     * @param individualUri The URI of the individual to delete.
     */
    public void deleteIndividualByIri(String ontologyName, String individualUri) {
        try (TDBConnection connection = new TDBConnection(ReadWrite.WRITE, ontologyDirectory, ontologyName)) {
            individualService.deleteIndividualByIri(connection.getDataset(), ontologyName, individualUri);
            individualService.deleteIndividualByIri(connection.getDataset(), "forms", individualUri);
            connection.commit();
        }
    }

    /**
     * Deletes an existing draft from the ontology.
     * @param formName The name of the form the draft is for.
     * @param draftUri The URI of the draft to delete.
     */
    public void deleteDraft(String formName,
                            String draftUri) {
        logger.info("Deleting draft with URI: " + draftUri);
        try (TDBConnection connection = new TDBConnection(ReadWrite.WRITE, ontologyDirectory, null)) {
            individualService.deleteIndividualByIri(connection.getDataset(), "forms", draftUri);
            logger.info("Deletion of draft successful.");
            connection.commit();
        }
    }

    /**
     * Creates a draft from a filled form or updates an existing draft.
     * @param formName The name of the form the draft is for.
     * @param ontologyName The name of the ontology the form is in.
     * @param instanceName The current name of the draft.
     * @param firstDraftName The name of draft under which the draft was created first - can be equal to instanceName.
     * @param formValues The values of the standard form fields.
     * @param additionalValues The values of the additional fields.
     */
    public void createDraftFromFilledForm(String formName,
                                          String ontologyName,
                                          String instanceName,
                                          String firstDraftName,
                                          Map<String, List<String>> formValues,
                                          Map<String, List<String>> additionalValues) {
        logger.info("Creating draft for form: " + formName + " with name: " + instanceName);
        try (TDBConnection connection = new TDBConnection(ReadWrite.WRITE, ontologyDirectory, ontologyName)) {
            var dataset = connection.getDataset();

            StringBuilder json = new StringBuilder("{\n");
            json.append("\"normalFields\": {\n");
            for (var formValue : formValues.keySet()) {
                if (formValues.get(formValue) == null || formValues.get(formValue).isEmpty())
                    continue;
                json.append("\"%s\": [\"%s\"],\n".formatted(formValue, String.join("\", \"", formValues.get(formValue))));
            }
            if (!formValues.isEmpty()) json.deleteCharAt(json.lastIndexOf(","));
            json.append("},\n");

            json.append("\"additionalFields\": {\n");
            for (var formValue : additionalValues.keySet()) {
                if (additionalValues.get(formValue) == null || additionalValues.get(formValue).isEmpty())
                    continue;
                json.append("\"%s\": [\"%s\"],\n".formatted(formValue, String.join("\", \"", additionalValues.get(formValue))));
            }
            if (!additionalValues.isEmpty()) json.deleteCharAt(json.lastIndexOf(","));
            json.append("}\n");
            json.append("}");
            logger.debug("Draft JSON: {}", json.toString());

            var baseIri = formsOntologyIri + "#" + firstDraftName;
            var indiv = individualService.getIndividualByIri(dataset, "forms", baseIri);
            boolean alreadyCreated = true;
            if (indiv == null) {
                indiv = individualService.addIndividualWithUniqueIRI(dataset, "Individual", baseIri);
                alreadyCreated = false;
            }

            if (alreadyCreated) {
                propertyService.removePropertyValueFromIndividual(dataset, "forms", indiv,
                        "hasDraft");
                propertyService.removePropertyValueFromIndividual(dataset, "forms", indiv,
                        "hasDraftName");
            } else {
                var form = individualService.getIndividualByLocalName(dataset, "forms", formName);
                propertyService.addObjectPropertyToIndividual(dataset, "forms", form,
                        "created", baseIri);
                propertyService.addDatatypePropertyToIndividual(dataset, "forms", indiv,
                        "isDraft", "true", XSDDatatype.XSDboolean);
            }
            propertyService.addDatatypePropertyToIndividual(dataset, "forms", indiv,
                    "hasDraft", json.toString(), XSDDatatype.XSDstring);
            propertyService.addDatatypePropertyToIndividual(dataset, "forms", indiv,
                    "hasDraftName", instanceName, XSDDatatype.XSDstring);
            connection.commit();
            logger.info("Draft creation successful.");
        } catch (Exception e) {
            logger.error("Draft creation failed: " + e.getMessage());
            throw e;
        }
    }

    /**
     * Get all already set fields in a draft.
     * @param formName The name of the form the draft is for.
     * @param individualName The name of the draft.
     * @param ontologyName The name of the ontology the form is in.
     * @return A list of all set fields in the draft.
     */
    public List<SetField> getSetFieldsByDraft(String formName, String individualName, String ontologyName) {
        try (TDBConnection connection = new TDBConnection(ReadWrite.READ, ontologyDirectory, ontologyName)) {
            var form = individualService.getIndividualByLocalName(connection.getDataset(), "forms", formName);
            var individual = individualService.findIndividualInOntology(connection.getDataset(), "forms", individualName);
            var draft = propertyService.getDatatypePropertyValueFromIndividual(connection.getDataset(), "forms", individual, "hasDraft");
            var gson = new Gson();
            var draftMap = gson.fromJson(draft.getString(), Map.class);
            var setFields = new ArrayList<SetField>();
            var additionalFields = (Map) draftMap.get("additionalFields");
            var normalFields = (Map) draftMap.get("normalFields");
            normalFields.putAll(additionalFields);
            for (var key : normalFields.keySet()) {
                setFields.add(new SetField(key.toString(), (List<String>) normalFields.get(key.toString())));
            }
            return setFields;
        }
    }

    /**
     * Get the current draft name of a draft, given the original/first draft name.
     * @param individualName The first name of the draft, as stated also in the URI.
     * @return The current name of the draft.
     */
    public String getCurrentDraftName(String individualName) {
        try (TDBConnection connection = new TDBConnection(ReadWrite.READ, ontologyDirectory, null)) {
            var individual = individualService.findIndividualInOntology(connection.getDataset(), "forms", individualName);
            var draft = propertyService.getDatatypePropertyValueFromIndividual(connection.getDataset(), "forms", individual, "hasDraftName");
            return draft.getString();
        }
    }

    /**
     * Get all additional form elements of a draft (without values).
     * @param formName The name of the form.
     * @param ontologyName The name of the ontology the form is in.
     * @param individualName The name of the draft.
     * @return A list of all additional form elements of the draft.
     */
    public List<FormField> getAllAdditionalFormElementsOfDraft(String formName, String ontologyName,
                                                               String individualName) {
        logger.info("Getting all additional form elements of draft: " + individualName + " in form: " + formName);
        try (TDBConnection connection = new TDBConnection(ReadWrite.READ, ontologyDirectory, ontologyName)) {
            var individual = individualService.findIndividualInOntology(connection.getDataset(), "forms", individualName);
            var draft = propertyService.getDatatypePropertyValueFromIndividual(connection.getDataset(), "forms", individual, "hasDraft");
            var gson = new Gson();
            var draftMap = gson.fromJson(draft.getString(), Map.class);
            var formFields = new ArrayList<FormField>();
            var fields = (Map) draftMap.get("additionalFields");
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

    /**
     * Add a new additional form field to a draft.
     * @param formName The name of the form.
     * @param individualName The name of the draft.
     * @param propertyName The name of the property to add.
     */
    public void addFieldElementToInstance(String formName, String individualName,
                                          String propertyName) {
        logger.info("Adding field element: " + propertyName + " to draft: " + individualName + " in form: " + formName);
        try (TDBConnection connection = new TDBConnection(ReadWrite.WRITE, ontologyDirectory, null)) {
            var individual = individualService.findIndividualInOntology(connection.getDataset(), "forms", individualName);
            var draft = propertyService.getDatatypePropertyValueFromIndividual(connection.getDataset(), "forms", individual, "hasDraft");
            var gson = new Gson();
            var draftMap = gson.fromJson(draft.getString(), Map.class);
            var additionalField = (Map) draftMap.get("additionalFields");
            additionalField.put(propertyName, new ArrayList<>());
            var json = gson.toJson(draftMap);
            propertyService.removePropertyValueFromIndividual(connection.getDataset(), "forms", individual,
                    "hasDraft");
            propertyService.addDatatypePropertyToIndividual(connection.getDataset(), "forms", individual,
                    "hasDraft", json, XSDDatatype.XSDstring);
            connection.commit();
            logger.info("Field element added successfully.");
        }
    }

    /**
     * Create an individual from a filled form. Activated with the "save" function.
     * Removes the draft if it exists.
     * @param formName The name of the form.
     * @param ontologyName The name of the ontology the form is in.
     * @param targetField The target class of the individual.
     * @param instanceName The name of the individual.
     * @param draftName The original/first name of the corresponding draft if it exists. Else, null.
     * @param formValues The values of the form fields.
     * @return The URI of the created individual.
     */
    public String createIndividualFromFilledForm(String formName,
                                               String ontologyName,
                                               String targetField,
                                               String instanceName,
                                               String draftName,
                                               Map<String, String[]> formValues) {
        logger.info("Creating individual from filled form: " + instanceName + " in form: " + formName);
        try (TDBConnection connection = new TDBConnection(ReadWrite.WRITE, ontologyDirectory, ontologyName)) {
            var ontology = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM, connection.getDataset().getNamedModel(ontologyName));
            var classURI = generalTDBService.getClassURIInOntology(connection.getDataset(), ontologyName, targetField);
            var ontologyURI = classURI.substring(0, classURI.lastIndexOf("#") + 1);
            var individual = ontology.createIndividual(ontologyURI + instanceName + "_" + UUID.randomUUID(),
                    ontology.getOntClass(classURI));
            individual.setLabel(instanceName, null);
            for (var formValue : formValues.keySet()) {
                if (formValue.equals("instanceName") || formValue.equals("ontologyName") || formValue.equals("targetClass") ||
                        formValue.equals("create-individual-dialog-option"))
                    continue;
                var propUri = generalTDBService.getPropertyURIInOntology(connection.getDataset(), ontologyName, formValue);
                var prop = ontology.getProperty(propUri);
                if (generalTDBService.checkIfObjectProperty(connection.getDataset(), ontologyName, prop.getURI())) {
                    for (var objectValue : formValues.get(formValue)) {
                        var objectIndividual = individualService.findIndividualInOntology(connection.getDataset(), ontologyName, objectValue);
                        individual.addProperty(prop, objectIndividual);
                    }
                } else {
                    var dtype = ontology.getDatatypeProperty(propUri).getRange().getLocalName();
                    for (var dataValue : formValues.get(formValue)) {
                        switch (dtype) {
                            case "int":
                                individual.addLiteral(prop, Integer.parseInt(dataValue));
                                break;
                            case "float":
                                individual.addLiteral(prop, Float.parseFloat(dataValue));
                                break;
                            case "double":
                                individual.addLiteral(prop, Double.parseDouble(dataValue));
                                break;
                            case "boolean":
                                individual.addLiteral(prop, Boolean.parseBoolean(dataValue));
                                break;
                            case "dateTime":
                                var lit = ontology.createTypedLiteral(dataValue, XSDDatatype.XSDdateTime);
                                individual.addLiteral(prop, lit);
                                break;
                            default:
                                individual.addLiteral(prop, dataValue);
                                break;
                        }
                    }
                }
            }
            if (draftName != null) {
                // draft already exists
                var completeDraftIri = formsOntologyIri + "#" + draftName;
                individualService.deleteIndividualByIri(connection.getDataset(), "forms", completeDraftIri);
            }
            individualService.addIndividualWithUniqueIRI(connection.getDataset(), "Individual", individual.getURI());
            var form = individualService.getIndividualByLocalName(connection.getDataset(), "forms", formName);
            propertyService.addObjectPropertyToIndividual(connection.getDataset(), "forms", form,
                    "created", individual.getURI());
            connection.commit();
            logger.info("Individual creation successful.");
            return individual.getURI();
        } catch (Exception e) {
            logger.error("Individual creation failed: " + e.getMessage());
            return null;
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
