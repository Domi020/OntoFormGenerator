package fau.fdm.OntoFormGenerator.controller;

import fau.fdm.OntoFormGenerator.service.OntologyOverviewService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

@Controller
public class RestController {

    Logger logger = LoggerFactory.getLogger(OntologyOverviewService.class);

    private final OntologyOverviewService ontologyOverviewService;

    public RestController(OntologyOverviewService ontologyOverviewService) {
        this.ontologyOverviewService = ontologyOverviewService;
    }

    @RequestMapping(value = "/", method = RequestMethod.GET)
    public String loadMainPage(Model model) {

        model.addAttribute("ontologies", ontologyOverviewService.getNamesOfImportedOntologies());

        return "index";
    }
}
