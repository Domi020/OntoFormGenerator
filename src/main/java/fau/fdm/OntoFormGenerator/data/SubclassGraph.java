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

    private final OntologyClass owlThing = new OntologyClass("owl:Thing", "http://www.w3.org/2002/07/owl#Thing");

    public SubclassGraph() {
        addClass(owlThing);
    }

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

    public void addEdgesToOwlThing() {
        for (var ontClass : classes) {
            if (!ontClass.equals(owlThing) && !checkIfClassHasSuperclass(ontClass)) {
                addEdge(new SubclassRelation(classes.get(0), ontClass));
            }
        }
    }

    private boolean checkIfClassHasSuperclass(OntologyClass ontClass) {
        for (var rel : edges) {
            if (rel.getSubClass().equals(ontClass)) {
                return true;
            }
        }
        return false;
    }
}
