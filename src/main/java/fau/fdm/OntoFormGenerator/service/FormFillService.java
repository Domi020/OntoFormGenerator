package fau.fdm.OntoFormGenerator.service;

import com.google.gson.Gson;
import fau.fdm.OntoFormGenerator.data.SetField;
import fau.fdm.OntoFormGenerator.tdb.GeneralTDBService;
import fau.fdm.OntoFormGenerator.tdb.IndividualService;
import fau.fdm.OntoFormGenerator.tdb.PropertyService;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.ontapi.OntModelFactory;
import org.apache.jena.ontapi.OntSpecification;
import org.apache.jena.ontapi.model.OntIndividual;
import org.apache.jena.ontology.OntModelSpec;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.tdb2.TDB2Factory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class FormFillService {

    private final Logger logger;

    private final IndividualService individualService;

    private final GeneralTDBService generalTDBService;
    private final PropertyService propertyService;

    @Value("${ontoformgenerator.ontologyDirectory}")
    private String ontologyDirectory;

    public FormFillService(IndividualService individualService,
                           GeneralTDBService generalTDBService, PropertyService propertyService) {
        this.individualService = individualService;
        this.logger = LoggerFactory.getLogger(OntologyOverviewService.class);
        this.generalTDBService = generalTDBService;
        this.propertyService = propertyService;
    }

    public void deleteIndividualByIri(String ontologyName, String individualUri) {
        Dataset dataset = TDB2Factory.connectDataset(ontologyDirectory);
        dataset.begin(ReadWrite.WRITE);
        try {
            individualService.deleteIndividualByIri(dataset, ontologyName, individualUri);
            individualService.deleteIndividualByIri(dataset, "forms", individualUri);
            dataset.commit();
        } catch (Exception e) {
            dataset.abort();
        } finally {
            dataset.end();
        }
    }

    public void createDraftFromFilledForm(String formName,
                                          String ontologyName,
                                          String targetField,
                                          String instanceName,
                                          Map<String, List<String>> formValues,
                                          Map<String, List<String>> additionalValues) {
        Dataset dataset = TDB2Factory.connectDataset(ontologyDirectory);
        dataset.begin(ReadWrite.WRITE);
        try {
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

            OntIndividual indiv = individualService.getIndividualByIri(dataset, individualURI);
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
            dataset.commit();
        } catch (Exception e) {
            dataset.abort();
        } finally {
            dataset.end();
        }
    }

    public List<SetField> getSetFieldsByDraft(String formName, String individualName, String ontologyName) {
        Dataset dataset = TDB2Factory.connectDataset(ontologyDirectory);
        dataset.begin(ReadWrite.READ);
        try {
            var form = individualService.getIndividualByString(dataset, "forms", formName);
            var individual = individualService.findOntIndividualInOntology(dataset, "forms", individualName);
            var draft = propertyService.getDatatypePropertyValueFromIndividual(dataset, "forms", individual, "hasDraft");
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
        } finally {
            dataset.end();
        }
    }

    public void addFieldElementToInstance(String formName, String individualName,
                                          String propertyName) {
        Dataset dataset = TDB2Factory.connectDataset(ontologyDirectory);
        dataset.begin(ReadWrite.WRITE);
        try {
            var individual = individualService.findOntIndividualInOntology(dataset, "forms", individualName);
            var draft = propertyService.getDatatypePropertyValueFromIndividual(dataset, "forms", individual, "hasDraft");
            var gson = new Gson();
            var draftMap = gson.fromJson(draft.getString(), Map.class);
            var additionalField = (Map) draftMap.get("additionalFields");
            additionalField.put(propertyName, new ArrayList<>());
            var json = gson.toJson(draftMap);
            propertyService.removePropertyValueFromIndividual(dataset, "forms", individual,
                    "hasDraft");
            propertyService.addDatatypePropertyToIndividual(dataset, "forms", individual,
                    "hasDraft", json, XSDDatatype.XSDstring);
            dataset.commit();
        } catch (Exception e) {
            dataset.abort();
        } finally {
            dataset.end();
        }
    }

    public String createIndividualFromFilledForm(String formName,
                                               String ontologyName,
                                               String targetField,
                                               String instanceName,
                                               Map<String, String[]> formValues) {
        Dataset dataset = TDB2Factory.connectDataset(ontologyDirectory);
        dataset.begin(ReadWrite.WRITE);
        try {
            var ontology = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM, dataset.getNamedModel(ontologyName));
            var classURI = generalTDBService.getClassURIInOntology(dataset, ontologyName, targetField);
            var ontologyURI = classURI.substring(0, classURI.lastIndexOf("#") + 1);
            var individual = ontology.createIndividual(ontologyURI + instanceName,
                    ontology.getOntClass(classURI));
            for (var formValue : formValues.keySet()) {
                if (formValue.equals("instanceName") || formValue.equals("ontologyName") || formValue.equals("targetClass") ||
                        formValue.equals("create-individual-dialog-option"))
                    continue;
                var propUri = generalTDBService.getPropertyURIInOntology(dataset, ontologyName, formValue);
                var prop = ontology.getProperty(propUri);
                if (generalTDBService.checkIfObjectProperty(dataset, ontologyName, prop.getURI())) {
                    for (var objectValue : formValues.get(formValue)) {
                        var objectIndividual = individualService.findIndividualInOntology(dataset, ontologyName, objectValue);
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
            OntIndividual indiv = individualService.getOntIndividualByIri(dataset, individual.getURI());
            if (indiv == null) {
                // no draft exists
                individualService.addIndividualWithURI(dataset, "Individual", individual.getURI());
                var form = individualService.getIndividualByString(dataset, "forms", formName);
                propertyService.addObjectPropertyToIndividual(dataset, "forms", form,
                        "created", individual.getURI());
            } else {
                // draft already exists
                propertyService.removePropertyValueFromIndividual(dataset, "forms", indiv,
                        "hasDraft");
                propertyService.removePropertyValueFromIndividual(dataset, "forms", indiv,
                        "isDraft");
            }
            dataset.commit();
            return individual.getURI();
        } catch (Exception e) {
            dataset.abort();
            return null;
        } finally {
            dataset.end();
        }
    }
}
