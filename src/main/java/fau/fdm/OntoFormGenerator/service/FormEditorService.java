package fau.fdm.OntoFormGenerator.service;

import fau.fdm.OntoFormGenerator.tdb.IndividualService;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.tdb2.TDB2Factory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;

@Service
public class FormEditorService {

    private final Logger logger;

    private final IndividualService individualService;

    @Value("${ontoformgenerator.ontologyDirectory}")
    private String ontologyDirectory;

    public FormEditorService(IndividualService individualService) {
        this.individualService = individualService;
        this.logger = LoggerFactory.getLogger(OntologyOverviewService.class);
    }

    public void updateForm(String formName, MultiValueMap<String, String> formInput) {
        // TODO
        // 1. targetsClass setzen
        // 2. pro Feld: targetsField setzen
        Dataset dataset = TDB2Factory.connectDataset(ontologyDirectory);
        dataset.begin(ReadWrite.WRITE);
        var form = individualService.getIndividualByString(dataset, "forms", formName);
        var ontology = individualService.getObjectPropertyValueFromIndividual(dataset, "forms",
                form, "targetsOntology");

        // Set targetsClass
        var classIri = individualService.findIriOfClass(dataset, formInput.getFirst("ontologyClass"));
        var classIndividual = individualService.getOrAddIndividualByString(dataset, classIri, "TargetClass");
        form.addProperty(
                individualService.getPropertyFromOntology(dataset, "forms", "targetsClass"),
                classIndividual
        );
        for (int i = 0; i < formInput.get("fieldName").size(); i++) {
            // set targetsField for each field
            var fieldName = formInput.get("fieldName").get(i);
            var propertyName = formInput.get("propertyName").get(i);
            var property = individualService.getPropertyFromOntology(dataset, ontology.getLocalName(),
                    ontology.getURI(), propertyName);
            var targetField = individualService.addIndividualWithURI(dataset, "TargetField",
                    property.getURI());
            if (formInput.get("isObjectProperty").get(i).equals("true")) {
                // object property

            } else {
                // datatype property
                var field = individualService.createDatatypeFormElement(dataset, fieldName,
                        formInput.get("propertyRange").get(i));
                individualService.addObjectPropertyToIndividual(dataset, "forms",
                        field, "targetsField", targetField.getURI());
                individualService.addObjectPropertyToIndividual(dataset, "forms",
                        form, "hasFormElement", field.getURI());
                individualService.addDatatypePropertyToIndividual(dataset, "forms",
                        field, "isObjectProperty", "false");
                individualService.addDatatypePropertyToIndividual(dataset, "forms",
                        field, "hasPositionInForm", String.valueOf(i));
            }
        }

        dataset.commit();
        dataset.end();
    }
}
