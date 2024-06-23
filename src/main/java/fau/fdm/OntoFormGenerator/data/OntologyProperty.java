package fau.fdm.OntoFormGenerator.data;

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

    public OntologyProperty(String name, OntologyClass domain, boolean isObjectProperty, OntologyClass objectRange, String datatypeRange) {
        this.name = name;
        this.domain = domain;
        this.isObjectProperty = isObjectProperty;
        this.objectRange = objectRange;
        this.datatypeRange = datatypeRange;
    }

    public OntologyProperty() {}
}
