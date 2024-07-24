package fau.fdm.OntoFormGenerator.controller;

import fau.fdm.OntoFormGenerator.data.Individual;
import fau.fdm.OntoFormGenerator.data.OntologyClass;
import fau.fdm.OntoFormGenerator.data.OntologyProperty;
import fau.fdm.OntoFormGenerator.service.FormOverviewService;
import fau.fdm.OntoFormGenerator.service.OntologyContentService;
import fau.fdm.OntoFormGenerator.service.OntologyOverviewService;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.List;
import java.util.UUID;

@Controller
public class OntologyController {

    Logger logger = LoggerFactory.getLogger(OntologyOverviewService.class);

    private final OntologyOverviewService ontologyOverviewService;

    private final OntologyContentService ontologyContentService;

    private final FormOverviewService formOverviewService;

    public OntologyController(OntologyOverviewService ontologyOverviewService, OntologyContentService ontologyContentService, FormOverviewService formOverviewService) {
        this.ontologyOverviewService = ontologyOverviewService;
        this.ontologyContentService = ontologyContentService;
        this.formOverviewService = formOverviewService;
    }

    @RequestMapping(value = "/ontologies/{ontology}", method = RequestMethod.DELETE)
    public String deleteOntology(@PathVariable String ontology,
                                 Model model) {
        ontologyOverviewService.deleteOntology(ontology);
        return loadIndexPage(model);
    }

    @RequestMapping(value = "/ontologies", method = RequestMethod.POST)
    public String importOntology(@RequestParam("file") MultipartFile file,
                                                   @RequestParam("ontologyName") String ontologyName,
                                                    Model model) {
        String fileName = UUID.randomUUID() + ".owl";
        File localFile = null;
        try {
            localFile = new File("temp/ontology/" + fileName);
            localFile.getParentFile().mkdirs();
            localFile.createNewFile();
            file.transferTo(localFile.toPath());
            ontologyOverviewService.importOntology(localFile, ontologyName);
            return loadIndexPage(model);
        } catch (Exception e) {
            logger.error("Error importing ontology", e);
            return loadIndexPage(model);
        } finally {
            if (localFile != null) {
                FileUtils.deleteQuietly(localFile);
            }
        }
    }

    @RequestMapping(value = "/ontologies/{ontologyName}", method = RequestMethod.GET)
    public ResponseEntity<Resource> downloadOntology(@PathVariable String ontologyName) {
        var file = ontologyOverviewService.downloadOntology(ontologyName);
        return ResponseEntity.ok()
                .contentLength(file.contentLength())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header("Content-Disposition", "attachment; filename=" + ontologyName + ".owl")
                .body(file);

    }

    @RequestMapping(value = "/api/ontologies/{ontologyName}/classes/{className}/properties", method = RequestMethod.GET)
    public ResponseEntity<List<OntologyProperty>> getAllPropertiesFromDomain(@PathVariable String ontologyName,
                                                                             @PathVariable String className) {
        return new ResponseEntity<>(ontologyContentService.getAllPropertiesOfDomain(ontologyName, className),
                HttpStatus.OK);
    }

    @RequestMapping(value = "/api/ontologies/{ontologyName}/classes/{className}/individuals", method = RequestMethod.GET)
    public ResponseEntity<List<Individual>> getAllIndividualsFromClass(@PathVariable String ontologyName,
                                                                      @PathVariable String className) {
        return new ResponseEntity<>(ontologyContentService.getAllIndividualsOfClass(ontologyName, className),
                HttpStatus.OK);
    }

    @RequestMapping(value = "/api/ontologies/{ontologyName}/individuals", method = RequestMethod.GET)
    public ResponseEntity<List<Individual>> getAllIndividualsOfOntology(@PathVariable String ontologyName) {
        return new ResponseEntity<>(ontologyContentService.getAllIndividualsOfOntology(ontologyName),
                HttpStatus.OK);
    }

    private String loadIndexPage(Model model) {
        model.addAttribute("ontologies", ontologyOverviewService.getImportedOntologies());
        model.addAttribute("forms", formOverviewService.getAllForms());

        return "index";
    }

}
