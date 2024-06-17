package fau.fdm.OntoFormGenerator.data;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OntologyProperty {
    private String name;
    private OntologyClass domain;
    private boolean isObjectProperty;
    private OntologyClass objectRange;
    private String datatypeRange;
}
