package fau.fdm.OntoFormGenerator.exception;

/**
 * ValidationException when a property contains a word that is filtered by the naming filter.
 */
public class NamingFilterViolatedException extends OntologyValidationException {
    public NamingFilterViolatedException(String newProperty, String filteredWord) {
        super("The property " + newProperty + " contains the filtered word \"" + filteredWord + "\".");
    }
}
