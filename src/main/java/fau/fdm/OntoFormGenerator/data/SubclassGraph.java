package fau.fdm.OntoFormGenerator.data;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class SubclassGraph {

    private List<OntologyClass> classes = new ArrayList<>();

    private List<SubclassRelation> edges = new ArrayList<>();

    public boolean addClass(OntologyClass c) {
        if (classes.contains(c)) {
            return false;
        }
        classes.add(c);
        return true;
    }

    public boolean addEdge(SubclassRelation e) {
        if (!classes.contains(e.getSuperClass())) {
            addClass(e.getSuperClass());
        }
        if (!classes.contains(e.getSubClass())) {
            addClass(e.getSubClass());
        }
        edges.add(e);
        return true;
    }
}
