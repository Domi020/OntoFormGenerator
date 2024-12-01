package fau.fdm.OntoFormGenerator.tdb;

import org.apache.jena.ontology.*;
import org.apache.jena.query.*;
import org.apache.jena.rdf.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * TDB Service for handling individuals in the ontology.
 * For example, retrieving, adding, deleting individuals.
 */
@Service
public class IndividualService {

    private final GeneralTDBService generalTDBService;

    @Value("${ontoformgenerator.ontologies.baseIRI}")
    private String baseIRI;

    private final Logger logger = LoggerFactory.getLogger(IndividualService.class);

    public IndividualService(GeneralTDBService generalTDBService) {
        this.generalTDBService = generalTDBService;
    }

    // Retrieval methods

    /**
     * Get all individuals of a given class in the ontology.
     * @param dataset The dataset to use.
     * @param ontologyName The name of the ontology.
     * @param className The name of the class.
     * @return A list of individuals of the given class.
     */
    public List<Individual> getAllIndividualsOfClass(Dataset dataset,
                                                        String ontologyName,
                                                        String className) {
        List<Individual> individuals = new ArrayList<>();
        var ontModel = generalTDBService.getOntModel(dataset.getNamedModel(ontologyName));
        var classURI = generalTDBService.getClassURIInOntology(dataset, ontologyName, className);
        var ontClass = ontModel.getOntClass(classURI);
        ontModel.listIndividuals().filterKeep(individual -> individual.hasOntClass(ontClass, true))
                .forEach(individuals::add);
        return individuals;
    }

    /**
     * Get an individual in the ontology by its local name.
     * @param dataset The dataset to use.
     * @param ontologyName The name of the ontology.
     * @param individualName The local name of the individual.
     * @return The individual if found, otherwise null.
     */
    public Individual getIndividualByLocalName(Dataset dataset,
                                               String ontologyName,
                                               String individualName) {
        var ontModel = generalTDBService.getOntModel(dataset.getNamedModel(ontologyName));
        return ontModel.getIndividual(baseIRI + "/" + ontologyName + "#" + individualName);
    }

    /**
     * Find an individual in the ontology by its IRI.
     * @param dataset The dataset to use.
     * @param ontologyName The name of the ontology.
     * @param iri The IRI of the individual.
     * @return The individual if found, otherwise null.
     */
    public Individual getIndividualByIri(Dataset dataset,
                                         String ontologyName,
                                         String iri) {
        OntModel ontModel = generalTDBService.getOntModel(dataset.getNamedModel(ontologyName));
        return ontModel.getIndividual(iri);
    }

    /**
     * Search for an individual in the ontology by its local name.
     * @param dataset The dataset to use.
     * @param ontologyName The name of the ontology.
     * @param individualName The local name of the individual.
     * @return The individual if found, otherwise null.
     */
    public Individual findIndividualInOntology(Dataset dataset,
                                               String ontologyName,
                                               String individualName) {
        OntModel ontModel = generalTDBService.getOntModel(dataset.getNamedModel(ontologyName));
        var ind = ontModel.listIndividuals().filterKeep(individual -> individual.getLocalName().equals(individualName))
                .nextOptional();
        return ind.orElse(null);
    }

    /**
     * Get all individuals by a SPARQL query.
     * @param dataset The dataset to use.
     * @param ontologyName The name of the ontology.
     * @param query The SPARQL query to execute. The query should select individuals with the variable name "?f".
     * @return A list of individuals found by the query.
     */
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

    // ----------------------------------------------------------------------------------------------------------------
    // ----------------------------------------------------------------------------------------------------------------

    // Creation methods

    /**
     * Add a new individual to the ontology by its local name.
     * @param dataset The dataset to use.
     * @param ontologyName The name of the ontology.
     * @param className The name of the class of the individual.
     * @param individualName The local name of the individual.
     * @return The created individual.
     */
    public Individual addIndividualByLocalName(Dataset dataset,
                                               String ontologyName,
                                               String className,
                                               String individualName) {
        var model = dataset.getNamedModel(ontologyName);
        var ontModel = generalTDBService.getOntModel(model);
        var ontClass = ontModel.getOntClass(baseIRI + "/" + ontologyName + "#" + className);
        return ontModel.createIndividual(baseIRI + "/" + ontologyName + "#" + individualName, ontClass);
    }

    /**
     * Add a new individual to the ontology by its local name in the forms ontology.
     * @param dataset The dataset to use.
     * @param className The name of the class of the individual.
     * @param individualName The local name of the individual.
     * @return The created individual.
     */
    public Individual addIndividualByLocalName(Dataset dataset,
                                               String className,
                                               String individualName) {
        return addIndividualByLocalName(dataset, "forms", className, individualName);
    }

    /**
     * Add a new individual to the ontology by its IRI.
     * @param dataset The dataset to use.
     * @param className The name of the class of the individual.
     * @param IRI The IRI of the individual.
     * @return The created individual.
     */
    public Individual addIndividualWithUniqueIRI(Dataset dataset,
                                                 String className,
                                                 String IRI) {
        var model = dataset.getNamedModel("forms");
        var ontModel = generalTDBService.getOntModel(model);
        var ontClass = ontModel.getOntClass(baseIRI + "/forms#" + className);
        return ontModel.createIndividual(IRI, ontClass);
    }

    // ----------------------------------------------------------------------------------------------------------------
    // ----------------------------------------------------------------------------------------------------------------

    // Deletion methods

    /**
     * Delete an individual by its local name.
     * @param dataset The dataset to use.
     * @param ontologyName The name of the ontology.
     * @param individualName The local name of the individual.
     */
    public void deleteIndividualByLocalName(Dataset dataset,
                                            String ontologyName,
                                            String individualName) {
        OntModel ontModel = generalTDBService.getOntModel(dataset.getNamedModel(ontologyName));
        ontModel.getIndividual(baseIRI + "/" + ontologyName + "#" + individualName).remove();
    }

    /**
     * Delete an individual by its IRI.
     * @param dataset The dataset to use.
     * @param ontologyName The name of the ontology.
     * @param iri The IRI of the individual.
     */
    public void deleteIndividualByIri(Dataset dataset,
                                      String ontologyName,
                                      String iri) {
        OntModel ontModel = generalTDBService.getOntModel(dataset.getNamedModel(ontologyName));
        ontModel.getIndividual(iri).remove();
    }

    // ----------------------------------------------------------------------------------------------------------------
    // ----------------------------------------------------------------------------------------------------------------

    // Other methods

    /**
     * Check if an individual is imported in the ontology, or was created with the form generator.
     * @param dataset The dataset to use.
     * @param ontologyName The name of the ontology.
     * @param individualIri The IRI of the individual.
     * @return True if the individual is imported, false otherwise (was created with OntoFormGenerator).
     */
    public boolean checkIfIndividualIsImported(Dataset dataset,
                                               String ontologyName,
                                               String individualIri) {
        OntModel ontModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM,
                dataset.getNamedModel(ontologyName));
        var baseModelWithoutImports = ontModel.getBaseModel();
        var filtered = baseModelWithoutImports.listStatements().filterKeep(
                statement -> statement.getSubject().getURI().equals(individualIri) &&
                        statement.getPredicate().getURI().equals("http://www.w3.org/1999/02/22-rdf-syntax-ns#type")
        );
        return !filtered.hasNext();
    }
}
