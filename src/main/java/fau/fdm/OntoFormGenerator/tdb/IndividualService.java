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

    // TODO: Replace "forms" strings by constants

    // Retrieval methods

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

    public Individual getIndividualByLocalName(Dataset dataset,
                                               String ontologyName,
                                               String individualName) {
        var ontModel = generalTDBService.getOntModel(dataset.getNamedModel(ontologyName));
        return ontModel.getIndividual(baseIRI + "/" + ontologyName + "#" + individualName);
    }

    public Individual getIndividualByIri(Dataset dataset,
                                         String ontologyName,
                                         String iri) {
        OntModel ontModel = generalTDBService.getOntModel(dataset.getNamedModel(ontologyName));
        return ontModel.getIndividual(iri);
    }

    public Individual findIndividualInOntology(Dataset dataset,
                                               String ontologyName,
                                               String individualName) {
        OntModel ontModel = generalTDBService.getOntModel(dataset.getNamedModel(ontologyName));
        var ind = ontModel.listIndividuals().filterKeep(individual -> individual.getLocalName().equals(individualName))
                .nextOptional();
        return ind.orElse(null);
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

    // ----------------------------------------------------------------------------------------------------------------
    // ----------------------------------------------------------------------------------------------------------------

    // Creation methods

    public Individual addIndividualByLocalName(Dataset dataset,
                                               String ontologyName,
                                               String className,
                                               String individualName) {
        var model = dataset.getNamedModel(ontologyName);
        var ontModel = generalTDBService.getOntModel(model);
        var ontClass = ontModel.getOntClass(baseIRI + "/" + ontologyName + "#" + className);
        return ontModel.createIndividual(baseIRI + "/" + ontologyName + "#" + individualName, ontClass);
    }

    public Individual addIndividualByLocalName(Dataset dataset,
                                               String className,
                                               String individualName) {
        return addIndividualByLocalName(dataset, "forms", className, individualName);
    }

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

    public void deleteIndividualByLocalName(Dataset dataset,
                                            String ontologyName,
                                            String individualName) {
        OntModel ontModel = generalTDBService.getOntModel(dataset.getNamedModel(ontologyName));
        ontModel.getIndividual(baseIRI + "/" + ontologyName + "#" + individualName).remove();
    }

    public void deleteIndividualByIri(Dataset dataset,
                                      String ontologyName,
                                      String iri) {
        OntModel ontModel = generalTDBService.getOntModel(dataset.getNamedModel(ontologyName));
        ontModel.getIndividual(iri).remove();
    }

    // ----------------------------------------------------------------------------------------------------------------
    // ----------------------------------------------------------------------------------------------------------------

    // Other methods

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
