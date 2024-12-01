package fau.fdm.OntoFormGenerator.service;

import fau.fdm.OntoFormGenerator.OntoFormGeneratorApplication;
import fau.fdm.OntoFormGenerator.data.Constraint;
import fau.fdm.OntoFormGenerator.data.Individual;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;

@ExtendWith(SpringExtension.class)
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        classes = OntoFormGeneratorApplication.class)
@AutoConfigureMockMvc
@TestPropertySource(
        locations = "classpath:application-test.properties")
public class OntologyConstraintServiceTest {
    @Autowired
    private MockMvc mvc;

    @Autowired
    private OntologyConstraintService ontologyConstraintService;

    @Test
    public void getConstraintsTest_noConstraint() {
        var list = ontologyConstraintService.getConstraints("restaurantOnt",
                "http://ontologies.ontoformgenerator.de/MyRestaurant#Order",
                "http://ontologies.ontoformgenerator.de/MyRestaurant#hasSpecialWish");
        Assertions.assertEquals(0, list.size());
    }

    @Test
    public void getConstraintsTest_onlyConstraint() {
        var list = ontologyConstraintService.getConstraints("restaurantOnt",
                "http://www.w3.org/TR/2003/PR-owl-guide-20031209/wine#Chardonnay",
                "http://www.w3.org/TR/2003/PR-owl-guide-20031209/wine#hasFlavor");
        Assertions.assertEquals(1, list.size());
        Assertions.assertNull(list.get(0).getValue());
        Assertions.assertEquals(Constraint.ConstraintType.ONLY,
                list.get(0).getConstraintType());
    }

    @Test
    public void getConstraintsTest_cardinalityConstraint() {
        var list = ontologyConstraintService.getConstraints("restaurantOnt",
                "http://www.w3.org/TR/2003/PR-owl-guide-20031209/food#Meal",
                "http://www.w3.org/TR/2003/PR-owl-guide-20031209/food#course");
        Assertions.assertEquals(2, list.size());
        Assertions.assertEquals(1, list.get(1).getValue());
        Assertions.assertEquals(Constraint.ConstraintType.MIN,
                list.get(1).getConstraintType());
    }

    @Test
    public void filterForAllValuesFromIndividualsTest() {
        var individualList = new ArrayList<Individual>();
        individualList.add(new Individual("Full", "Full", "http://www.w3.org/TR/2003/PR-owl-guide-20031209/wine#Full",
                null, true));
        individualList.add(new Individual("Medium", "Medium", "http://www.w3.org/TR/2003/PR-owl-guide-20031209/wine#Medium",
                null, true));
        individualList.add(new Individual("Light", "Light", "http://www.w3.org/TR/2003/PR-owl-guide-20031209/wine#Light",
                null, true));

        var filteredList = ontologyConstraintService.filterForAllValuesFromIndividuals(
                individualList, "restaurantOnt",
                "http://www.w3.org/TR/2003/PR-owl-guide-20031209/wine#Chardonnay",
                "http://www.w3.org/TR/2003/PR-owl-guide-20031209/wine#hasBody"
        );

        Assertions.assertEquals(2, filteredList.size());
    }
}
