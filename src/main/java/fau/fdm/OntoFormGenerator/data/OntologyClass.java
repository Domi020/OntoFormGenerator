package fau.fdm.OntoFormGenerator.data;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class OntologyClass {
    private String name;
    private String uri;

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof OntologyClass) {
            return ((OntologyClass) obj).getUri().equals(this.uri) &&
                    ((OntologyClass) obj).getName().equals(this.name);
        }
        return false;
    }
}
