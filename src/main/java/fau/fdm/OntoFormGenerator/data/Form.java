package fau.fdm.OntoFormGenerator.data;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class Form {
    private String formName;
    private String ontologyName;
    private OntologyClass targetClass;
}
