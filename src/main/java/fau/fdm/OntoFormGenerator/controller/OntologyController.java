package fau.fdm.OntoFormGenerator.controller;

import fau.fdm.OntoFormGenerator.service.OntologyOverviewService;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.UUID;

@Controller
public class OntologyController {

    Logger logger = LoggerFactory.getLogger(OntologyOverviewService.class);

    private final OntologyOverviewService ontologyOverviewService;

    public OntologyController(OntologyOverviewService ontologyOverviewService) {
        this.ontologyOverviewService = ontologyOverviewService;
    }

    @RequestMapping(value = "/ontology", method = RequestMethod.POST)
    public boolean importOntology(@RequestParam("file") MultipartFile file,
                                  @RequestParam("ontologyName") String ontologyName) {
        String fileName = UUID.randomUUID() + ".owl";
        File localFile = null;
        try {
            localFile = new File("temp/ontology/" + fileName);
            localFile.getParentFile().mkdirs();
            localFile.createNewFile();
            file.transferTo(localFile);
            ontologyOverviewService.importOntology(localFile, ontologyName);
            return true;
        } catch (Exception e) {
            logger.error("Error importing ontology", e);
            return false;
        } finally {
            if (localFile != null) {
                FileUtils.deleteQuietly(localFile);
            }
        }
    }
}
