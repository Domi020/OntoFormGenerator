package fau.fdm.OntoFormGenerator.service;


import com.clarkparsia.owlapi.explanation.BlackBoxExplanation;
import com.clarkparsia.owlapi.explanation.HSTExplanationGenerator;
import fau.fdm.OntoFormGenerator.data.*;
import fau.fdm.OntoFormGenerator.data.Individual;
import fau.fdm.OntoFormGenerator.tdb.GeneralTDBService;
import fau.fdm.OntoFormGenerator.tdb.IndividualService;
import fau.fdm.OntoFormGenerator.tdb.PropertyService;
import org.apache.jena.ontapi.OntModelFactory;
import org.apache.jena.ontapi.OntSpecification;
import org.apache.jena.ontapi.model.OntIndividual;
import org.apache.jena.ontology.*;
import org.apache.jena.query.*;
import org.apache.jena.rdf.model.InfModel;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.reasoner.Reasoner;
import org.apache.jena.reasoner.ReasonerRegistry;
import org.apache.jena.tdb2.TDB2Factory;
import org.apache.jena.vocabulary.XSD;
import org.semanticweb.HermiT.Configuration;
import org.semanticweb.HermiT.ReasonerFactory;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.manchestersyntax.renderer.ManchesterOWLSyntaxOWLObjectRendererImpl;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.*;

@Service
public class OntologyContentService {

    private final Logger logger;
    private final PropertyService propertyService;

    @Value("${ontoformgenerator.ontologyDirectory}")
    private String ontologyDirectory;

    @Value("${ontoformgenerator.ontologies.forms}")
    private String formsIRI;

    private final IndividualService individualService;

    private final GeneralTDBService generalTDBService;

    @Autowired
    public OntologyContentService(IndividualService individualService, GeneralTDBService generalTDBService, PropertyService propertyService) {
        this.generalTDBService = generalTDBService;
        this.logger = LoggerFactory.getLogger(OntologyOverviewService.class);
        this.individualService = individualService;
        this.propertyService = propertyService;
    }

