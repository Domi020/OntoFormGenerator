package fau.fdm.OntoFormGenerator.controller;

import fau.fdm.OntoFormGenerator.data.Form;
import fau.fdm.OntoFormGenerator.data.Individual;
import fau.fdm.OntoFormGenerator.service.*;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Controller
public class FormController {

    private final FormOverviewService formOverviewService;

    private final FormEditorService formEditorService;

    private final FormFillService formFillService;

    private final OntologyOverviewService ontologyOverviewService;
    private final OntologyContentService ontologyContentService;

    public FormController(FormOverviewService formOverviewService, FormEditorService formEditorService,
                          FormFillService formFillService, OntologyOverviewService ontologyOverviewService, OntologyContentService ontologyContentService) {
        this.formOverviewService = formOverviewService;
        this.formEditorService = formEditorService;
        this.formFillService = formFillService;
        this.ontologyOverviewService = ontologyOverviewService;
        this.ontologyContentService = ontologyContentService;
    }

    @RequestMapping(value = "/api/forms", method = RequestMethod.POST)
    public String addNewForm(@RequestParam("formName") String formName,
                             @RequestParam("ontologyNameInFormCreate") String ontologyName,
                             @RequestParam("ontologyURIInFormCreate") String ontologyURIInFormCreate,
                             Model model) {
        formOverviewService.addNewForm(formName, ontologyName, ontologyURIInFormCreate);
        return loadIndexPage(model);
    }

    @RequestMapping(value = "/api/forms", method = RequestMethod.GET)
    public ResponseEntity<List<Form>> getFormsWithTargetClass(@RequestParam("ontologyName") String ontologyName,
                                                              @RequestParam("targetClass") String targetClass) {
        return ResponseEntity.ok(formOverviewService.getFormsWithTargetClass(ontologyName, targetClass));
    }

    @RequestMapping(value = "/api/forms/{formName}", method = RequestMethod.POST, consumes =
            MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public String updateCreatedForm(@PathVariable String formName,
                                    @RequestBody MultiValueMap<String, String> form,
                                    Model model) {
        formEditorService.updateForm(formName, form);
        return loadIndexPage(model);
    }

    @RequestMapping(value = "/api/forms/{formName}/fill", method = RequestMethod.POST, consumes =
            MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public String fillForm(@PathVariable String formName,
                                    @RequestBody MultiValueMap<String, String> form,
                                    Model model) {
        formFillService.createIndividualFromFilledForm(formName,
                form.getFirst("ontologyName"), form.getFirst("targetClass"),
                form.getFirst("instanceName"), form);
        return loadIndexPage(model);
    }

    @RequestMapping(value = "/api/forms/{formName}/draft", method = RequestMethod.POST,
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public String fillFormDraft(@PathVariable String formName,
                           @RequestBody Map<String, Object> form,
                           Model model) {
        formFillService.createDraftFromFilledForm(formName,
                form.get("ontologyName").toString(), form.get("targetClass").toString(),
                form.get("instanceName").toString(), form);
        return loadIndexPage(model);
    }

    @RequestMapping(value = "/api/ontologies/{ontologyName}/individuals/{individualName}", method = RequestMethod.POST)
    public String editIndividual(@PathVariable String ontologyName, @PathVariable String individualName,
                                 @RequestBody MultiValueMap<String, String> form, Model model) {
        ontologyContentService.editIndividual(ontologyName, individualName, form);
        return loadIndexPage(model);
    }

    @RequestMapping(value = "/api/forms/{formName}/individuals", method = RequestMethod.GET)
    public ResponseEntity<List<Individual>> getIndividualsOfForm(@PathVariable String formName) {
        return ResponseEntity.ok(formOverviewService.getAllIndividualsOfForm(formName));
    }

    @RequestMapping(value = "/api/forms/{formName}/drafts", method = RequestMethod.GET)
    public ResponseEntity<List<Individual>> getDraftsOfForm(@PathVariable String formName) {
        return ResponseEntity.ok(formOverviewService.getAllDraftsOfForm(formName));
    }

    private String loadIndexPage(Model model) {
        model.addAttribute("ontologies", ontologyOverviewService.getImportedOntologies());
        model.addAttribute("forms", formOverviewService.getAllForms());

        return "index";
    }

}
