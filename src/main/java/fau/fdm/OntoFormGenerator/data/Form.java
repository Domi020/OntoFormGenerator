package fau.fdm.OntoFormGenerator.data;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/**
 * Represents a form that is generated from an ontology.
 */
@Getter
@Setter
@AllArgsConstructor
public class Form {
    private String formName;
    private String ontologyName;
    private OntologyClass targetClass;
}
