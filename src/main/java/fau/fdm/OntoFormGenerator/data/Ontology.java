package fau.fdm.OntoFormGenerator.data;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/**
 * Represents an ontology with name and base IRI.
 */
@Getter
@Setter
@AllArgsConstructor
public class Ontology {
    private String name;
    private String iri;
}
