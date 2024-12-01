package fau.fdm.OntoFormGenerator.data;

import lombok.Getter;
import lombok.Setter;

/**
 * Represents a property (datatype or object) in an ontology.
 */
@Getter
@Setter
public class OntologyProperty {
    private String name;
    private OntologyClass domain;
    private String uri;
    private boolean isObjectProperty;
    private OntologyClass objectRange;
    private String datatypeRange;
    private String rdfsLabel;
    private String rdfsComment;

    public OntologyProperty(String name, OntologyClass domain, String uri, boolean isObjectProperty, OntologyClass objectRange, String datatypeRange) {
        this.name = name;
        this.domain = domain;
        this.isObjectProperty = isObjectProperty;
        this.objectRange = objectRange;
        this.datatypeRange = datatypeRange;
        this.uri = uri;
    }

    public OntologyProperty() {}
}
