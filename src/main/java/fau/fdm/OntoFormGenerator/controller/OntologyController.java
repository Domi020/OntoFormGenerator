package fau.fdm.OntoFormGenerator.controller;

import fau.fdm.OntoFormGenerator.data.OntologyClass;
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

    public OntologyController(OntologyOverviewService ontologyOverviewService) {
        this.ontologyOverviewService = ontologyOverviewService;
    }

    @RequestMapping(value = "/ontologies/{ontology}", method = RequestMethod.DELETE)
    public ResponseEntity<Boolean> deleteOntology(@PathVariable String ontology) {
        ontologyOverviewService.deleteOntology(ontology);
        return new ResponseEntity<>(true, HttpStatus.OK);
    }

    @RequestMapping(value = "/ontologies", method = RequestMethod.POST)
    public ResponseEntity<Boolean> importOntology(@RequestParam("file") MultipartFile file,
                                                   @RequestParam("ontologyName") String ontologyName) {
        String fileName = UUID.randomUUID() + ".owl";
        File localFile = null;
        try {
            localFile = new File("temp/ontology/" + fileName);
            localFile.getParentFile().mkdirs();
            localFile.createNewFile();
            file.transferTo(localFile.toPath());
            ontologyOverviewService.importOntology(localFile, ontologyName);
            return new ResponseEntity<>(true, org.springframework.http.HttpStatus.OK);
        } catch (Exception e) {
            logger.error("Error importing ontology", e);
            return new ResponseEntity<>(false, HttpStatus.INTERNAL_SERVER_ERROR);
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



}
