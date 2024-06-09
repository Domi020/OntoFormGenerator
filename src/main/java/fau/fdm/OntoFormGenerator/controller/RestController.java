package fau.fdm.OntoFormGenerator.controller;

import fau.fdm.OntoFormGenerator.service.FormOverviewService;
import fau.fdm.OntoFormGenerator.service.OntologyOverviewService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

@Controller
public class RestController {

    Logger logger = LoggerFactory.getLogger(OntologyOverviewService.class);

    private final OntologyOverviewService ontologyOverviewService;
    private final FormOverviewService formOverviewService;

    public RestController(OntologyOverviewService ontologyOverviewService,
                          FormOverviewService formOverviewService) {
        this.ontologyOverviewService = ontologyOverviewService;
        this.formOverviewService = formOverviewService;
    }

    @RequestMapping(value = "/", method = RequestMethod.GET)
    public String loadMainPage(Model model) {

        model.addAttribute("ontologies", ontologyOverviewService.getNamesOfImportedOntologies());
        model.addAttribute("forms", formOverviewService.getAllForms());

        return "index";
    }

    @RequestMapping(value = "/editor/{ontology}", method = RequestMethod.GET)
    public String loadEditorPage(Model model, @PathVariable String ontology) {
        model.addAttribute("ontology", ontology);
        return "editor";
    }
}
