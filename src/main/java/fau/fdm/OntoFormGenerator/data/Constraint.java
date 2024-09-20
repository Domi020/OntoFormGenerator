package fau.fdm.OntoFormGenerator.data;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Constraint {
    private OntologyClass domain;
    private OntologyProperty onProperty;
    private Object value;
    private ConstraintType constraintType;

    public enum ConstraintType {
        MIN,
        MAX,
        EXACTLY,
        ONLY,
    }
}
