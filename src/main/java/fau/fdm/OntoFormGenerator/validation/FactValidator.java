package fau.fdm.OntoFormGenerator.validation;

import com.clarkparsia.owlapi.explanation.BlackBoxExplanation;
import com.clarkparsia.owlapi.explanation.HSTExplanationGenerator;
import fau.fdm.OntoFormGenerator.data.ValidationResult;
import org.apache.jena.rdf.model.Model;
import org.semanticweb.HermiT.Configuration;
import org.semanticweb.HermiT.ReasonerFactory;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.manchestersyntax.renderer.ManchesterOWLSyntaxOWLObjectRendererImpl;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import uk.ac.manchester.cs.jfact.JFactFactory;

public class FactValidator extends Validator {

    @Override
    public ValidationResult validate(Model model) {
        var inStream = transformModelToInputStream(model);
        OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
        OWLDataFactory dataFactory = manager.getOWLDataFactory();
        manager.getOntologyConfigurator().setMissingImportHandlingStrategy(MissingImportHandlingStrategy.SILENT);
        OWLOntology owlApiOntology;
        try {
            owlApiOntology = manager.loadOntologyFromOntologyDocument(inStream);
        } catch (OWLOntologyCreationException e) {
            throw new RuntimeException("Error while loading ontology", e);
        }
        var reasonerFactory = new JFactFactory();
        Configuration config = new Configuration();
        config.throwInconsistentOntologyException = false;
        config.individualTaskTimeout = Long.MAX_VALUE;
        var reasoner = reasonerFactory.createReasoner(owlApiOntology, config);
        if (reasoner.isConsistent()) {
            return new ValidationResult(true, "");
        }
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
}
