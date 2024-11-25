package fau.fdm.OntoFormGenerator.service;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import fau.fdm.OntoFormGenerator.OntoFormGeneratorApplication;
import org.assertj.core.api.AssertJProxySetup;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;

import java.io.File;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(SpringExtension.class)
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        classes = OntoFormGeneratorApplication.class)
@AutoConfigureMockMvc
@TestPropertySource(
        locations = "classpath:application-test.properties")
public class OntologyOverviewServiceTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private OntologyOverviewService ontologyOverviewService;

    @Test
    public void getImportedOntologiesTest() {
        var list = ontologyOverviewService.getImportedOntologies();
        Assertions.assertEquals(1, list.size());
        Assertions.assertEquals("restaurantOnt",
                list.get(0).getName());
    }
}