    public List<OntologyClass> getAllClassesOfOntology(String ontologyName) {
        List<OntologyClass> classes = new ArrayList<>();
        Dataset dataset = TDB2Factory.connectDataset(ontologyDirectory);
        dataset.begin(ReadWrite.READ);
        try {
            ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM,
                    dataset.getNamedModel(ontologyName)).listClasses().forEach(
                    ontClass -> classes.add(new OntologyClass(ontClass.getLocalName(), ontClass.getURI()))
            );
            return classes;
        } finally {
            dataset.end();
        }
    }

    public List<OntologyProperty> getAllPropertiesOfDomain(String ontologyName, String className) {
        List<OntologyProperty> properties = new ArrayList<>();
        Dataset dataset = TDB2Factory.connectDataset(ontologyDirectory);
        dataset.begin(ReadWrite.READ);
        try {
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
            return properties;
        } finally {
            dataset.end();
        }

    }

    public List<Individual> getAllIndividualsOfOntology(String ontologyName) {
        List<Individual> individuals = new ArrayList<>();
        Dataset dataset = TDB2Factory.connectDataset(ontologyDirectory);
        dataset.begin(ReadWrite.READ);
        try {
            OntModel ontModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM,
                    dataset.getNamedModel(ontologyName));
            ontModel.listIndividuals().forEach(
                    individual -> {
                        var individualName = individual.getLocalName();
                        var individualIri = individual.getURI();
                        var ontClass = individual.getOntClass();
                        var ontologyClass = new OntologyClass(ontClass.getLocalName(), ontClass.getURI());
                        individuals.add(new Individual(individualName, individualIri, ontologyClass,
                                individualService.checkIfIndividualIsImported(dataset, ontologyName, individualIri)));
                    }
            );
            return individuals;
        } finally {
            dataset.end();
        }
    }

    public List<Individual> getAllIndividualsOfClass(String ontologyName, String className) {
        List<Individual> individuals = new ArrayList<>();
        Dataset dataset = TDB2Factory.connectDataset(ontologyDirectory);
        dataset.begin(ReadWrite.READ);
        try {
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
                        individual.setImported(individualService.checkIfIndividualIsImported(dataset, ontologyName, stmt.getSubject().getURI()));
                        individuals.add(individual);
                    }
            );
            return individuals;
        } finally {
            dataset.end();
        }
    }

    public Individual getIndividualByString(String individualName, String ontologyName) {
        Dataset dataset = TDB2Factory.connectDataset(ontologyDirectory);
        try {
            dataset.begin(ReadWrite.READ);
            var individual = individualService.findIndividualInOntology(dataset, ontologyName, individualName);
            return new Individual(individual.getLocalName(), individual.getURI(),
                    new OntologyClass(individual.getOntClass().getLocalName(), individual.getOntClass().getURI()),
                    individualService.checkIfIndividualIsImported(dataset, ontologyName, individual.getURI()));
        } finally {
            dataset.end();
        }
    }

    public List<SetProperty> getAllSetPropertiesByIndividual(String individualName, String ontologyName) {
        Dataset dataset = TDB2Factory.connectDataset(ontologyDirectory);
        try {
            dataset.begin(ReadWrite.READ);
            return getSetProperties(dataset, individualName, ontologyName);
        } finally {
            dataset.end();
        }
    }

    private List<SetProperty> getSetProperties(Dataset dataset, String individualName, String ontologyName) {
        List<SetProperty> setProperties = new ArrayList<>();
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
                            ontClass, individualService.checkIfIndividualIsImported(dataset, ontologyName, individual.getURI())));
                    if (isObjectProperty) {
                        setProperty.setValue(stmt.getObject().asResource().getLocalName());
                    } else {
                        setProperty.setValue(stmt.getObject().toString());
                    }
                    setProperties.add(setProperty);
                }
        );
        return setProperties;
    }

    public Boolean addEmptyIndividual(String ontologyName, String className, String individualName) {
        Dataset dataset = TDB2Factory.connectDataset(ontologyDirectory);
        dataset.begin(ReadWrite.WRITE);
        try {
            var ontModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM,
                    dataset.getNamedModel(ontologyName));
            var ontClass = ontModel.getOntClass(individualService.findIriOfClass(dataset, ontologyName, className));
            var ontologyURI = ontClass.getURI().substring(0, ontClass.getURI().lastIndexOf("#") + 1);
            ontModel.createIndividual(ontologyURI + individualName, ontClass);
            dataset.commit();
            return true;
        } catch (Exception e) {
            dataset.abort();
            return false;
        } finally {
            dataset.end();
        }
    }

    public String editIndividual(Dataset dataset,
                                 String ontologyName,
                                 String individualName,
                                 Map<String, String[]> form) {
        //TODO: Delete all old props first; create new
        var ontology = OntModelFactory.createModel(dataset.getNamedModel(ontologyName).getGraph(),
                OntSpecification.OWL2_DL_MEM);

        var individual = individualService.findIndividualInOntology(dataset, ontologyName, individualName);
        var indivUri = individual.getURI();

        var setProperties = getSetProperties(dataset, individualName, ontologyName);

        // propertyService.removeAllPropertyValuesFromIndividual(individual);

        for (int i = 0; i < form.getOrDefault("propertyName", new String[0]).length; i++) {
            // Check if property already exists
            boolean foundElement = false;
            var propertyName = form.get("propertyName")[i];
            var propertyValue = form.get("fieldValue")[i];

            for (int j = 0; j < setProperties.size(); j++) {
                if (setProperties.get(j).getProperty().getName().equals(propertyName)) {
                    if (propertyValue.equals(setProperties.get(j).getValue())) {
                        foundElement = true;
                    } else {
                        propertyService.removePropertyValueFromIndividual(dataset, ontologyName,
                                individual, form.get("propertyName")[i]);
                    }
                    setProperties.remove(j);
                    break;
                }
            }

            if (foundElement) continue;

            OntIndividual field;
            var prop = propertyService.getPropertyFromOntology(dataset, ontologyName, propertyName);

            if (form.get("isObjectProperty")[i].equals("true")) {
                // object property
                var objectIndividual = individualService.findIndividualInOntology(dataset, ontologyName, propertyValue);
                individual.addProperty(prop, objectIndividual);
            } else {
                // datatype property
                var dtype = ontology.getDataProperty(prop.getURI()).ranges().findFirst().get().getLocalName();
                switch (dtype) {
                    case "int":
                        individual.addLiteral(prop, Integer.parseInt(propertyValue));
                        break;
                    case "float":
                        individual.addLiteral(prop, Float.parseFloat(propertyValue));
                        break;
                    case "double":
                        individual.addLiteral(prop, Double.parseDouble(propertyValue));
                        break;
                    case "boolean":
                        individual.addLiteral(prop, Boolean.parseBoolean(propertyValue));
                        break;
                    default:
                        individual.addLiteral(prop, propertyValue);
                        break;
                }
            }
        }


        // delete all old form elements that are not in the new form
        for (var alreadyInsertedElement : setProperties) {
            if (alreadyInsertedElement == null) continue;
            boolean found = false;
            for (int i = 0; i < form.getOrDefault("propertyName", new String[0]).length; i++) {
                if (alreadyInsertedElement.getProperty().getName().equals(form.get("propertyName")[i])) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                propertyService.removePropertyValueFromIndividual(dataset, ontologyName,
                        individual, alreadyInsertedElement.getProperty().getName(),
                        alreadyInsertedElement.getValue());
            }
        }
        return indivUri;
    }

    public SubclassGraph buildSubclassGraph(String ontologyName) {
        Dataset dataset = TDB2Factory.connectDataset(ontologyDirectory);
        dataset.begin(ReadWrite.READ);
        SubclassGraph subclassGraph = new SubclassGraph();
        try {
            var ontModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM,
                    dataset.getNamedModel(ontologyName));


            var subClassQuery =
                    "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#> " +
                            "SELECT ?class ?subclass WHERE { " +
                            "  ?subclass rdfs:subClassOf ?class . " +
                            "}";
            Query query = QueryFactory.create(subClassQuery);
            try (QueryExecution qexec = QueryExecutionFactory.create(query, ontModel)) {
                ResultSet results = qexec.execSelect();

                // Build the graph structure
                while (results.hasNext()) {
                    QuerySolution soln = results.nextSolution();
                    if (soln.getResource("class").getLocalName() == null) {
                        subclassGraph.addClass(new OntologyClass(soln.getResource("subclass").getLocalName(), soln.getResource("subclass").getURI()));
                    } else if (soln.getResource("subclass").getLocalName() == null) {
                        subclassGraph.addClass(new OntologyClass(soln.getResource("class").getLocalName(), soln.getResource("class").getURI()));
                    } else {
                        SubclassRelation rel = new SubclassRelation(
                                new OntologyClass(soln.getResource("class").getLocalName(), soln.getResource("class").getURI()),
                                new OntologyClass(soln.getResource("subclass").getLocalName(), soln.getResource("subclass").getURI())
                        );
                        subclassGraph.addEdge(rel);
                    }
                }
            }
            subclassGraph.addEdgesToOwlThing();
            return subclassGraph;
        } finally {
            dataset.end();
        }
    }

    public OntologyClass addNewClass(String ontologyName, String className,
                                     String superClassName) {
        Dataset dataset = TDB2Factory.connectDataset(ontologyDirectory);
        dataset.begin(ReadWrite.WRITE);
        try {
            var ontModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM,
                    dataset.getNamedModel(ontologyName));
            var uri = generalTDBService.getOntologyURIByOntologyName(dataset, ontologyName)
                    + "#" + className;
            var newClass = ontModel.createClass(uri);
            if (superClassName != null) {
                var superClassURI = generalTDBService.getClassURIInOntology(dataset, ontologyName, superClassName);
                newClass.addSuperClass(ontModel.getOntClass(superClassURI));
            }
            dataset.commit();
            return new OntologyClass(className, uri);
        } catch (Exception e) {
            dataset.abort();
            throw e;
        } finally {
            dataset.end();
        }
    }

    public ValidationResult validateOntology(Dataset dataset, String ontologyName)
    throws OWLOntologyCreationException {
        var tdbModel = dataset.getNamedModel(ontologyName);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        tdbModel.write(outputStream, "RDF/XML");
        ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());
        OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
        OWLDataFactory dataFactory = manager.getOWLDataFactory();
        var owlApiOntology = manager.loadOntologyFromOntologyDocument(inputStream);
        ReasonerFactory reasonerFactory = new ReasonerFactory();
        Configuration config = new Configuration();
        config.throwInconsistentOntologyException = false;
        var reasoner = reasonerFactory.createReasoner(owlApiOntology, config);
        if (reasoner.isConsistent()) {
            return new ValidationResult(true, "");
        }
        reasonerFactory = new org.semanticweb.HermiT.Reasoner.ReasonerFactory() {
            @Override
            public OWLReasoner createHermiTOWLReasoner(org.semanticweb.HermiT.Configuration configuration, OWLOntology o) {
                configuration.throwInconsistentOntologyException = false;
                return new org.semanticweb.HermiT.Reasoner(config, o);
            }
        };
        BlackBoxExplanation x = new BlackBoxExplanation(owlApiOntology, reasonerFactory, reasoner);
        HSTExplanationGenerator explanationGenerator = new HSTExplanationGenerator(x);
        StringBuilder explaination = new StringBuilder("Knowledge base is inconsistent.\n");
        var expl = explanationGenerator.getExplanation(dataFactory.getOWLThing());
        var renderer = new ManchesterOWLSyntaxOWLObjectRendererImpl();
        explaination.append("Axioms causing the inconsistency:\n\n\n");
        for (OWLAxiom causingAxiom : expl) {
            explaination.append(renderer.render(causingAxiom)).append("\n\n");
        }
        return new ValidationResult(false, explaination.toString());
    }

    public ValidationResult validateOntology(String ontologyName) {
        Dataset dataset = TDB2Factory.connectDataset(ontologyDirectory);
        dataset.begin(ReadWrite.READ);
        try {
            return validateOntology(dataset, ontologyName);
        } catch (OWLOntologyCreationException e) {
            throw new RuntimeException(e);
        } finally {
            dataset.end();
        }
    }


    public void deleteIndividual(String ontologyName, String individualUri) {
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

    public OntologyProperty createNewProperty(String ontologyName, String propertyName,
                                              boolean objectProperty, String domain, String range) {
        Dataset dataset = TDB2Factory.connectDataset(ontologyDirectory);
        dataset.begin(ReadWrite.WRITE);
        try {
            var ontModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM,
                    dataset.getNamedModel(ontologyName));
            var domainClass = ontModel.getOntClass(individualService.findIriOfClass(dataset, ontologyName, domain));
            OntResource fullRange;
            if (objectProperty) {
                var property = ontModel.createObjectProperty(generalTDBService.getOntologyURIByOntologyName(dataset, ontologyName) + "#" + propertyName);
                property.addDomain(domainClass);
                fullRange = ontModel.getOntClass(individualService.findIriOfClass(dataset, ontologyName, range));
                property.addRange(fullRange);
            } else {
                var property = ontModel.createDatatypeProperty(generalTDBService.getOntologyURIByOntologyName(dataset, ontologyName) + "#" + propertyName);
                property.addDomain(domainClass);
                fullRange = getResourceForDatatype(ontModel, range);
                property.addRange(fullRange);
            }
            dataset.commit();
            return new OntologyProperty(propertyName, new OntologyClass(domain, domainClass.getURI()),
                    objectProperty, objectProperty ? new OntologyClass(fullRange.getLocalName(), fullRange.getURI()) : null,
                    objectProperty ? null : range);
        } catch (Exception e) {
            dataset.abort();
            throw e;
        } finally {
            dataset.end();
        }
    }

    private OntResource getResourceForDatatype(OntModel model, String datatype) {
        return switch (datatype) {
            case "int" -> model.getOntResource(XSD.xint.getURI());
            case "float" -> model.getOntResource(XSD.xfloat.getURI());
            case "double" -> model.getOntResource(XSD.xdouble.getURI());
            case "boolean" -> model.getOntResource(XSD.xboolean.getURI());
            case "datetime" -> model.getOntResource(XSD.dateTime.getURI());
            default -> model.getOntResource(XSD.xstring.getURI());
        };
    }

    public List<OntologyClass> getTargetClasses(String ontologyName) {
        Dataset dataset = TDB2Factory.connectDataset(ontologyDirectory);
        dataset.begin(ReadWrite.READ);
        try {
            var ontology = individualService.findOntIndividualInOntology(dataset, "forms", ontologyName);
            var targetClasses = propertyService.getMultipleObjectPropertyValuesFromIndividual(dataset, "forms",
                    ontology, "hasTargetClass");
            List<OntologyClass> classes = new ArrayList<>();
            targetClasses.forEach(
                    targetClass -> classes.add(new OntologyClass(targetClass.getLocalName(), targetClass.getURI()))
            );
            return classes;
        } finally {
            dataset.end();
        }
    }

    public List<OntologyProperty> queryProperties(String ontologyName, String className, String query) {
        Dataset dataset = TDB2Factory.connectDataset(ontologyDirectory);
        dataset.begin(ReadWrite.READ);
        try {
            // 1. Search for name (case invariant; contains)
            // 2. Search for label (case invariant; contains)
            // 3. Search for description (case invariant; contains)
            var properties = propertyService.searchProperties(dataset, ontologyName, className, query);
            List<OntologyProperty> ontologyProperties = new ArrayList<>();
            properties.forEach(
                    property -> {
                        var ontologyProperty = new OntologyProperty();
                        ontologyProperty.setName(property.getLocalName());
                        if (property.getDomain() != null) {
                            ontologyProperty.setDomain(new OntologyClass(property.getDomain().getLocalName(), property.getDomain().getURI()));
                        }
                        ontologyProperty.setObjectProperty(property.isObjectProperty());
                        if (property.isObjectProperty()) {
                            if (property.getRange() != null)
                                ontologyProperty.setObjectRange(new OntologyClass(property.getRange().getLocalName(), property.getRange().getURI()));
                        } else {
                            ontologyProperty.setDatatypeRange(property.getRange().getLocalName());
                        }
                        ontologyProperty.setRdfsLabel(property.getLabel(null));
                        ontologyProperty.setRdfsComment(property.getComment(null));
                        ontologyProperties.add(ontologyProperty);
                    }
            );
            return ontologyProperties;
        } finally {
            dataset.end();
        }
    }
}
