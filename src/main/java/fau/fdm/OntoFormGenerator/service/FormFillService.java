package fau.fdm.OntoFormGenerator.service;

import fau.fdm.OntoFormGenerator.tdb.GeneralTDBService;
import fau.fdm.OntoFormGenerator.tdb.IndividualService;
import org.apache.jena.ontapi.OntModelFactory;
import org.apache.jena.ontapi.OntSpecification;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.tdb2.TDB2Factory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;

@Service
public class FormFillService {

    private final Logger logger;

    private final IndividualService individualService;

    private final GeneralTDBService generalTDBService;

    @Value("${ontoformgenerator.ontologyDirectory}")
    private String ontologyDirectory;

    public FormFillService(IndividualService individualService,
                           GeneralTDBService generalTDBService) {
        this.individualService = individualService;
        this.logger = LoggerFactory.getLogger(OntologyOverviewService.class);
        this.generalTDBService = generalTDBService;
    }

    public void createIndividualFromFilledForm(String formName,
                                               String ontologyName,
                                               String targetField,
                                               String instanceName,
                                               MultiValueMap<String, String> formValues) {
        Dataset dataset = TDB2Factory.connectDataset(ontologyDirectory);
        dataset.begin(ReadWrite.WRITE);
        try {
            var ontologyURI = generalTDBService.getOntologyURIByOntologyName(dataset, ontologyName);
            if (ontologyURI.endsWith("#")) ontologyURI = ontologyURI.substring(0, ontologyURI.length() - 1);
            var ontology = OntModelFactory.createModel(dataset.getNamedModel(ontologyName).getGraph(),
                    OntSpecification.OWL2_DL_MEM);
            var individual = ontology.createIndividual(ontologyURI + "/" + targetField + "#" + instanceName,
                    ontology.getOntClass(generalTDBService.getClassURIInOntology(dataset, ontologyName, targetField)));
            for (var formValue : formValues.keySet()) {
                if (formValue.equals("instanceName") || formValue.equals("ontologyName") || formValue.equals("targetClass"))
                    continue;
                var propUri = generalTDBService.getPropertyURIInOntology(dataset, ontologyName, formValue);
                var prop = ontology.getProperty(propUri);
                if (generalTDBService.checkIfObjectProperty(dataset, ontologyName, prop.getURI())) {
                    var objectValue = formValues.getFirst(formValue);
                    var objectIndividual = individualService.findIndividualInOntology(dataset, ontologyName, objectValue);
                    individual.addProperty(prop, objectIndividual);
                } else {
                    var dataValue = formValues.getFirst(formValue);
                    var dtype = ontology.getDataProperty(propUri).ranges().findFirst().get().getLocalName();
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
                        default:
                            individual.addLiteral(prop, dataValue);
                            break;
                    }
                }
            }
            dataset.commit();
        }  catch (Exception e) {
            dataset.abort();
        } finally {
            dataset.end();
        }
    }
}
