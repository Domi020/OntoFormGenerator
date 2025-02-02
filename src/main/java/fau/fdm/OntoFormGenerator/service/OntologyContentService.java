package fau.fdm.OntoFormGenerator.service;


import fau.fdm.OntoFormGenerator.data.*;
import fau.fdm.OntoFormGenerator.data.Individual;
import fau.fdm.OntoFormGenerator.exception.*;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Service for getting ontology information about classes, properties and individuals.
 */
@Service
public class OntologyContentService {

    private final Logger logger;
    private final PropertyService propertyService;
    public final OntologyValidationService ontologyValidationService;

    private final IndividualService individualService;

    private final GeneralTDBService generalTDBService;

    @Value("${ontoformgenerator.ontologyDirectory}")
    private String ontologyDirectory;

    @Autowired
    public OntologyContentService(IndividualService individualService, GeneralTDBService generalTDBService, PropertyService propertyService, OntologyValidationService ontologyValidationService) {
        this.generalTDBService = generalTDBService;
        this.logger = LoggerFactory.getLogger(OntologyContentService.class);
        this.individualService = individualService;
        this.propertyService = propertyService;
        this.ontologyValidationService = ontologyValidationService;
    }

    /**
     * Get all classes of an ontology.
     * @param ontologyName The name of the ontology.
     * @return A list of all classes in the ontology.
     */
    public List<OntologyClass> getAllClassesOfOntology(String ontologyName) {
        List<OntologyClass> classes = new ArrayList<>();
        try (TDBConnection connection = new TDBConnection(ReadWrite.READ, ontologyDirectory, ontologyName)) {
            connection.getModel().listClasses().forEach(
                    ontClass -> classes.add(new OntologyClass(ontClass.getLocalName(), ontClass.getURI()))
            );
            return classes;
        }
    }

