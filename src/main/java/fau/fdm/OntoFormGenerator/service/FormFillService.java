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
import org.apache.jena.ontapi.model.OntIndividual;
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

@Service
public class FormFillService {

    private final Logger logger;

    private final IndividualService individualService;

    private final GeneralTDBService generalTDBService;
    private final PropertyService propertyService;

    public FormFillService(IndividualService individualService,
                           GeneralTDBService generalTDBService, PropertyService propertyService) {
        this.individualService = individualService;
        this.logger = LoggerFactory.getLogger(OntologyOverviewService.class);
        this.generalTDBService = generalTDBService;
        this.propertyService = propertyService;
    }

    public void deleteIndividualByIri(String ontologyName, String individualUri) {
        try (TDBConnection connection = new TDBConnection(ReadWrite.WRITE, ontologyName)) {
            individualService.deleteIndividualByIri(connection.getDataset(), ontologyName, individualUri);
            individualService.deleteIndividualByIri(connection.getDataset(), "forms", individualUri);
            connection.commit();
        }
    }

    public void deleteDraft(String formName,
                            String draftUri) {
        try (TDBConnection connection = new TDBConnection(ReadWrite.WRITE, null)) {
            individualService.deleteIndividualByIri(connection.getDataset(), "forms", draftUri);
            connection.commit();
        }
    }

    public void createDraftFromFilledForm(String formName,
                                          String ontologyName,
                                          String targetField,
                                          String instanceName,
                                          Map<String, List<String>> formValues,
                                          Map<String, List<String>> additionalValues) {
        try (TDBConnection connection = new TDBConnection(ReadWrite.WRITE, ontologyName)) {
            var dataset = connection.getDataset();
            var classURI = generalTDBService.getClassURIInOntology(dataset, ontologyName, targetField);
            var ontologyURI = classURI.substring(0, classURI.lastIndexOf("#") + 1);
            var individualURI = ontologyURI + instanceName;

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

            var indiv = individualService.getIndividualByIri(dataset, individualURI);
            boolean alreadyCreated = true;
            if (indiv == null) {
                indiv = individualService.addIndividualWithURI(dataset, "Individual", individualURI);
                alreadyCreated = false;
            }

            if (alreadyCreated) {
                propertyService.removePropertyValueFromIndividual(dataset, "forms", indiv,
                        "hasDraft");
            } else {
                var form = individualService.getIndividualByString(dataset, "forms", formName);
                propertyService.addObjectPropertyToIndividual(dataset, "forms", form,
                        "created", individualURI);
                propertyService.addDatatypePropertyToIndividual(dataset, "forms", indiv,
                        "isDraft", "true", XSDDatatype.XSDboolean);
            }
            propertyService.addDatatypePropertyToIndividual(dataset, "forms", indiv,
                    "hasDraft", json.toString(), XSDDatatype.XSDstring);
            connection.commit();
        }
    }

    public List<SetField> getSetFieldsByDraft(String formName, String individualName, String ontologyName) {
        try (TDBConnection connection = new TDBConnection(ReadWrite.READ, ontologyName)) {
            var form = individualService.getIndividualByString(connection.getDataset(), "forms", formName);
            var individual = individualService.findOntIndividualInOntology(connection.getDataset(), "forms", individualName);
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

    public List<FormField> getAllAdditionalElementsOfDraft(String formName, String ontologyName,
                                                           String individualName) {
        try (TDBConnection connection = new TDBConnection(ReadWrite.READ, ontologyName)) {
            var individual = individualService.findOntIndividualInOntology(connection.getDataset(), "forms", individualName);
            var draft = propertyService.getDatatypePropertyValueFromIndividual(connection.getDataset(), "forms", individual, "hasDraft");
            var gson = new Gson();
            var draftMap = gson.fromJson(draft.getString(), Map.class);
            var formFields = new ArrayList<FormField>();
            var fields = (Map) draftMap.get("additionalFields");
            var form = individualService.getIndividualByString(connection.getDataset(), "forms", formName);
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

    public void addFieldElementToInstance(String formName, String individualName,
                                          String propertyName) {
        try (TDBConnection connection = new TDBConnection(ReadWrite.WRITE, null)) {
            var individual = individualService.findOntIndividualInOntology(connection.getDataset(), "forms", individualName);
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
        }
    }

    public String createIndividualFromFilledForm(String formName,
                                               String ontologyName,
                                               String targetField,
                                               String instanceName,
                                               Map<String, String[]> formValues) {
        try (TDBConnection connection = new TDBConnection(ReadWrite.WRITE, ontologyName)) {
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
            var indiv = individualService.getOntIndividualByIri(connection.getDataset(), individual.getURI());
            if (indiv == null) {
                // no draft exists
                individualService.addIndividualWithURI(connection.getDataset(), "Individual", individual.getURI());
                var form = individualService.getIndividualByString(connection.getDataset(), "forms", formName);
                propertyService.addObjectPropertyToIndividual(connection.getDataset(), "forms", form,
                        "created", individual.getURI());
            } else {
                // draft already exists
                propertyService.removePropertyValueFromIndividual(connection.getDataset(), "forms", indiv,
                        "hasDraft");
                propertyService.removePropertyValueFromIndividual(connection.getDataset(), "forms", indiv,
                        "isDraft");
            }
            connection.commit();
            return individual.getURI();
        } catch (Exception e) {
            return null;
        }
    }
    //TODO: import-Vorgang prüfen

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
