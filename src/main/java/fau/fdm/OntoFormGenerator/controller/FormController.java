package fau.fdm.OntoFormGenerator.controller;

import fau.fdm.OntoFormGenerator.data.Form;
import fau.fdm.OntoFormGenerator.data.Individual;
import fau.fdm.OntoFormGenerator.service.*;
import fau.fdm.OntoFormGenerator.tdb.IndividualService;
import org.springframework.http.HttpStatusCode;
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
    private final IndividualService individualService;

    public FormController(FormOverviewService formOverviewService, FormEditorService formEditorService,
                          FormFillService formFillService, OntologyOverviewService ontologyOverviewService, OntologyContentService ontologyContentService, IndividualService individualService) {
        this.formOverviewService = formOverviewService;
        this.formEditorService = formEditorService;
        this.formFillService = formFillService;
        this.ontologyOverviewService = ontologyOverviewService;
        this.ontologyContentService = ontologyContentService;
        this.individualService = individualService;
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
            "application/json;charset=UTF-8")
    public ResponseEntity<String> fillForm(@PathVariable String formName,
                                    @RequestParam(value = "validate", required = false) String validate,
                                    @RequestBody Map<String, String> form) {
        String ontologyName = form.get("ontologyName");
        var individualUri = formFillService.createIndividualFromFilledForm(formName,
                ontologyName, form.get("targetClass"),
                form.get("instanceName"), form);
        if (Boolean.parseBoolean(validate)) {
            try {
                var res = ontologyContentService.validateOntology(ontologyName);
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
                           @RequestBody Map<String, Object> form,
                           Model model) {
        var normalFields = (Map<String, Object>) form.get("normalFields");
        var additionalFields = (Map<String, Object>) form.get("additionalFields");
        formFillService.createDraftFromFilledForm(formName,
                normalFields.get("ontologyName").toString(), normalFields.get("targetClass").toString(),
                normalFields.get("instanceName").toString(), normalFields, additionalFields);
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

    @RequestMapping(value = "/api/forms/{formName}/drafts/{individualName}/field/{propertyName}", method = RequestMethod.POST)
    public ResponseEntity<Boolean> addFieldToDraft(@PathVariable String formName, @PathVariable String individualName,
                                                   @PathVariable String propertyName) {
        formFillService.addFieldElementToInstance(formName, individualName, propertyName);
        return ResponseEntity.ok(true);
    }

    private String loadIndexPage(Model model) {
        model.addAttribute("ontologies", ontologyOverviewService.getImportedOntologies());
        model.addAttribute("forms", formOverviewService.getAllForms());

        return "index";
    }

}
