package fau.fdm.OntoFormGenerator.data;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class FormField {
    private OntologyProperty ontologyProperty;
    private String fieldType;
    private String name;
    private int maximumValues;
}
