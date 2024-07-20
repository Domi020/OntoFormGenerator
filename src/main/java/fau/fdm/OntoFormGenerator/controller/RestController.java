package fau.fdm.OntoFormGenerator.controller;

import fau.fdm.OntoFormGenerator.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class RestController {

    private final FormFillService formFillService;
    Logger logger = LoggerFactory.getLogger(OntologyOverviewService.class);

    private final OntologyOverviewService ontologyOverviewService;
    private final FormOverviewService formOverviewService;
    private final OntologyContentService ontologyContentService;
    private final FormEditorService formEditorService;

    public RestController(OntologyOverviewService ontologyOverviewService,
                          FormOverviewService formOverviewService, OntologyContentService ontologyContentService, FormEditorService formEditorService, FormFillService formFillService) {
        this.ontologyOverviewService = ontologyOverviewService;
        this.formOverviewService = formOverviewService;
        this.ontologyContentService = ontologyContentService;
        this.formEditorService = formEditorService;
        this.formFillService = formFillService;
    }

    @RequestMapping(value = "/", method = RequestMethod.GET)
    public String loadMainPage(Model model) {

        model.addAttribute("ontologies", ontologyOverviewService.getImportedOntologies());
        model.addAttribute("forms", formOverviewService.getAllForms());

        return "index";
    }

    @RequestMapping(value = "/editor/{form}", method = RequestMethod.GET)
    public String loadEditorPage(Model model, @PathVariable String form, @RequestParam("ontology") String ontology) {
        model.addAttribute("form", form);
        model.addAttribute("ontology", ontology);
        model.addAttribute("ontologyClasses", ontologyContentService.getAllClassesOfOntology(ontology));

        model.addAttribute("targetClass", formEditorService.getSelectedEditorClass(form));
        model.addAttribute("formElements", formEditorService.getAllFormElementsOfForm(form));


        return "editor";
    }
    //TODO: ReloadProperties => löscht alle Property Felder...und macht Form kaputt

    @RequestMapping(value = "/fill/{form}", method = RequestMethod.GET)
    public String loadFilloutPage(Model model, @PathVariable String form, @RequestParam("ontology") String ontology) {
        model.addAttribute("form", form);
        model.addAttribute("ontology", ontology);
        model.addAttribute("ontologyClasses", ontologyContentService.getAllClassesOfOntology(ontology));

        model.addAttribute("targetClass", formEditorService.getSelectedEditorClass(form));
        model.addAttribute("formElements", formEditorService.getAllFormElementsOfForm(form));

        return "formfill";
    }

    @RequestMapping(value = "/fill/{form}/draft/{individualName}", method = RequestMethod.GET)
    public String loadFilloutPageWithDraft(Model model, @PathVariable String form, @PathVariable String individualName) {
        model.addAttribute("form", form);
        var ontology = formOverviewService.getOntologyOfForm(form);
        model.addAttribute("ontology", ontology.getName());
        model.addAttribute("ontologyClasses", ontologyContentService.getAllClassesOfOntology(ontology.getName()));

        model.addAttribute("targetClass", formEditorService.getSelectedEditorClass(form));
        model.addAttribute("formElements", formEditorService.getAllFormElementsOfForm(form));

        model.addAttribute("setElements", formFillService.getSetFieldsByDraft(form, individualName,
                ontology.getName()));
        model.addAttribute("individualName", individualName);

        return "formfill";
    }

    @RequestMapping(value = "/edit/forms/{formName}/individuals/{individualName}", method = RequestMethod.GET)
    public String editIndividual(@PathVariable String formName, @PathVariable String individualName, Model model) {
        var ontology = formOverviewService.getOntologyOfForm(formName);
        var individual = ontologyContentService.getIndividualByString(individualName,
                ontology.getName());
        model.addAttribute("form", formName);
        model.addAttribute("ontology", ontology);
        model.addAttribute("individual", individual);
        model.addAttribute("classProperties", ontologyContentService.getAllPropertiesOfDomain(ontology.getName(),
                individual.getOntologyClass().getName()));
        // model.addAttribute("formElements", formEditorService.getAllFormElementsOfForm(formName));
        model.addAttribute("individualProperties", ontologyContentService.getAllSetPropertiesByIndividual(
                individualName, ontology.getName()));
        return "individual-edit";
    }
}
