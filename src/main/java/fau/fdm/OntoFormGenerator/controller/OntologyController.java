package fau.fdm.OntoFormGenerator.controller;

import fau.fdm.OntoFormGenerator.service.OntologyOverviewService;
import org.apache.commons.io.FileUtils;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.UUID;

@Controller
public class OntologyController {

    private OntologyOverviewService ontologyOverviewService;

    public OntologyController(OntologyOverviewService ontologyOverviewService) {
        this.ontologyOverviewService = ontologyOverviewService;
    }
    /*TODO
     * 1. Save file to disk
     * 2. Parse file as OWL ontology
     * 3. Save ontology to database
     */
    @RequestMapping(value = "/ontology", method = RequestMethod.POST)
    public boolean importOntology(@RequestParam("file") MultipartFile file) {
        String fileName = UUID.randomUUID() + ".owl";
        File localFile = null;
        try {
            localFile = new File("temp/ontology/" + fileName);
            file.transferTo(localFile);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        } finally {
            if (localFile != null) {
                FileUtils.deleteQuietly(localFile);
            }
        }
    }
}
