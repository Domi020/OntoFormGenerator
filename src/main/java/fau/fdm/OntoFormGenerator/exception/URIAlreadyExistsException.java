package fau.fdm.OntoFormGenerator.exception;

/**
 * ValidationException when an entity with its URI already exists.
 */
public class URIAlreadyExistsException extends OntologyValidationException {
    public URIAlreadyExistsException(String conceptName, String URI) {
        super("An entity " + conceptName + " does already exist." +
                " Choose another name.");
    }
}
