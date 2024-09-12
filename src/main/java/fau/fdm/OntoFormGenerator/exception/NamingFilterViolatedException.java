package fau.fdm.OntoFormGenerator.exception;

public class NamingFilterViolatedException extends OntologyValidationException {
    public NamingFilterViolatedException(String newProperty, String filteredWord) {
        super("The property " + newProperty + " contains the filtered word \"" + filteredWord + "\".");
    }
}
