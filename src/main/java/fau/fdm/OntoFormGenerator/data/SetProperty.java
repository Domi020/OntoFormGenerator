package fau.fdm.OntoFormGenerator.data;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Represents an existing property in the knowledge base including value and reference to the individual.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class SetProperty {
    private OntologyProperty property;
    private Individual individual;
    private Object value;
}
