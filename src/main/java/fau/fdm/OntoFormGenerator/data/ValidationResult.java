package fau.fdm.OntoFormGenerator.data;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/**
 * Represents the result of a validation process. Includes a boolean value indicating whether
 * the validation was successful and a reason if it was not.
 */
@AllArgsConstructor
@Getter
@Setter
public class ValidationResult {
    boolean consistent;
    String reason;
}
