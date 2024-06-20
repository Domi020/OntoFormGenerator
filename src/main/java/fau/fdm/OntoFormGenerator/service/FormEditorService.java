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

        // Set targetsClass
        var classIri = individualService.findIriOfClass(dataset, formInput.getFirst("ontologyClass"));
        var classIndividual = individualService.getOrAddIndividualByString(dataset, classIri, "TargetClass");
        form.addProperty(
                individualService.getPropertyFromOntology(dataset, "forms", "targetsClass"),
                classIndividual
        );
        //formInput.forEach((key, value) -> {
        //    // Set targetsClass
//
//
        //    var property = individualService.getIndividualByString(dataset, "forms", key);
        //    //form.addProperty(property, value.get(0));
        //});
        dataset.commit();
        dataset.end();
    }
}
