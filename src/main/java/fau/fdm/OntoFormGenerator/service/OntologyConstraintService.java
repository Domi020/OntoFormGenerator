package fau.fdm.OntoFormGenerator.service;

import fau.fdm.OntoFormGenerator.data.Constraint;
import fau.fdm.OntoFormGenerator.data.Individual;
import fau.fdm.OntoFormGenerator.data.OntologyClass;
import fau.fdm.OntoFormGenerator.data.OntologyProperty;
import fau.fdm.OntoFormGenerator.tdb.TDBConnection;
import org.apache.jena.ontology.OntClass;
import org.apache.jena.ontology.OntModelSpec;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Service for getting constraints from the ontologies.
 */
@Service
public class OntologyConstraintService {

    @Value("${ontoformgenerator.ontologyDirectory}")
    private String ontologyDirectory;

    private final Logger logger;

    public OntologyConstraintService() {
        this.logger = LoggerFactory.getLogger(OntologyConstraintService.class);
    }

    /**
     * Get cardinality and value constraints for a given domain class and property.
     * @param dataset The dataset to use.
     * @param ontologyName The name of the ontology.
     * @param domainClassUri The URI of the domain class.
     * @param propertyUri The URI of the property.
     * @return A list of constraints for this combination.
     */
    public List<Constraint> getConstraints(Dataset dataset, String ontologyName, String domainClassUri, String propertyUri) {
        logger.info("Getting constraints for domain class {} and property {}", domainClassUri, propertyUri);
        var ontModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM,
                dataset.getNamedModel(ontologyName));

        var resultList = new ArrayList<Constraint>();

        var domainClass = ontModel.getOntClass(domainClassUri);
        var domainClassLoc = new OntologyClass(domainClass.getLocalName(), domainClass.getURI());
        domainClass.listSuperClasses().forEachRemaining(subClass -> {
            if (subClass.isRestriction()) {
                var restriction = subClass.asRestriction();
                var onProperty = restriction.getOnProperty();
                if (propertyUri != null && !onProperty.getURI().equals(propertyUri)) return;
                var res = new Constraint();
                res.setDomain(domainClassLoc);
                res.setOnProperty(new OntologyProperty(onProperty.getLocalName(),
                        domainClassLoc, onProperty.getURI(), onProperty.isObjectProperty(), null, null));
                if (restriction.isMaxCardinalityRestriction()) {
                    res.setConstraintType(Constraint.ConstraintType.MAX);
                    res.setValue(restriction.asMaxCardinalityRestriction().getMaxCardinality());
                } else if (restriction.isMinCardinalityRestriction()) {
                    res.setConstraintType(Constraint.ConstraintType.MIN);
                    res.setValue(restriction.asMinCardinalityRestriction().getMinCardinality());
                } else if (restriction.isCardinalityRestriction()) {
                    res.setConstraintType(Constraint.ConstraintType.EXACTLY);
                    res.setValue(restriction.asCardinalityRestriction().getCardinality());
                } else if (restriction.isAllValuesFromRestriction()) {
                    res.setConstraintType(Constraint.ConstraintType.ONLY);
                    res.setValue(restriction.asAllValuesFromRestriction().getAllValuesFrom().getURI());
                } else {
                    return;
                }
                resultList.add(res);
            }
        });
        logger.info("Found {} constraints", resultList.size());
        logger.debug("Constraints: {}", resultList);
        return resultList;
    }

    /**
     * Get cardinality and value constraints for a given domain class and property.
     * @param ontologyName The name of the ontology.
     * @param domainClassUri The URI of the domain class.
     * @param propertyUri The URI of the property.
     * @return A list of constraints for this combination.
     */
    public List<Constraint> getConstraints(String ontologyName, String domainClassUri, String propertyUri) {
        try (TDBConnection connection = new TDBConnection(ReadWrite.READ, ontologyDirectory, ontologyName)) {
            return getConstraints(connection.getDataset(), ontologyName, domainClassUri, propertyUri);
        }
    }

    /**
     * Filter a list of allowed individuals for the given domain class and property from a complete list of individuals.
     * @param individuals The complete list of individuals for a property.
     * @param ontologyName The name of the ontology.
     * @param domainClassUri The URI of the domain class.
     * @param propertyUri The URI of the property.
     * @return A list of individuals that are allowed for the given domain class and property. This is a subset of
     *       the input list individuals.
     */
    public List<Individual> filterForAllValuesFromIndividuals(List<Individual> individuals,
                                                              String ontologyName,
                                                              String domainClassUri,
                                                              String propertyUri) {
        try (TDBConnection connection = new TDBConnection(ReadWrite.READ, ontologyDirectory, ontologyName)) {
            var ontModel = connection.getModel();
            ontModel.getOntClass(domainClassUri).listSuperClasses().forEachRemaining(cls -> {
                if (cls.isRestriction()) {
                    var restriction = cls.asRestriction();
                    if (restriction.isAllValuesFromRestriction()) {
                        var allValuesFrom = restriction.asAllValuesFromRestriction();
                        if (!restriction.getOnProperty().getURI().equals(propertyUri)) return;
                        var restrictionClass = (OntClass) allValuesFrom.getAllValuesFrom();
                        List<Resource> rangeClasses = new ArrayList<>();
                        if (restrictionClass.isEnumeratedClass()) {
                            rangeClasses.addAll(restrictionClass.asEnumeratedClass().listOneOf().toList());
                        } else {
                            rangeClasses.add(restrictionClass);
                        }

                        individuals.removeIf(iv -> {
                            for (var range : rangeClasses) {
                                if (iv.getIri().equals(range.getURI())) {
                                    return false;
                                }
                            }
                            return true;
                        });
                    }
                }
            });
        }
        return individuals;
    }
}
