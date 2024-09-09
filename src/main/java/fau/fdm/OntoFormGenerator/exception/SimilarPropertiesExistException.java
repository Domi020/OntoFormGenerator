package fau.fdm.OntoFormGenerator.exception;


public class SimilarPropertiesExistException extends OntologyValidationException {
    public SimilarPropertiesExistException(String newProperty, String[] existingProperties) {
        super("The property " + newProperty + " is too similar to the existing properties: " +
                String.join(", ", existingProperties));
    }
}
