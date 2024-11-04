package fau.fdm.OntoFormGenerator.service;


import fau.fdm.OntoFormGenerator.data.*;
import fau.fdm.OntoFormGenerator.data.Individual;
import fau.fdm.OntoFormGenerator.exception.NamingFilterViolatedException;
import fau.fdm.OntoFormGenerator.exception.NamingSchemaDifferentException;
import fau.fdm.OntoFormGenerator.exception.OntologyValidationException;
import fau.fdm.OntoFormGenerator.exception.SimilarPropertiesExistException;
import fau.fdm.OntoFormGenerator.tdb.GeneralTDBService;
import fau.fdm.OntoFormGenerator.tdb.IndividualService;
import fau.fdm.OntoFormGenerator.tdb.PropertyService;
import fau.fdm.OntoFormGenerator.tdb.TDBConnection;
import org.apache.jena.ontapi.OntModelFactory;
import org.apache.jena.ontapi.OntSpecification;
import org.apache.jena.ontapi.model.OntIndividual;
import org.apache.jena.ontology.*;
import org.apache.jena.query.*;
import org.apache.jena.rdf.model.InfModel;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.reasoner.Reasoner;
import org.apache.jena.reasoner.ReasonerRegistry;
import org.apache.jena.vocabulary.XSD;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class OntologyContentService {

    private final Logger logger;
    private final PropertyService propertyService;
    public final OntologyValidationService ontologyValidationService;

    private final IndividualService individualService;

    private final GeneralTDBService generalTDBService;

    @Autowired
    public OntologyContentService(IndividualService individualService, GeneralTDBService generalTDBService, PropertyService propertyService, OntologyValidationService ontologyValidationService) {
        this.generalTDBService = generalTDBService;
        this.logger = LoggerFactory.getLogger(OntologyOverviewService.class);
        this.individualService = individualService;
        this.propertyService = propertyService;
        this.ontologyValidationService = ontologyValidationService;
    }

    public List<OntologyClass> getAllClassesOfOntology(String ontologyName) {
        List<OntologyClass> classes = new ArrayList<>();
        try (TDBConnection connection = new TDBConnection(ReadWrite.READ, ontologyName)) {
            connection.getModel().listClasses().forEach(
                    ontClass -> classes.add(new OntologyClass(ontClass.getLocalName(), ontClass.getURI()))
            );
            return classes;
        }
    }

    public List<OntologyProperty> getAllPropertiesOfDomain(String ontologyName, String classURI) {
        List<OntologyProperty> properties = new ArrayList<>();
        try (TDBConnection connection = new TDBConnection(ReadWrite.READ, ontologyName)) {
            OntClass ontClass = connection.getModel().getOntClass(classURI);
            if (ontClass == null) {
                return properties;
            }
            ontClass.listDeclaredProperties(false).forEachRemaining(
                    property -> {
                        if (property.isAnnotationProperty()) return;
                        OntologyProperty ontologyProperty = new OntologyProperty();
                        ontologyProperty.setName(property.getLocalName());
                        ontologyProperty.setRdfsComment(property.getComment(null));
                        ontologyProperty.setDomain(new OntologyClass(ontClass.getLocalName(), classURI));
                        ontologyProperty.setUri(property.getURI());
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
        }
    }

    public List<Individual> getAllIndividualsOfOntology(String ontologyName) {
        List<Individual> individuals = new ArrayList<>();
        try (TDBConnection connection = new TDBConnection(ReadWrite.READ, ontologyName)) {
            OntModel ontModel = connection.getModel();
            Dataset dataset = connection.getDataset();
            ontModel.listIndividuals().forEach(
                    individual -> {
                        var individualName = individual.getLocalName();
                        var individualIri = individual.getURI();
                        var ontClass = individual.getOntClass();
                        var ontologyClass = new OntologyClass(ontClass.getLocalName(), ontClass.getURI());
                        individuals.add(new Individual(individualName, propertyService.getLabelOfIndividual(dataset, ontologyName, individualIri),
                                individualIri, ontologyClass,
                                individualService.checkIfIndividualIsImported(dataset, ontologyName, individualIri)));
                    }
            );
            return individuals;
        }
    }

    //TODO: prüfen: bekommt man so alle Individuals?? => v.a. Superclass?
    public List<Individual> getAllIndividualsOfClass(String ontologyName, String classIri) {
        List<Individual> individuals = new ArrayList<>();
        try (TDBConnection connection = new TDBConnection(ReadWrite.READ, ontologyName)) {
            Reasoner reasoner = ReasonerRegistry.getOWLMicroReasoner();
            reasoner.bindSchema(connection.getModel());
            InfModel infModel = ModelFactory.createInfModel(reasoner, connection.getModel());
            Resource classRes = infModel.getResource(classIri);
            var typeProp = infModel.getProperty("http://www.w3.org/1999/02/22-rdf-syntax-ns#type");

            infModel.listStatements(null, typeProp, classRes).forEach(
                    stmt -> {
                        var individual = new Individual();
                        individual.setName(stmt.getSubject().getLocalName());
                        individual.setLabel(propertyService.getLabelOfIndividual(connection.getDataset(), ontologyName, stmt.getSubject().getURI()));
                        individual.setIri(stmt.getSubject().getURI());
                        individual.setOntologyClass(new OntologyClass(generalTDBService.getClassNameInOntology(
                                connection.getDataset(), ontologyName, classIri
                        ), classIri));
                        individual.setImported(individualService.checkIfIndividualIsImported(connection.getDataset(), ontologyName, stmt.getSubject().getURI()));
                        individuals.add(individual);
                    }
            );
            return individuals;
        }
    }

    public Individual getIndividualByString(String individualName, String ontologyName) {
        try (TDBConnection connection = new TDBConnection(ReadWrite.READ, ontologyName)) {
            var individual = individualService.findIndividualInOntology(connection.getDataset(), ontologyName, individualName);
            return new Individual(individual.getLocalName(), propertyService.getLabelOfIndividual(connection.getDataset(), ontologyName, individual.getURI()), individual.getURI(),
                    new OntologyClass(individual.getOntClass().getLocalName(), individual.getOntClass().getURI()),
                    individualService.checkIfIndividualIsImported(connection.getDataset(), ontologyName, individual.getURI()));
        }
    }

    public List<SetProperty> getAllSetPropertiesByIndividual(String individualURI, String ontologyName) {
        try (TDBConnection connection = new TDBConnection(ReadWrite.READ, ontologyName)) {
            return getSetProperties(connection.getDataset(), individualURI, ontologyName);
        }
    }

    private List<SetProperty> getSetProperties(Dataset dataset, String individualUri, String ontologyName) {
        // TODO: Testen
        List<SetProperty> setProperties = new ArrayList<>();
        var individual = individualService.getOntIndividualByIri(dataset, ontologyName, individualUri);
        individual.listProperties().forEachRemaining(
                stmt -> {
                    if (stmt.getPredicate().getLocalName().equals("type"))
                        return;
                    if (generalTDBService.checkIfAnnotationProperty(dataset, ontologyName, stmt.getPredicate().getURI()))
                        return;
                    var setProperty = new SetProperty();
                    var ontClass = new OntologyClass(individual.ontClass().get().getLocalName(),
                            individual.ontClass().get().getURI());
                    var isObjectProperty = generalTDBService.checkIfObjectProperty(dataset, ontologyName,
                            stmt.getPredicate().getURI());
                    setProperty.setProperty(new OntologyProperty(
                            stmt.getPredicate().getLocalName(),
                            ontClass,
                            null,
                            isObjectProperty,
                            isObjectProperty ? new OntologyClass(stmt.getObject().asResource().getLocalName(),
                                    stmt.getObject().asResource().getURI()) : null,
                            isObjectProperty ? null : stmt.getObject().asLiteral().getDatatype().getJavaClass().getSimpleName()
                    ));
                    setProperty.setIndividual(new Individual(individual.getLocalName(),
                            propertyService.getLabelOfIndividual(dataset, ontologyName, individual.getURI()),
                            individual.getURI(), ontClass, individualService.checkIfIndividualIsImported(dataset, ontologyName, individual.getURI())));
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

    public Boolean addEmptyIndividual(String ontologyName, String classUri, String individualName) {
        try (TDBConnection connection = new TDBConnection(ReadWrite.WRITE, ontologyName)) {
            var ontClass = connection.getModel().getOntClass(classUri);
            var ontologyURI = ontClass.getURI().substring(0, ontClass.getURI().lastIndexOf("#") + 1);
            connection.getModel().createIndividual(ontologyURI + individualName, ontClass);
            connection.commit();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public String editIndividual(Dataset dataset,
                                 String ontologyName,
                                 String individualUri,
                                 Map<String, String[]> form) {
        //TODO: Delete all old props first; create new
        var ontology = OntModelFactory.createModel(dataset.getNamedModel(ontologyName).getGraph(),
                OntSpecification.OWL2_DL_MEM);

        var individual = individualService.getIndividualByIri(dataset, ontologyName, individualUri);

        var setProperties = getSetProperties(dataset, individualUri, ontologyName);

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
        return individualUri;
    }

    public SubclassGraph buildSubclassGraph(String ontologyName) {
        SubclassGraph subclassGraph = new SubclassGraph();
        try (TDBConnection connection = new TDBConnection(ReadWrite.READ, ontologyName)) {
            var ontModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM,
                    connection.getDataset().getNamedModel(ontologyName));


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
        }
    }

    public OntologyClass addNewClass(String ontologyName, String className,
                                     String superClassUri) {
        try (TDBConnection connection = new TDBConnection(ReadWrite.WRITE, ontologyName)) {
            var uri = generalTDBService.getOntologyURIByOntologyName(connection.getDataset(), ontologyName)
                    + "#" + className;
            var newClass = connection.getModel().createClass(uri);
            if (superClassUri != null) {
                //TODO: TESTEN
                newClass.addSuperClass(connection.getModel().getOntClass(superClassUri));
            }
            connection.commit();
            return new OntologyClass(className, uri);
        }
    }

    public void deleteIndividual(String ontologyName, String individualUri) {
        try (TDBConnection connection = new TDBConnection(ReadWrite.WRITE, ontologyName)) {
            individualService.deleteIndividualByIri(connection.getDataset(), ontologyName, individualUri);
            individualService.deleteIndividualByIri(connection.getDataset(), "forms", individualUri);
            connection.commit();
        }
    }

    private final String IS_USER_DEFINED = "http://www.semanticweb.org/fau/ontologies/2024/ontoformgenerator/forms" +
            "#isUserDefined";
    private final String RDFS_COMMENT = "http://www.w3.org/2000/01/rdf-schema#comment";

    public OntologyProperty createNewProperty(String ontologyName, String propDescription, String propertyName,
                                              boolean objectProperty, String domain, String range,
                                              boolean validate) throws OntologyValidationException {
        try (TDBConnection connection = new TDBConnection(ReadWrite.WRITE, ontologyName)) {
            if (validate) {
                var synonyms = ontologyValidationService.findPotentialSimilarProperties(connection.getDataset(), ontologyName, domain, propertyName);
                if (!synonyms.isEmpty()) {
                    throw new SimilarPropertiesExistException(propertyName,
                            synonyms.stream().map(OntologyProperty::getName).toArray(String[]::new));
                }
                var namingValidationResult = ontologyValidationService.checkNamingSchema(connection.getDataset(), ontologyName, propertyName);
                if (!namingValidationResult.isValid()) {
                    throw new NamingSchemaDifferentException(propertyName, ontologyName,
                            namingValidationResult.getNewPropertyNamingSchema().toString(),
                            namingValidationResult.getOntologyNamingSchema().toString());
                }
                var namingFilterResult = ontologyValidationService.checkNaming(propertyName);
                if (!namingFilterResult.isValid()) {
                    throw new NamingFilterViolatedException(propertyName, namingFilterResult.getFilteredWord());
                }
            }
            var ontModel = connection.getModel();
            var domainClass = ontModel.getOntClass(individualService.findIriOfClass(connection.getDataset(), ontologyName, domain));
            OntResource fullRange;
            var isUsedDefinedProp = ontModel.getProperty(IS_USER_DEFINED);
            var rdfsComment = ontModel.getProperty(RDFS_COMMENT);
            Property property;
            String uri = generalTDBService.getOntologyURIByOntologyName(connection.getDataset(), ontologyName) + "#" + propertyName;
            if (objectProperty) {
                property = ontModel.createObjectProperty(uri);
                var prop = (ObjectProperty) property;
                prop.addDomain(domainClass);
                fullRange = ontModel.getOntClass(individualService.findIriOfClass(connection.getDataset(), ontologyName, range));
                prop.addRange(fullRange);
            } else {
                property = ontModel.createDatatypeProperty(uri);
                var prop = (DatatypeProperty) property;
                prop.addDomain(domainClass);
                fullRange = getResourceForDatatype(ontModel, range);
                prop.addRange(fullRange);
            }
            property.addProperty(isUsedDefinedProp, ontModel.createTypedLiteral(true));
            if (propDescription != null && !propDescription.isEmpty()) {
                property.addProperty(rdfsComment, ontModel.createTypedLiteral(propDescription));
            }
            connection.commit();
            return new OntologyProperty(propertyName, new OntologyClass(domain, domainClass.getURI()), uri,
                    objectProperty, objectProperty ? new OntologyClass(fullRange.getLocalName(), fullRange.getURI()) : null,
                    objectProperty ? null : range);
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
        try (TDBConnection connection = new TDBConnection(ReadWrite.READ, ontologyName)) {
            var ontology = individualService.findOntIndividualInOntology(connection.getDataset(), "forms", ontologyName);
            var targetClasses = propertyService.getMultipleObjectPropertyValuesFromIndividual(connection.getDataset(), "forms",
                    ontology, "hasTargetClass");
            List<OntologyClass> classes = new ArrayList<>();
            targetClasses.forEach(
                    targetClass -> classes.add(new OntologyClass(targetClass.getLocalName(), targetClass.getURI()))
            );
            return classes;
        }
    }

    public List<OntologyProperty> queryProperties(String ontologyName, String classIri, String query) {
        try (TDBConnection connection = new TDBConnection(ReadWrite.READ, ontologyName)) {
            // 1. Search for name (case invariant; contains)
            // 2. Search for label (case invariant; contains)
            // 3. Search for description (case invariant; contains)
            var properties = propertyService.searchProperties(connection.getDataset(), ontologyName, classIri, query);
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
        }
    }
}
