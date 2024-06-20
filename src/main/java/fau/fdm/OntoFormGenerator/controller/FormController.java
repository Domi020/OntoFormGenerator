package fau.fdm.OntoFormGenerator.controller;

import fau.fdm.OntoFormGenerator.service.FormEditorService;
import fau.fdm.OntoFormGenerator.service.FormOverviewService;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@Controller
public class FormController {

    private final FormOverviewService formOverviewService;

    private final FormEditorService formEditorService;

    public FormController(FormOverviewService formOverviewService, FormEditorService formEditorService) {
        this.formOverviewService = formOverviewService;
        this.formEditorService = formEditorService;
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

}
