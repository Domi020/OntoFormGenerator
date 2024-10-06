package fau.fdm.OntoFormGenerator.service;

import fau.fdm.OntoFormGenerator.data.Constraint;
import fau.fdm.OntoFormGenerator.data.Individual;
import fau.fdm.OntoFormGenerator.data.OntologyClass;
import fau.fdm.OntoFormGenerator.data.OntologyProperty;
import org.apache.jena.ontology.OntClass;
import org.apache.jena.ontology.OntModelSpec;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.tdb2.TDB2Factory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class OntologyConstraintService {

    @Value("${ontoformgenerator.ontologyDirectory}")
    private String ontologyDirectory;

    public List<Constraint> getConstraints(Dataset dataset, String ontologyName, String domainClassUri, String propertyUri) {
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
        return resultList;
    }

    public List<Constraint> getConstraints(String ontologyName, String domainClassUri, String propertyUri) {
        Dataset dataset = TDB2Factory.connectDataset(ontologyDirectory);
        dataset.begin(ReadWrite.READ);
        try {
            return getConstraints(dataset, ontologyName, domainClassUri, propertyUri);
        } finally {
            dataset.end();
        }
    }

    public List<Individual> filterForAllValuesFromIndividuals(List<Individual> individuals,
                                                              String ontologyName,
                                                              String domainClassUri,
                                                              String propertyUri) {
        Dataset dataset = TDB2Factory.connectDataset(ontologyDirectory);
        dataset.begin(ReadWrite.READ);
        try {
            var ontModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM,
                    dataset.getNamedModel(ontologyName));
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
        } finally {
            dataset.end();
        }
        return individuals;
    }


}
