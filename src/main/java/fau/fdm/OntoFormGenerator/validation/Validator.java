package fau.fdm.OntoFormGenerator.validation;

import fau.fdm.OntoFormGenerator.data.ValidationResult;
import org.apache.jena.rdf.model.Model;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

/**
 * Represents an OWL reasoning validator.
 */
public abstract class Validator {

    /**
     * Validates the given model, and checks if the knowledge base is consistent or not.
     * @param model The OWL model to validate.
     * @return The validation result containing a boolean flag for consistency and explanation axioms if inconsistent.
     */
    public abstract ValidationResult validate(Model model);

    protected ByteArrayInputStream transformModelToInputStream(Model tdbModel) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        tdbModel.write(outputStream, "RDF/XML");
        return new ByteArrayInputStream(outputStream.toByteArray());
    }
}
