package fau.fdm.OntoFormGenerator.controller;

import fau.fdm.OntoFormGenerator.data.Draft;
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
    private final OntologyValidationService ontologyValidationService;

    public FormController(FormOverviewService formOverviewService, FormEditorService formEditorService,
                          FormFillService formFillService,
                          OntologyOverviewService ontologyOverviewService,
                          OntologyValidationService ontologyValidationService) {
        this.formOverviewService = formOverviewService;
        this.formEditorService = formEditorService;
        this.formFillService = formFillService;
        this.ontologyOverviewService = ontologyOverviewService;
        this.ontologyValidationService = ontologyValidationService;
    }

    @RequestMapping(value = "/api/forms", method = RequestMethod.POST)
    public String addNewForm(@RequestParam("formName") String formName,
                             @RequestParam("ontologyNameInFormCreate") String ontologyName,
                             @RequestParam("ontologyURIInFormCreate") String ontologyURIInFormCreate,
                             @RequestParam("targetClass") String targetClass,
                             Model model) {
        formOverviewService.addNewForm(formName, ontologyName, ontologyURIInFormCreate, targetClass);
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
        try {
            formEditorService.updateForm(formName, form);
        } catch (Exception e) {
            if (e.getMessage().equals("Field names must be unique")) {
                return "error/formedit-samefield-error";
            } else if (e.getMessage().equals("Properties must be unique")) {
                return "error/formedit-sameproperty-error";
            }
            return "error/formedit-generic-error";
        }
        return loadIndexPage(model);
    }

    @RequestMapping(value = "/api/forms/{formName}/fill", method = RequestMethod.POST, consumes =
            "application/json;charset=UTF-8")
    public ResponseEntity<String> fillForm(@PathVariable String formName,
                                    @RequestParam(value = "validate", required = false) String validate,
                                    @RequestParam(value = "draftName", required = false) String draftName,
                                    @RequestBody Map<String, String[]> form) {
        String ontologyName = form.get("ontologyName")[0];
        var individualUri = formFillService.createIndividualFromFilledForm(formName,
                ontologyName, form.get("targetClass")[0],
                form.get("instanceName")[0], draftName, form);
        if (Boolean.parseBoolean(validate)) {
            try {
                var res = ontologyValidationService.validateOntologyWithReasoner(ontologyName);
                if (res.isConsistent()) {
                    return ResponseEntity.ok("Instance was created and validated");
                } else {
                    formFillService.deleteIndividualByIri(ontologyName, individualUri);
                    return ResponseEntity.badRequest().body(res.getReason());
                }
            } catch (RuntimeException e) {
                formFillService.deleteIndividualByIri(ontologyName, individualUri);
                return ResponseEntity.internalServerError().body("An error occurred while validating the ontology.");
            }
        }
        return ResponseEntity.ok("Instance was created");
    }

    @RequestMapping(value = "/api/forms/{formName}/draft", method = RequestMethod.POST,
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public String fillFormDraft(@PathVariable String formName,
                           @RequestParam("firstDraftName") String firstDraftName,
                           @RequestBody Map<String, Object> form,
                           Model model) {
        var normalFields = (Map<String, List<String>>) form.get("normalFields");
        var additionalFields = (Map<String, List<String>>) form.get("additionalFields");
        formFillService.createDraftFromFilledForm(formName,
                normalFields.get("ontologyName").get(0), normalFields.get("instanceName").get(0), firstDraftName,
                normalFields, additionalFields);
        return loadIndexPage(model);
    }

    @RequestMapping(value = "/api/forms/{formName}/draft", method = RequestMethod.DELETE)
    public String deleteFormDraft(@PathVariable String formName,
                                @RequestParam("uri") String draftUri,
                                Model model) {
        formFillService.deleteDraft(formName, draftUri);
        return loadIndexPage(model);
    }


    @RequestMapping(value = "/api/forms/{formName}/individuals", method = RequestMethod.GET)
    public ResponseEntity<List<Individual>> getIndividualsOfForm(@PathVariable String formName) {
        return ResponseEntity.ok(formOverviewService.getAllIndividualsOfForm(formName));
    }

    @RequestMapping(value = "/api/forms/{formName}/drafts", method = RequestMethod.GET)
    public ResponseEntity<List<Draft>> getDraftsOfForm(@PathVariable String formName) {
        return ResponseEntity.ok(formOverviewService.getAllDraftsOfForm(formName));
    }

    @RequestMapping(value = "/api/forms/{formName}/drafts/{individualName}/field/{propertyName}", method = RequestMethod.POST)
    public ResponseEntity<Boolean> addFieldToDraft(@PathVariable String formName, @PathVariable String individualName,
                                                   @PathVariable String propertyName) {
        formFillService.addFieldElementToInstance(formName, individualName, propertyName);
        return ResponseEntity.ok(true);
    }

    @RequestMapping(value = "/api/forms/{formName}", method = RequestMethod.DELETE)
    public String deleteForm(@PathVariable String formName, Model model) {
        formOverviewService.deleteForm(formName);
        return loadIndexPage(model);
    }

    private String loadIndexPage(Model model) {
        model.addAttribute("ontologies", ontologyOverviewService.getImportedOntologies());
        model.addAttribute("forms", formOverviewService.getAllForms());

        return "index";
    }

}
