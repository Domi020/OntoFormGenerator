package fau.fdm.OntoFormGenerator.tdb;

import fau.fdm.OntoFormGenerator.service.OntologyOverviewService;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.ontology.OntModelSpec;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.tdb2.TDB2Factory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import javax.xml.crypto.Data;
import java.io.FileInputStream;
import java.io.FileNotFoundException;

@Service
public class IndividualService {

    @Value("${ontoformgenerator.ontologyDirectory}")
    private String ontologyDirectory;

    @Value("${ontoformgenerator.ontologies.forms}")
    private String formsIRI;

    @Value("${ontoformgenerator.ontologies.baseIRI}")
    private String baseIRI;

    private final Logger logger = LoggerFactory.getLogger(OntologyOverviewService.class);

    public void addIndividual(Dataset dataset,
                              String ontologyName,
                              String className,
                              String individualName) {
        var model = dataset.getNamedModel(ontologyName);
        OntModel ontModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM, model);
        var ontClass = ontModel.getOntClass(baseIRI + "/"+ ontologyName + "#" + className);
        ontModel.createIndividual(baseIRI + "/" + ontologyName + "#" + individualName, ontClass);
    }

    public void addIndividual(Dataset dataset,
                              String className,
                              String individualName) {
        addIndividual(dataset, "forms", className, individualName);
    }

    public void deleteIndividual(Dataset dataset,
                                 String ontologyName,
                                 String individualName) {
        OntModel ontModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM,
                dataset.getNamedModel(ontologyName));
        ontModel.getIndividual(baseIRI + "/" + ontologyName + "#" + individualName).remove();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initFormOntology() {
        Dataset dataset = TDB2Factory.connectDataset(ontologyDirectory);
        dataset.begin(ReadWrite.WRITE);
        Model model = dataset.getNamedModel("forms");
        if (model.isEmpty()) {
            try {
                OntModel m = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM);
                var fis = new FileInputStream("owl/forms.rdf");
                m.read(fis, null);
                model = m;
                dataset.addNamedModel("forms", model);
            } catch (FileNotFoundException e) {
                logger.error("Error reading forms ontology file while importing new ontology", e);
                dataset.abort();
                dataset.end();
                System.exit(1);
            }
        }
        dataset.commit();
        dataset.end();
    }

}
