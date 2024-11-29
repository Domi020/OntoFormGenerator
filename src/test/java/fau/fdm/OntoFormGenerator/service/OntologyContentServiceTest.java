package fau.fdm.OntoFormGenerator.service;

import fau.fdm.OntoFormGenerator.OntoFormGeneratorApplication;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;

@ExtendWith(SpringExtension.class)
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        classes = OntoFormGeneratorApplication.class)
@AutoConfigureMockMvc
@TestPropertySource(
        locations = "classpath:application-test.properties")
public class OntologyContentServiceTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private OntologyContentService ontologyContentService;

    @Test
    public void getAllClassesOfOntologyTest() {
        var list = ontologyContentService.getAllClassesOfOntology("restaurantOnt");
        Assertions.assertEquals(493, list.size());
    }

    @Test
    public void getAllPropertiesOfDomainTest() {
        var list = ontologyContentService.getAllPropertiesOfDomain("restaurantOnt",
                "http://ontologies.ontoformgenerator.de/MyRestaurant#Order");
        Assertions.assertEquals(12, list.size());
        Assertions.assertEquals("locatedIn", list.get(0).getName());
    }

    @Test
    public void getAllIndividualsOfOntologyTest() {
        var list = ontologyContentService.getAllIndividualsOfOntology("restaurantOnt");
        Assertions.assertEquals(207, list.size());
    }

    @Test
    public void getAllIndividualsOfClassTest() {
        var list = ontologyContentService.getAllIndividualsOfClass("restaurantOnt",
                "http://www.w3.org/TR/2003/PR-owl-guide-20031209/wine#Chardonnay");
        Assertions.assertEquals(8, list.size());
        for (var individual : list) {
            Assertions.assertTrue(individual.isImported());
        }
    }

    @Test
    public void getIndividualByStringTest() {
        var individual = ontologyContentService.getIndividualByString("CotesDOrRegion",
                "restaurantOnt");
        Assertions.assertNotNull(individual);
        Assertions.assertEquals("http://www.w3.org/TR/2003/PR-owl-guide-20031209/wine#CotesDOrRegion",
                individual.getIri());
    }

    @Test
    public void getIndividualByString_NotFoundTest() {
        var individual = ontologyContentService.getIndividualByString("NOTAVAILABLE",
                "restaurantOnt");
        Assertions.assertNull(individual);
    }

    @Test
    public void buildSubclassGraphTest() {
        var graph = ontologyContentService.buildSubclassGraph("restaurantOnt");
        Assertions.assertEquals(111, graph.getEdges().size());
    }

    @Test
    public void queryPropertiesTest_foundSomething() {
        var list = ontologyContentService.queryProperties(
                "restaurantOnt",
                "http://ontologies.ontoformgenerator.de/MyRestaurant#Order",
                "hasOrdered");
        Assertions.assertEquals(2, list.size());
    }

    @Test
    public void queryPropertiesTest_foundSomethingSubstring() {
        var list = ontologyContentService.queryProperties(
                "restaurantOnt",
                "http://ontologies.ontoformgenerator.de/MyRestaurant#Order",
                "Ordered");
        Assertions.assertEquals(2, list.size());
    }

    @Test
    public void queryPropertiesTest_foundSomethingWithRdfsComment() {
        var list = ontologyContentService.queryProperties(
                "restaurantOnt",
                "http://ontologies.ontoformgenerator.de/MyRestaurant#Order",
                "alternate ingredients");
        Assertions.assertEquals(1, list.size());
        Assertions.assertEquals("hasSpecialWish",
                list.get(0).getName());
    }

    @Test
    public void queryPropertiesTest_empty() {
        var list = ontologyContentService.queryProperties(
                "restaurantOnt",
                "http://ontologies.ontoformgenerator.de/MyRestaurant#Order",
                "hasD");
        Assertions.assertEquals(0, list.size());
    }
}
