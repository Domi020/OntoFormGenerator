package fau.fdm.OntoFormGenerator.data;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Draft extends Individual {
    private String firstDraftName;

    public Draft(String name, String label, String iri, OntologyClass ontologyClass, boolean isImported, String firstDraftName) {
        super(name, label, iri, ontologyClass, isImported);
        this.firstDraftName = firstDraftName;
    }
}
