package fau.fdm.OntoFormGenerator.service;


import fau.fdm.OntoFormGenerator.data.*;
import fau.fdm.OntoFormGenerator.tdb.GeneralTDBService;
import fau.fdm.OntoFormGenerator.tdb.IndividualService;
import org.apache.jena.ontology.OntClass;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.ontology.OntModelSpec;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.InfModel;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.reasoner.Reasoner;
import org.apache.jena.reasoner.ReasonerRegistry;
import org.apache.jena.tdb2.TDB2Factory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class OntologyContentService {

    private final Logger logger;

    @Value("${ontoformgenerator.ontologyDirectory}")
    private String ontologyDirectory;

    @Value("${ontoformgenerator.ontologies.forms}")
    private String formsIRI;

    private final IndividualService individualService;

    private final GeneralTDBService generalTDBService;

    @Autowired
    public OntologyContentService(IndividualService individualService, GeneralTDBService generalTDBService) {
        this.generalTDBService = generalTDBService;
        this.logger = LoggerFactory.getLogger(OntologyOverviewService.class);
        this.individualService = individualService;
    }

    public List<OntologyClass> getAllClassesOfOntology(String ontologyName) {
        List<OntologyClass> classes = new ArrayList<>();
        Dataset dataset = TDB2Factory.connectDataset(ontologyDirectory);
        dataset.begin(ReadWrite.READ);
        ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM,
                dataset.getNamedModel(ontologyName)).listClasses().forEach(
                ontClass -> classes.add(new OntologyClass(ontClass.getLocalName(), ontClass.getURI()))
        );
        dataset.end();
        return classes;
    }

    public List<OntologyProperty> getAllPropertiesOfDomain(String ontologyName, String className) {
        List<OntologyProperty> properties = new ArrayList<>();
        Dataset dataset = TDB2Factory.connectDataset(ontologyDirectory);
        dataset.begin(ReadWrite.READ);
        String classURI = generalTDBService.getClassURIInOntology(dataset, ontologyName, className);
        OntClass ontClass = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM,
                        dataset.getNamedModel(ontologyName))
                .getOntClass(classURI);
        if (ontClass == null) {
            return properties;
        }
        ontClass.listDeclaredProperties(false).forEachRemaining(
                property -> {
                    OntologyProperty ontologyProperty = new OntologyProperty();
                    ontologyProperty.setName(property.getLocalName());
                    ontologyProperty.setDomain(new OntologyClass(className, classURI));
                    if (property.isObjectProperty()) {
                        ontologyProperty.setObjectProperty(true);
                        if (property.getRange() != null) {
                            ontologyProperty.setObjectRange(
                                    new OntologyClass(property.getRange().getLocalName(), property.getRange().getURI()));
                        }
                    } else {
                        ontologyProperty.setObjectProperty(false);
                        if (property.getRange() != null) {
                            ontologyProperty.setDatatypeRange(property.getRange().getLocalName());
                        }
                    }
                    properties.add(ontologyProperty);
                }
        );
        dataset.end();
        return properties;
    }

    public List<Individual> getAllIndividualsOfClass(String ontologyName, String className) {
        List<Individual> individuals = new ArrayList<>();
        Dataset dataset = TDB2Factory.connectDataset(ontologyDirectory);
        dataset.begin(ReadWrite.READ);
        var classIri = individualService.findIriOfClass(dataset, ontologyName, className);

        OntModel ontModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM,
                dataset.getNamedModel(ontologyName));
        Reasoner reasoner = ReasonerRegistry.getOWLMicroReasoner();
        reasoner.bindSchema(ontModel);
        InfModel infModel = ModelFactory.createInfModel(reasoner, ontModel);
        Resource classRes = infModel.getResource(classIri);
        var typeProp = infModel.getProperty("http://www.w3.org/1999/02/22-rdf-syntax-ns#type");

        infModel.listStatements(null, typeProp, classRes).forEach(
                stmt -> {
                    var individual = new Individual();
                    individual.setName(stmt.getSubject().getLocalName());
                    individual.setIri(stmt.getSubject().getURI());
                    individual.setOntologyClass(new OntologyClass(className, classIri));
                    individuals.add(individual);
                }
        );
        return individuals;
    }

    public Individual getIndividualByString(String individualName, String ontologyName) {
        Dataset dataset = TDB2Factory.connectDataset(ontologyDirectory);
        try {
            dataset.begin(ReadWrite.READ);
            var individual = individualService.findIndividualInOntology(dataset, ontologyName, individualName);
            return new Individual(individual.getLocalName(), individual.getURI(),
                    new OntologyClass(individual.getOntClass().getLocalName(), individual.getOntClass().getURI()));
        } finally {
            dataset.end();
        }
    }

    public List<SetProperty> getAllSetPropertiesByIndividual(String individualName, String ontologyName) {
        List<SetProperty> setProperties = new ArrayList<>();
        Dataset dataset = TDB2Factory.connectDataset(ontologyDirectory);
        dataset.begin(ReadWrite.READ);
        var individual = individualService.findIndividualInOntology(dataset, ontologyName, individualName);
        individual.listProperties().forEachRemaining(
                stmt -> {
                    if (stmt.getPredicate().getLocalName().equals("type"))
                        return;
                    var setProperty = new SetProperty();
                    var ontClass = new OntologyClass(individual.getOntClass().getLocalName(),
                            individual.getOntClass().getURI());
                    var isObjectProperty = generalTDBService.checkIfObjectProperty(dataset, ontologyName,
                            stmt.getPredicate().getURI());
                    setProperty.setProperty(new OntologyProperty(
                            stmt.getPredicate().getLocalName(),
                            ontClass,
                            isObjectProperty,
                            isObjectProperty ? new OntologyClass(stmt.getObject().asResource().getLocalName(),
                                    stmt.getObject().asResource().getURI()) : null,
                            isObjectProperty ? null : stmt.getObject().asLiteral().getDatatype().getJavaClass().getSimpleName()
                    ));
                    setProperty.setIndividual(new Individual(individual.getLocalName(), individual.getURI(),
                            ontClass));
                    setProperty.setValue(stmt.getObject().toString());
                    setProperties.add(setProperty);
                }
        );
        dataset.end();
        return setProperties;
    }
}
