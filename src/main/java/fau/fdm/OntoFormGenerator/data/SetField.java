package fau.fdm.OntoFormGenerator.data;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Represents an already set field in a form. Includes field name and the list of values this field holds.
 */
@Getter
@Setter
@AllArgsConstructor
public class SetField {
    private String fieldName;
    private List<String> values;
}
