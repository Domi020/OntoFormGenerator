package fau.fdm.OntoFormGenerator.data;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Individual {
    private String name;
    private String iri;
    private OntologyClass ontologyClass;
}
