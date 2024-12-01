package fau.fdm.OntoFormGenerator.data;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Represents an individual in an ontology.
 */
@Getter
@Setter
@NoArgsConstructor
public class Individual {
    private String name;
    private String label;
    private String iri;
    private OntologyClass ontologyClass;
    private boolean isImported;

    public Individual(String name, String label, String iri, OntologyClass ontologyClass, boolean isImported) {
        this.name = name;
        this.label = label == null ? name : label;
        this.iri = iri;
        this.ontologyClass = ontologyClass;
        this.isImported = isImported;
    }
}
