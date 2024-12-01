package fau.fdm.OntoFormGenerator.exception;

/**
 * ValidationException when the naming schema of a property does not match the naming schema of the ontology.
 */
public class NamingSchemaDifferentException extends OntologyValidationException {
    public NamingSchemaDifferentException(String newProperty, String ontologyName,
                                          String newPropertySchema, String ontologySchema) {
        super("The naming schema of the property " + newProperty + " does not match the naming schema of the ontology "
                + ontologyName + ". The ontology naming schema is " + ontologySchema + " and the property naming schema is " + newPropertySchema);
    }
}
