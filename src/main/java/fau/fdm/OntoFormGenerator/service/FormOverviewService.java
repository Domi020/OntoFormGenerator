package fau.fdm.OntoFormGenerator.service;

import fau.fdm.OntoFormGenerator.data.Form;
import fau.fdm.OntoFormGenerator.tdb.IndividualService;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.tdb2.TDB2Factory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Service
public class FormOverviewService {

    private final Logger logger;

    @Value("${ontoformgenerator.ontologyDirectory}")
    private String ontologyDirectory;

    @Value("${ontoformgenerator.ontologies.forms}")
    private String formsIRI;

    private final IndividualService individualService;

    @Autowired
    public FormOverviewService(IndividualService individualService) {
        this.logger = LoggerFactory.getLogger(OntologyOverviewService.class);
        this.individualService = individualService;
    }

    public void addNewForm(String formName, String ontologyName, String ontologyURI) {
        Dataset dataset = TDB2Factory.connectDataset(ontologyDirectory);
        dataset.begin(ReadWrite.WRITE);
        var individual = individualService.addIndividual(dataset, "Form", formName);
        individualService.addObjectPropertyToIndividual(dataset,
                "forms", individual, "targetsOntology", ontologyURI);
        dataset.commit();
        dataset.end();
    }

    public List<Form> getAllForms() {
        List<Form> forms = new ArrayList<>();
        Dataset dataset = TDB2Factory.connectDataset(ontologyDirectory);
        dataset.begin(ReadWrite.READ);
        var individuals = individualService.getAllIndividualsOfClass(dataset, "forms", "Form");
        for (var individual : individuals) {
            var formName = individual.getLocalName();
            var ontologyName = individualService.getObjectPropertyFromIndividual(dataset,
                    "forms", individual, "targetsOntology").getLocalName();
            var form = new Form(formName, ontologyName);
            forms.add(form);
        }
        dataset.end();
        return forms;
    }
}
