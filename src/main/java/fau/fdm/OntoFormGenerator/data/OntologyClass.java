package fau.fdm.OntoFormGenerator.data;

import lombok.Getter;
import lombok.Setter;

/**
 * Represents an ontology class in an ontology with name and URI.
 */
@Getter
@Setter
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

    public OntologyClass(String name, String uri) {
        if (name != null && uri != null &&
                name.equals("Thing") && uri.equals("http://www.w3.org/2002/07/owl#Thing")) {
            this.name = "owl:Thing";
            this.uri = "http://www.w3.org/2002/07/owl#Thing";
        } else {
            this.name = name;
            this.uri = uri;
        }
    }

    @Override
    public String toString() {
        return "OntologyClass{" +
                "name='" + name + '\'' +
                ", uri='" + uri + '\'' +
                '}';
    }
}
