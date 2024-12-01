package fau.fdm.OntoFormGenerator.exception;

/**
 * ValidationException when a property is too similar to existing properties.
 */
public class SimilarPropertiesExistException extends OntologyValidationException {
    public SimilarPropertiesExistException(String newProperty, String[] existingProperties) {
        super("The property " + newProperty + " is too similar to the existing properties: " +
                String.join(", ", existingProperties));
    }
}
