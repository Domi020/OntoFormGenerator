package fau.fdm.OntoFormGenerator.controller;

import fau.fdm.OntoFormGenerator.service.FormEditorService;
import fau.fdm.OntoFormGenerator.service.FormOverviewService;
import fau.fdm.OntoFormGenerator.service.OntologyContentService;
import fau.fdm.OntoFormGenerator.service.OntologyOverviewService;
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

    Logger logger = LoggerFactory.getLogger(OntologyOverviewService.class);

    private final OntologyOverviewService ontologyOverviewService;
    private final FormOverviewService formOverviewService;
    private final OntologyContentService ontologyContentService;
    private final FormEditorService formEditorService;

    public RestController(OntologyOverviewService ontologyOverviewService,
                          FormOverviewService formOverviewService, OntologyContentService ontologyContentService, FormEditorService formEditorService) {
        this.ontologyOverviewService = ontologyOverviewService;
        this.formOverviewService = formOverviewService;
        this.ontologyContentService = ontologyContentService;
        this.formEditorService = formEditorService;
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
}
