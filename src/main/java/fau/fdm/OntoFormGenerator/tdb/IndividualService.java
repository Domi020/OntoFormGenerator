package fau.fdm.OntoFormGenerator.tdb;

import fau.fdm.OntoFormGenerator.service.OntologyOverviewService;
import org.apache.jena.ontapi.OntModelFactory;
import org.apache.jena.ontapi.OntSpecification;
import org.apache.jena.ontapi.model.OntIndividual;
import org.apache.jena.ontology.*;
import org.apache.jena.query.*;
import org.apache.jena.rdf.model.*;
import org.apache.jena.reasoner.Reasoner;
import org.apache.jena.reasoner.ReasonerRegistry;
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

    @Value("${ontoformgenerator.ontologies.baseIRI}")
    private String baseIRI;

    private final Logger logger = LoggerFactory.getLogger(OntologyOverviewService.class);

    private org.apache.jena.ontapi.model.OntModel getOntModel(Model model) {
        return OntModelFactory.createModel(model.getGraph(), OntSpecification.OWL2_DL_MEM);
    }

    public OntIndividual addIndividual(Dataset dataset,
                                    String ontologyName,
                                    String className,
                                    String individualName) {
        var model = dataset.getNamedModel(ontologyName);
        var ontModel = getOntModel(model);
        var ontClass = ontModel.getOntClass(baseIRI + "/" + ontologyName + "#" + className);
        return ontModel.createIndividual(baseIRI + "/" + ontologyName + "#" + individualName, ontClass);
    }

    public OntIndividual addIndividual(Dataset dataset,
                                    String className,
                                    String individualName) {
        return addIndividual(dataset, "forms", className, individualName);
    }

    public OntIndividual addIndividualWithURI(Dataset dataset,
                                              String className,
                                              String URI) {
        var model = dataset.getNamedModel("forms");
        var ontModel = getOntModel(model);
        var ontClass = ontModel.getOntClass(baseIRI + "/forms#" + className);
        return ontModel.createIndividual(URI, ontClass);
    }

    public void deleteIndividual(Dataset dataset,
                                 String ontologyName,
                                 String individualName) {
        OntModel ontModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM,
                dataset.getNamedModel(ontologyName));
        ontModel.getIndividual(baseIRI + "/" + ontologyName + "#" + individualName).remove();
    }

    public void deleteIndividualByIri(Dataset dataset,
                                      String ontologyName,
                                      String iri) {
        OntModel ontModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM,
                dataset.getNamedModel(ontologyName));
        ontModel.getIndividual(iri).remove();
    }

    public List<OntIndividual> getAllIndividualsOfClass(Dataset dataset,
                                                     String ontologyName,
                                                     String className) {
        List<OntIndividual> individuals = new ArrayList<>();
        var ontModel = getOntModel(dataset.getNamedModel(ontologyName));
        var ontClass = ontModel.getOntClass(baseIRI + "/" + ontologyName + "#" + className);
        var iter = ontModel.namedIndividuals();
        iter.filter(individual -> individual.hasOntClass(ontClass, true))
                .forEach(individuals::add);
        return individuals;
    }

    public OntIndividual getIndividualByString(Dataset dataset,
                                            String ontologyName,
                                            String individualName) {
        var ontModel = getOntModel(dataset.getNamedModel(ontologyName));
        return ontModel.getIndividual(baseIRI + "/" + ontologyName + "#" + individualName);
    }

    public OntIndividual getOntIndividualByIri(Dataset dataset,
                                               String iri) {
        var ontModel = getOntModel(dataset.getNamedModel("forms"));
        return ontModel.getIndividual(iri);
    }

    public OntIndividual getIndividualByIri(Dataset dataset,
                                               String iri) {
        var ontModel = getOntModel(dataset.getNamedModel("forms"));
        return ontModel.getIndividual(iri);
    }

    public Individual findIndividualInOntology(Dataset dataset,
                                               String ontologyName,
                                               String individualName) {
        OntModel ontModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM,
                dataset.getNamedModel(ontologyName));
        return ontModel.listIndividuals().filterKeep(individual -> individual.getLocalName().equals(individualName)).next();
    }

    public OntIndividual findOntIndividualInOntology(Dataset dataset,
                                               String ontologyName,
                                               String individualName) {
        var ontModel = getOntModel(dataset.getNamedModel(ontologyName));
        return ontModel.individuals().filter(individual -> individual.getLocalName().equals(individualName)).findFirst().get();
    }

    public OntIndividual getOrAddIndividualByString(Dataset dataset,
                                                    String iri,
                                                    String className) {
        var individual = getOntIndividualByIri(dataset, iri);
        if (individual == null) {
            individual = addIndividualWithURI(dataset, className, iri);
        }
        return individual;
    }

    public String findIriOfClass(Dataset dataset, String className) {
        return findIriOfClass(dataset, "forms", className);
    }

    public String findIriOfClass(Dataset dataset, String ontologyName, String className) {
        // TODO: Was wenn selber ClassName über mehrere Ontologien?
        OntModel ontModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM,
                dataset.getNamedModel(ontologyName));
        var classIterator = ontModel.listClasses().filterKeep(ontClass -> ontClass.getLocalName() != null &&
                ontClass.getLocalName().equals(className));
        if (classIterator.hasNext()) {
            return classIterator.next().getURI();
        }
        return null;
    }

    public OntIndividual createDatatypeFormElement(Dataset dataset, String name, String datatype) {
        var formElement = switch (datatype) {
            case "string" -> addIndividual(dataset, "Input", name);
            case "boolean" -> addIndividual(dataset, "Select", name);
            case "date" -> addIndividual(dataset, "Date", name);
            case "dateTime" -> addIndividual(dataset, "Datetime", name);
            case "int", "integer" -> addIndividual(dataset, "Number", name);
            default -> null;
        };
        return formElement;

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
