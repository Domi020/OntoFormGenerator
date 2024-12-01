package fau.fdm.OntoFormGenerator.data;

import lombok.Getter;
import lombok.Setter;

/**
 * Represents a draft of an individual that is not yet in the ontology.
 */
@Getter
@Setter
public class Draft extends Individual {
    private String firstDraftName;

    public Draft(String name, String label, String iri, OntologyClass ontologyClass, boolean isImported, String firstDraftName) {
        super(name, label, iri, ontologyClass, isImported);
        this.firstDraftName = firstDraftName;
    }
}
