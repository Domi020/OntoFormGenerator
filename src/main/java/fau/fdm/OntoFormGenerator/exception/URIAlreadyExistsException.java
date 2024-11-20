package fau.fdm.OntoFormGenerator.exception;

public class URIAlreadyExistsException extends OntologyValidationException {
    public URIAlreadyExistsException(String conceptName, String URI) {
        super("An entity " + conceptName + " does already exist." +
                " Choose another name.");
    }
}
