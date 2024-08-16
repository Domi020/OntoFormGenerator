package fau.fdm.OntoFormGenerator.data;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@AllArgsConstructor
@Getter
@Setter
public class ValidationResult {
    boolean consistent;
    String reason;
}
