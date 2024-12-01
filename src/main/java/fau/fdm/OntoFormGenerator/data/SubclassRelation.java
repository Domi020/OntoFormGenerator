package fau.fdm.OntoFormGenerator.data;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Represents a subclass relation between two ontology classes. Used in the SubclassGraph.
 */
@Getter
@AllArgsConstructor
public class SubclassRelation {
    private OntologyClass superClass;
    private OntologyClass subClass;
}
