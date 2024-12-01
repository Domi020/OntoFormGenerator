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

@ExtendWith(SpringExtension.class)
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        classes = OntoFormGeneratorApplication.class)
@AutoConfigureMockMvc
@TestPropertySource(
        locations = "classpath:application-test.properties")
public class FormEditorServiceTest {

    @Autowired
    private FormEditorService formEditorService;

    @Test
    public void getTargetClassOfFormTest() {
        var form = formEditorService.getTargetClassOfForm("OrderForm");
        Assertions.assertEquals("Order", form.getName());
    }

    @Test
    public void getAllFormElementsOfFormTest() {
        var list = formEditorService.getAllFormElementsOfForm("OrderForm");
        Assertions.assertEquals(2, list.size());
        Assertions.assertEquals("drinks", list.get(0).getName());
        Assertions.assertEquals("special_wishes", list.get(1).getName());
    }
}
