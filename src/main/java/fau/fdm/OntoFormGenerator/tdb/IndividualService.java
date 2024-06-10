package fau.fdm.OntoFormGenerator.tdb;

import fau.fdm.OntoFormGenerator.service.OntologyOverviewService;
import org.apache.jena.ontology.Individual;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.ontology.OntModelSpec;
import org.apache.jena.query.*;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.tdb2.TDB2Factory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;

@Service
public class IndividualService {

    @Value("${ontoformgenerator.ontologyDirectory}")
    private String ontologyDirectory;

    @Value("${ontoformgenerator.ontologies.forms}")
    private String formsIRI;

    @Value("${ontoformgenerator.ontologies.baseIRI}")
    private String baseIRI;

    private final Logger logger = LoggerFactory.getLogger(OntologyOverviewService.class);

    public Individual addIndividual(Dataset dataset,
                                    String ontologyName,
                                    String className,
                                    String individualName) {
        var model = dataset.getNamedModel(ontologyName);
        OntModel ontModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM, model);
        var ontClass = ontModel.getOntClass(baseIRI + "/"+ ontologyName + "#" + className);
        return ontModel.createIndividual(baseIRI + "/" + ontologyName + "#" + individualName, ontClass);
    }

    public Individual addIndividual(Dataset dataset,
                              String className,
                              String individualName) {
        return addIndividual(dataset, "forms", className, individualName);
    }

    public void deleteIndividual(Dataset dataset,
                                 String ontologyName,
                                 String individualName) {
        OntModel ontModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM,
                dataset.getNamedModel(ontologyName));
        ontModel.getIndividual(baseIRI + "/" + ontologyName + "#" + individualName).remove();
    }

    public Individual addObjectPropertyToIndividual(Dataset dataset,
                                                    String ontologyName,
                                                    Individual domainIndividual,
                                                    String propertyName,
                                                    String otherIndividualName) {
        OntModel ontModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM,
                dataset.getNamedModel(ontologyName));
        var otherIndividual = ontModel.getIndividual(baseIRI + "/" + ontologyName + "#" + otherIndividualName);
        domainIndividual.addProperty(ontModel.getProperty(baseIRI + "/" + ontologyName + "#" + propertyName), otherIndividual);
        return domainIndividual;
    }

    public Resource getObjectPropertyFromIndividual(Dataset dataset,
                                                    String ontologyName,
                                                    Individual domainIndividual,
                                                    String propertyName) {
        OntModel ontModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM,
                dataset.getNamedModel(ontologyName));
        return domainIndividual.getPropertyValue(ontModel.getProperty(baseIRI + "/" + ontologyName + "#" + propertyName))
                .asResource();
    }

    public List<Individual> getAllIndividualsOfClass(Dataset dataset,
                                                     String ontologyName,
                                                     String className) {
        List<Individual> individuals = new ArrayList<>();
        OntModel ontModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM,
                dataset.getNamedModel(ontologyName));
        var ontClass = ontModel.getOntClass(baseIRI + "/" + ontologyName + "#" + className);
        var iter = ontModel.listIndividuals(ontClass);
        iter.forEachRemaining(individuals::add);
        return individuals;
    }

    public Individual deleteObjectPropertyFromIndividual(Dataset dataset,
                                                         String ontologyName,
                                                         Individual domainIndividual,
                                                         String propertyName) {
        OntModel ontModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM,
                dataset.getNamedModel(ontologyName));
        domainIndividual.removeAll(ontModel.getProperty(baseIRI + "/" + ontologyName + "#" + propertyName));
        return domainIndividual;
    }

    public Individual deleteObjectPropertyFromIndividual(Dataset dataset,
                                                         String ontologyName,
                                                         String individualName,
                                                         String propertyName) {
        var individual = getIndividualByString(dataset, ontologyName, individualName);
        return deleteObjectPropertyFromIndividual(dataset, ontologyName, individual, propertyName);
    }

    public Individual getIndividualByString(Dataset dataset,
                                            String ontologyName,
                                            String individualName) {
        OntModel ontModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM,
                dataset.getNamedModel(ontologyName));
        return ontModel.getIndividual(baseIRI + "/" + ontologyName + "#" + individualName);
    }

    public List<Resource> selectIndividualsInSPARQLQuery(Dataset dataset,
                                                           String ontologyName,
                                                           String query) {
        List<Resource> individuals = new ArrayList<>();
        var model = dataset.getNamedModel(ontologyName);
        Query q = QueryFactory.create(query);
        try (QueryExecution exc = QueryExecutionFactory.create(q, model)) {
            ResultSet results = exc.execSelect();
            while (results.hasNext()) {
                individuals.add(results.nextSolution().getResource("f"));
            }
        }
        return individuals;
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
