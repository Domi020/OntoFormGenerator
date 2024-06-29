package fau.fdm.OntoFormGenerator.controller;

import fau.fdm.OntoFormGenerator.service.FormEditorService;
import fau.fdm.OntoFormGenerator.service.FormFillService;
import fau.fdm.OntoFormGenerator.service.FormOverviewService;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@Controller
public class FormController {

    private final FormOverviewService formOverviewService;

    private final FormEditorService formEditorService;

    private final FormFillService formFillService;

    public FormController(FormOverviewService formOverviewService, FormEditorService formEditorService,
                          FormFillService formFillService) {
        this.formOverviewService = formOverviewService;
        this.formEditorService = formEditorService;
        this.formFillService = formFillService;
    }

    @RequestMapping(value = "/api/forms", method = RequestMethod.POST)
    public String addNewForm(@RequestParam("formName") String formName,
                             @RequestParam("ontologyNameInFormCreate") String ontologyName,
                             @RequestParam("ontologyURIInFormCreate") String ontologyURIInFormCreate) {
        formOverviewService.addNewForm(formName, ontologyName, ontologyURIInFormCreate);
        return "index";
    }

    @RequestMapping(value = "/api/forms/{formName}", method = RequestMethod.POST, consumes =
            MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public String updateCreatedForm(@PathVariable String formName,
                                    @RequestBody MultiValueMap<String, String> form) {
        formEditorService.updateForm(formName, form);
        return "index";
    }

    @RequestMapping(value = "/api/forms/{formName}/fill", method = RequestMethod.POST, consumes =
            MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public String fillForm(@PathVariable String formName,
                                    @RequestBody MultiValueMap<String, String> form) {
        formFillService.createIndividualFromFilledForm(formName,
                form.getFirst("ontologyName"), form.getFirst("targetClass"),
                form.getFirst("instanceName"), form);
        return "index";
    }

}
