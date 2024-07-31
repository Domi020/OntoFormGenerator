package fau.fdm.OntoFormGenerator.data;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class SubclassRelation {
    private OntologyClass superClass;
    private OntologyClass subClass;
}
