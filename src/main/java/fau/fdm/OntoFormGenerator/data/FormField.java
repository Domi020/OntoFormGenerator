package fau.fdm.OntoFormGenerator.data;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/**
 * Represents a form field in a form including name, property reference and field type.
 */
@Getter
@Setter
@AllArgsConstructor
public class FormField {
    private OntologyProperty ontologyProperty;
    private String fieldType;
    private String name;
    private int maximumValues;
    private int minimumValues;
    private boolean required;
}
