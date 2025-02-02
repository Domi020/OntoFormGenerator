package fau.fdm.OntoFormGenerator.controller;

import fau.fdm.OntoFormGenerator.data.FormField;
import fau.fdm.OntoFormGenerator.service.*;
import fau.fdm.OntoFormGenerator.tdb.GeneralTDBService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.ArrayList;

@Controller
public class RestController {

    private final FormFillService formFillService;
    private final OntologyConstraintService ontologyConstraintService;
    private final GeneralTDBService generalTDBService;
    Logger logger = LoggerFactory.getLogger(OntologyOverviewService.class);

    private final OntologyOverviewService ontologyOverviewService;
    private final FormOverviewService formOverviewService;
    private final OntologyContentService ontologyContentService;
    private final FormEditorService formEditorService;

    public RestController(OntologyOverviewService ontologyOverviewService,
                          FormOverviewService formOverviewService, OntologyContentService ontologyContentService, FormEditorService formEditorService, FormFillService formFillService, OntologyConstraintService ontologyConstraintService, GeneralTDBService generalTDBService) {
        this.ontologyOverviewService = ontologyOverviewService;
        this.formOverviewService = formOverviewService;
        this.ontologyContentService = ontologyContentService;
        this.formEditorService = formEditorService;
        this.formFillService = formFillService;
        this.ontologyConstraintService = ontologyConstraintService;
        this.generalTDBService = generalTDBService;
    }

    @RequestMapping(value = "/", method = RequestMethod.GET)
    public String loadMainPage(Model model) {

        model.addAttribute("ontologies", ontologyOverviewService.getImportedOntologies());
        var allForms = formOverviewService.getAllForms();
        model.addAttribute("forms", allForms);
        model.addAttribute("formDrafts", formOverviewService.getAllDraftsOfForms(allForms));

        return "index";
    }

    @RequestMapping(value = "/editor/{form}", method = RequestMethod.GET)
    public String loadEditorPage(Model model, @PathVariable String form, @RequestParam("ontology") String ontology) {
        var targetClass = formEditorService.getTargetClassOfForm(form);
        model.addAttribute("form", form);
        model.addAttribute("ontology", ontology);
        model.addAttribute("ontologyClasses", ontologyContentService.getAllClassesOfOntology(ontology));

        model.addAttribute("targetClass", targetClass);
        model.addAttribute("formElements", formEditorService.getAllFormElementsOfForm(form));

        model.addAttribute("constraints", ontologyConstraintService.getConstraints(ontology,
                targetClass.getUri(), null));

        return "editor";
    }

    @RequestMapping(value = "/fill/{form}", method = RequestMethod.GET)
    public String loadFilloutPage(Model model, @PathVariable String form, @RequestParam("ontology") String ontology) {
        model.addAttribute("form", form);
        model.addAttribute("ontology", ontology);
        model.addAttribute("ontologyClasses", ontologyContentService.getAllClassesOfOntology(ontology));

        model.addAttribute("targetClass", formEditorService.getTargetClassOfForm(form));
        model.addAttribute("formElements", formEditorService.getAllFormElementsOfForm(form));
        model.addAttribute("additionalElements", new ArrayList<FormField>());

        model.addAttribute("draftName", null);

        return "formfill";
    }

    @RequestMapping(value = "/fill/{form}/draft/{individualName}", method = RequestMethod.GET)
    public String loadFilloutPageWithDraft(Model model, @PathVariable String form, @PathVariable String individualName) {
        // formElements => all fields from original form
        // additionalElements => all additional set fields for this draft
        // setElements => all values
        model.addAttribute("form", form);
        var ontology = formOverviewService.getOntologyOfForm(form);
        model.addAttribute("ontology", ontology.getName());
        model.addAttribute("ontologyClasses", ontologyContentService.getAllClassesOfOntology(ontology.getName()));

        model.addAttribute("targetClass", formEditorService.getTargetClassOfForm(form));
        model.addAttribute("formElements", formEditorService.getAllFormElementsOfForm(form));
        model.addAttribute("additionalElements", formFillService.getAllAdditionalFormElementsOfDraft(form,
                ontology.getName(), individualName));

        model.addAttribute("setElements", formFillService.getSetFieldsByDraft(form, individualName,
                ontology.getName()));
        model.addAttribute("individualName", formFillService.getCurrentDraftName(individualName));

        model.addAttribute("draftName", individualName);

        return "formfill";
    }

    @RequestMapping(value = "/edit/ontologies/{ontologyName}/individuals/{individualName}", method = RequestMethod.GET)
    public String editIndividual(@PathVariable String ontologyName, @PathVariable String individualName, Model model) {
        var ontology = ontologyOverviewService.getOntologyByName(ontologyName);
        var individual = ontologyContentService.getIndividualByString(individualName,
                ontology.getName());
        // model.addAttribute("form", formName);
        model.addAttribute("ontology", ontology);
        model.addAttribute("individual", individual);
        model.addAttribute("classProperties", ontologyContentService.getAllPropertiesOfDomain(ontology.getName(),
                individual.getOntologyClass().getUri()));
        // model.addAttribute("formElements", formEditorService.getAllFormElementsOfForm(formName));
        model.addAttribute("individualProperties", ontologyContentService.getAllSetPropertiesByIndividual(
                individual.getIri(), ontology.getName()));
        return "individual-edit";
    }

    @RequestMapping(value = "/view/ontologies/{ontologyName}", method = RequestMethod.GET)
    public String loadClassViewer(@PathVariable String ontologyName,
                                  Model model) {
        model.addAttribute("ontology", ontologyOverviewService.getOntologyByName(ontologyName));
        model.addAttribute("subclassGraph", ontologyContentService.buildSubclassGraph(ontologyName));
        return "class-viewer";
    }

    @RequestMapping(value = "/restart", method = RequestMethod.POST)
    public String restart(Model model,
                          @RequestParam(value = "deleteDB", required = false) boolean deleteDB) {
        generalTDBService.restart(deleteDB);
        return loadMainPage(model);
    }
}