    /**
     * Get all properties of a domain class.
     * @param ontologyName The name of the ontology.
     * @param classURI The URI of the domain class.
     * @return A list of all properties of the domain class.
     */
    public List<OntologyProperty> getAllPropertiesOfDomain(String ontologyName, String classURI) {
        List<OntologyProperty> properties = new ArrayList<>();
        try (TDBConnection connection = new TDBConnection(ReadWrite.READ, ontologyDirectory, ontologyName)) {
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
                            } else {
                                ontologyProperty.setObjectRange(
                                        new OntologyClass("Thing", "http://www.w3.org/2002/07/owl#Thing")
                                );
                            }
                        } else {
                            ontologyProperty.setObjectProperty(false);
                            if (property.getRange() != null) {
                                ontologyProperty.setDatatypeRange(property.getRange().getLocalName());
                            } else {
                                ontologyProperty.setDatatypeRange("string");
                            }
                        }
                        properties.add(ontologyProperty);
                    }
            );
            return properties;
        }
    }

    /**
     * Get all individuals of an ontology.
     * @param ontologyName The name of the ontology.
     * @return A list of all individuals in the ontology.
     */
    public List<Individual> getAllIndividualsOfOntology(String ontologyName) {
        List<Individual> individuals = new ArrayList<>();
        try (TDBConnection connection = new TDBConnection(ReadWrite.READ, ontologyDirectory, ontologyName)) {
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

    /**
     * Get all individuals of a class. Uses reasoning to also get all indirect individuals.
     * @param ontologyName The name of the ontology.
     * @param classIri The URI of the class.
     * @return A list of all individuals of the class.
     */
    public List<Individual> getAllIndividualsOfClass(String ontologyName, String classIri) {
        List<Individual> individuals = new ArrayList<>();
        try (TDBConnection connection = new TDBConnection(ReadWrite.READ, ontologyDirectory, ontologyName)) {
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

    /**
     * Get the individual of an ontology by its label name.
     * @param individualName The label name of the individual.
     * @param ontologyName The name of the ontology.
     * @return The individual with the given name.
     */
    public Individual getIndividualByString(String individualName, String ontologyName) {
        try (TDBConnection connection = new TDBConnection(ReadWrite.READ, ontologyDirectory, ontologyName)) {
            var individual = individualService.findIndividualInOntology(connection.getDataset(), ontologyName, individualName);
            if (individual == null) {
                return null;
            }
            return new Individual(individual.getLocalName(), propertyService.getLabelOfIndividual(connection.getDataset(), ontologyName, individual.getURI()), individual.getURI(),
                    new OntologyClass(individual.getOntClass().getLocalName(), individual.getOntClass().getURI()),
                    individualService.checkIfIndividualIsImported(connection.getDataset(), ontologyName, individual.getURI()));
        }
    }

    /**
     * Get the set properties (datatype and object) of an individual.
     * @param individualURI The URI of the individual.
     * @param ontologyName The name of the ontology.
     * @return A list of all set properties of the individual.
     */
    public List<SetProperty> getAllSetPropertiesByIndividual(String individualURI, String ontologyName) {
        try (TDBConnection connection = new TDBConnection(ReadWrite.READ, ontologyDirectory, ontologyName)) {
            return getSetProperties(connection.getDataset(), individualURI, ontologyName);
        }
    }

    private List<SetProperty> getSetProperties(Dataset dataset, String individualUri, String ontologyName) {
        List<SetProperty> setProperties = new ArrayList<>();
        var individual = individualService.getIndividualByIri(dataset, ontologyName, individualUri);
        individual.listProperties().forEachRemaining(
                stmt -> {
                    if (stmt.getPredicate().getLocalName().equals("type"))
                        return;
                    if (generalTDBService.checkIfAnnotationProperty(dataset, ontologyName, stmt.getPredicate().getURI()))
                        return;
                    var setProperty = new SetProperty();
                    var ontClass = new OntologyClass(individual.getOntClass().getLocalName(),
                            individual.getOntClass().getURI());
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

    /**
     * Create an empty individual in the ontology of a given class.
     * @param ontologyName The name of the ontology.
     * @param classUri The URI of the class.
     * @param individualName The label name of the individual.
     * @return True if the individual was created successfully, false otherwise.
     */
    public Boolean addEmptyIndividual(String ontologyName, String classUri, String individualName) {
        logger.info("Adding empty individual {} to class {}", individualName, classUri);
        try (TDBConnection connection = new TDBConnection(ReadWrite.WRITE, ontologyDirectory, ontologyName)) {
            var ontClass = connection.getModel().getOntClass(classUri);
            var ontologyURI = ontClass.getURI().substring(0, ontClass.getURI().lastIndexOf("#") + 1);
            connection.getModel().createIndividual(ontologyURI + individualName, ontClass);
            connection.commit();
            logger.info("Successfully added individual {} to class {}", individualName, classUri);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Edit an individual in the ontology - add, remove or change set properties.
     * @param dataset The TDB dataset to use.
     * @param ontologyName The name of the ontology.
     * @param individualUri The URI of the individual.
     * @param form The form data with the new properties in the following format:
     *             {
     *             "propertyName": ["property1", "property2", ...],
     *             "fieldValue": ["value1", "value2", ...],
     *             "isObjectProperty": ["true", "false", ...]
     *             }
     * @return The URI of the edited individual.
     */
    public String editIndividual(Dataset dataset,
                                 String ontologyName,
                                 String individualUri,
                                 Map<String, String[]> form) {
        logger.info("Editing individual {} in ontology {}", individualUri, ontologyName);
        logger.debug("Form data: {}", form);
        var ontology = OntModelFactory.createModel(dataset.getNamedModel(ontologyName).getGraph(),
                OntSpecification.OWL2_DL_MEM);

        var individual = individualService.getIndividualByIri(dataset, ontologyName, individualUri);

        var setProperties = getSetProperties(dataset, individualUri, ontologyName);

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
        logger.info("Successfully edited individual {} in ontology {}", individualUri, ontologyName);
        return individualUri;
    }

    /**
     * Generate the subclass graph of an ontology.
     * @param ontologyName The name of the ontology.
     * @return The subclass graph of the ontology.
     */
    public SubclassGraph buildSubclassGraph(String ontologyName) {
        SubclassGraph subclassGraph = new SubclassGraph();
        try (TDBConnection connection = new TDBConnection(ReadWrite.READ, ontologyDirectory, ontologyName)) {
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

    /**
     * Add a new class to the ontology.
     * @param ontologyName The name of the ontology.
     * @param className The label name of the new class.
     * @param superClass The label name of the superclass of the new class. Must be filled with "owl:Thing" if no superclass is given.
     * @return The new class.
     * @throws OntologyValidationException If the validation of the new class fails - for example if there are problems
     *  with the naming schema or if it already exists.
     */
    public OntologyClass addNewClass(String ontologyName, String className,
                                     String superClass) throws OntologyValidationException {
        logger.info("Adding new class {} to ontology {}", className, ontologyName);
        try (TDBConnection connection = new TDBConnection(ReadWrite.WRITE, ontologyDirectory, ontologyName)) {
            var superClassUri = generalTDBService.getClassURIInOntology(connection.getDataset(), ontologyName, superClass);
            var uri = generalTDBService.getOntologyURIByOntologyName(connection.getDataset(), ontologyName)
                    + "#" + className;
            if (ontologyValidationService.checkIfURIisUsed(connection.getDataset(), ontologyName, uri)) {
                throw new URIAlreadyExistsException(className, uri);
            }
            var newClass = connection.getModel().createClass(uri);
            if (superClassUri != null) {
                newClass.addSuperClass(connection.getModel().getOntClass(superClassUri));
            }
            var isUsedDefinedProp = connection.getModel().getProperty(IS_USER_DEFINED);
            newClass.addProperty(isUsedDefinedProp, connection.getModel().createTypedLiteral(true));
            connection.commit();
            logger.info("Successfully added new class {} to ontology {}", className, ontologyName);
            return new OntologyClass(className, uri);
        } catch (Exception e) {
            logger.error("Error adding new class {} to ontology {}", className, ontologyName);
            logger.error(e.getMessage());
            throw e;
        }
    }

    /**
     * Delete an existing individual from the ontology.
     * @param ontologyName The name of the ontology.
     * @param individualUri The URI of the individual.
     */
    public void deleteIndividual(String ontologyName, String individualUri) {
        logger.info("Deleting individual {} from ontology {}", individualUri, ontologyName);
        try (TDBConnection connection = new TDBConnection(ReadWrite.WRITE, ontologyDirectory, ontologyName)) {
            individualService.deleteIndividualByIri(connection.getDataset(), ontologyName, individualUri);
            individualService.deleteIndividualByIri(connection.getDataset(), "forms", individualUri);
            logger.info("Successfully deleted individual {} from ontology {}", individualUri, ontologyName);
            connection.commit();
        } catch (Exception e) {
            logger.error("Error deleting individual {} from ontology {}", individualUri, ontologyName);
            logger.error(e.getMessage());
            throw e;
        }
    }

    private final String IS_USER_DEFINED = "http://ontologies.ontoformgenerator.de/general#isUserDefined";
    private final String RDFS_COMMENT = "http://www.w3.org/2000/01/rdf-schema#comment";

    /**
     * Create a new property in the ontology.
     * @param ontologyName The name of the ontology.
     * @param propDescription The description (rdfs:comment) of the new property.
     * @param propertyName The label name of the new property.
     * @param objectProperty True if the new property is an object property, false if it is a datatype property.
     * @param domain The IRI of the domain class of the new property.
     * @param range The IRI of the range class of the new property.
     * @param validate True if the new property should be validated, false otherwise.
     * @return The new property.
     * @throws OntologyValidationException If the validation of the new property fails - for example if there are problems
     *      with the naming schema or if it already exists.
     */
    public OntologyProperty createNewProperty(String ontologyName, String propDescription, String propertyName,
                                              boolean objectProperty, String domain, String range,
                                              boolean validate) throws OntologyValidationException {
        logger.info("Creating new property {} in ontology {}", propertyName, ontologyName);
        try (TDBConnection connection = new TDBConnection(ReadWrite.WRITE, ontologyDirectory, ontologyName)) {
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
            var domainClass = ontModel.getOntClass(domain);
            OntResource fullRange;
            var isUsedDefinedProp = ontModel.getProperty(IS_USER_DEFINED);
            var rdfsComment = ontModel.getProperty(RDFS_COMMENT);
            Property property;
            String uri = generalTDBService.getOntologyURIByOntologyName(connection.getDataset(), ontologyName) + "#" + propertyName;
            if (ontologyValidationService.checkIfURIisUsed(connection.getDataset(), ontologyName, uri)) {
                throw new URIAlreadyExistsException(propertyName, uri);
            }
            if (objectProperty) {
                property = ontModel.createObjectProperty(uri);
                var prop = (ObjectProperty) property;
                prop.addDomain(domainClass);
                fullRange = ontModel.getOntClass(generalTDBService.getClassURIInOntology(connection.getDataset(), ontologyName, range));
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
            logger.info("Successfully created new property {} in ontology {}", propertyName, ontologyName);
            return new OntologyProperty(propertyName, new OntologyClass(domain, domainClass.getURI()), uri,
                    objectProperty, objectProperty ? new OntologyClass(fullRange.getLocalName(), fullRange.getURI()) : null,
                    objectProperty ? null : range);
        } catch (Exception e) {
            logger.error("Error creating new property {} in ontology {}", propertyName, ontologyName);
            logger.error(e.getMessage());
            throw e;
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

    /**
     * Get all already used target classes of an ontology - classes for which forms were already created.
     * @param ontologyName The name of the ontology.
     * @return A list of all target classes.
     */
    public List<OntologyClass> getTargetClasses(String ontologyName) {
        try (TDBConnection connection = new TDBConnection(ReadWrite.READ, ontologyDirectory, ontologyName)) {
            var ontology = individualService.findIndividualInOntology(connection.getDataset(), "forms", ontologyName);
            var targetClasses = propertyService.getMultipleObjectPropertyValuesFromIndividual(connection.getDataset(), "forms",
                    ontology, "hasTargetClass");
            List<OntologyClass> classes = new ArrayList<>();
            targetClasses.forEach(
                    targetClass -> classes.add(new OntologyClass(targetClass.getLocalName(), targetClass.getURI()))
            );
            return classes;
        }
    }

    /**
     * Query properties of a class in an ontology - search for name, label and description.
     * @param ontologyName The name of the ontology.
     * @param classIri The URI of the class.
     * @param query The search query.
     * @return A list of all properties that match the search query.
     */
    public List<OntologyProperty> queryProperties(String ontologyName, String classIri, String query) {
        try (TDBConnection connection = new TDBConnection(ReadWrite.READ, ontologyDirectory, ontologyName)) {
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
