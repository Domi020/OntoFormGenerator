package fau.fdm.OntoFormGenerator.validation;

import fau.fdm.OntoFormGenerator.data.ValidationResult;
import org.apache.jena.rdf.model.Model;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

public abstract class Validator {
    public abstract ValidationResult validate(Model model);

    protected ByteArrayInputStream transformModelToInputStream(Model tdbModel) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        tdbModel.write(outputStream, "RDF/XML");
        return new ByteArrayInputStream(outputStream.toByteArray());
    }
}
