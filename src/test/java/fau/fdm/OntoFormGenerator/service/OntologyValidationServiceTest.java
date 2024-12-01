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
public class OntologyValidationServiceTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private OntologyValidationService ontologyValidationService;

    @Test
    public void checkNamingTest_valid() {
        var result = ontologyValidationService.checkNaming("myNewProp");
        Assertions.assertTrue(result.isValid());
    }

    @Test
    public void checkNamingTest_invalid() {
        var result = ontologyValidationService.checkNaming("propertyOneAndTwo");
        Assertions.assertFalse(result.isValid());
        Assertions.assertEquals("And", result.getFilteredWord());
        Assertions.assertEquals("propertyOneAndTwo", result.getNewPropertyName());
    }

    @Test
    public void checkIfURIIsUsedTest_used() {
        var result = ontologyValidationService.checkIfURIisUsed("restaurantOnt",
                "http://ontologies.ontoformgenerator.de/MyRestaurant#Order");
        Assertions.assertTrue(result);
    }

    @Test
    public void checkIfURIIsUsedTest_unused() {
        var result = ontologyValidationService.checkIfURIisUsed("restaurantOnt",
                "http://ontologies.ontoformgenerator.de/MyRestaurant#Orderx");
        Assertions.assertFalse(result);
    }

    @Test
    public void checkNamingSchemaTest_valid() {
        var result = ontologyValidationService.checkNamingSchema("restaurantOnt",
                "isCorrectProperty");
        Assertions.assertTrue(result.isValid());
        Assertions.assertEquals(OntologyValidationService.NamingSchema.CAMEL_CASE, result.getOntologyNamingSchema());
    }

    @Test
    public void checkNamingSchemaTest_invalid() {
        var result = ontologyValidationService.checkNamingSchema("restaurantOnt",
                "ALLUPPERCASE");
        Assertions.assertFalse(result.isValid());
        Assertions.assertEquals(OntologyValidationService.NamingSchema.ALL_CAPS, result.getNewPropertyNamingSchema());
    }

}
