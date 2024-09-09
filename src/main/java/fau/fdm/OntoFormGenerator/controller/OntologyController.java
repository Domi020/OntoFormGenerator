package fau.fdm.OntoFormGenerator.controller;

import fau.fdm.OntoFormGenerator.data.Individual;
import fau.fdm.OntoFormGenerator.data.OntologyClass;
import fau.fdm.OntoFormGenerator.data.OntologyProperty;
import fau.fdm.OntoFormGenerator.data.SubclassGraph;
import fau.fdm.OntoFormGenerator.exception.SimilarPropertiesExistException;
import fau.fdm.OntoFormGenerator.service.FormFillService;
import fau.fdm.OntoFormGenerator.service.FormOverviewService;
import fau.fdm.OntoFormGenerator.service.OntologyContentService;
import fau.fdm.OntoFormGenerator.service.OntologyOverviewService;
import fau.fdm.OntoFormGenerator.tdb.PropertyService;
import org.apache.commons.io.FileUtils;
import org.apache.jena.ontology.OntModelSpec;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.reasoner.ValidityReport;
import org.apache.jena.tdb2.TDB2Factory;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Controller
public class OntologyController {

    private final FormFillService formFillService;
    Logger logger = LoggerFactory.getLogger(OntologyOverviewService.class);

    private final OntologyOverviewService ontologyOverviewService;

    private final OntologyContentService ontologyContentService;

    private final FormOverviewService formOverviewService;

    @Value("${ontoformgenerator.ontologyDirectory}")
    private String ontologyDirectory;

    public OntologyController(OntologyOverviewService ontologyOverviewService, OntologyContentService ontologyContentService, FormOverviewService formOverviewService, FormFillService formFillService) {
        this.ontologyOverviewService = ontologyOverviewService;
        this.ontologyContentService = ontologyContentService;
        this.formOverviewService = formOverviewService;
        this.formFillService = formFillService;
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
                                                                             @PathVariable String className,
                                            @RequestParam(value = "query", required = false) String query) {
        if (query != null) {
            return new ResponseEntity<>(ontologyContentService.queryProperties(ontologyName, className, query),
                    HttpStatus.OK);
        } else {
            return new ResponseEntity<>(ontologyContentService.getAllPropertiesOfDomain(ontologyName, className),
                    HttpStatus.OK);
        }
    }

    @RequestMapping(value = "/api/ontologies/{ontologyName}/classes/{className}/individuals", method = RequestMethod.GET)
    public ResponseEntity<List<Individual>> getAllIndividualsFromClass(@PathVariable String ontologyName,
                                                                       @PathVariable String className,
                                                                       @RequestParam(value = "withImportedIndividuals",
                                                                               required = false,
                                                                               defaultValue = "true") boolean withImportedIndividuals) {
        var individuals = ontologyContentService.getAllIndividualsOfClass(ontologyName, className);
        if (!withImportedIndividuals) {
            individuals.removeIf(Individual::isImported);
        }
        return new ResponseEntity<>(individuals, HttpStatus.OK);
    }

    @RequestMapping(value = "/api/ontologies/{ontologyName}/individuals", method = RequestMethod.GET)
    public ResponseEntity<List<Individual>> getAllIndividualsOfOntology(@PathVariable String ontologyName) {
        return new ResponseEntity<>(ontologyContentService.getAllIndividualsOfOntology(ontologyName),
                HttpStatus.OK);
    }

    @RequestMapping(value = "/api/ontologies/{ontologyName}/classes/{className}", method = RequestMethod.POST)
    public ResponseEntity<OntologyClass> addNewClass(@PathVariable String ontologyName,
                                                    @PathVariable String className,
                                                     @RequestParam("superClass") String superClassName) {
        return new ResponseEntity<>(ontologyContentService.addNewClass(ontologyName, className, superClassName),
                HttpStatus.OK);
    }

    @RequestMapping(value = "/api/ontologies/{ontologyName}/classes/{className}/individuals/{individualName}",
            method = RequestMethod.POST)
    public ResponseEntity<Boolean> addEmptyIndividual(@PathVariable String ontologyName,
                                                      @PathVariable String className,
                                                      @PathVariable String individualName) {
        return new ResponseEntity<>(ontologyContentService.addEmptyIndividual(ontologyName, className, individualName),
                HttpStatus.OK);
    }

    @RequestMapping(value = "/api/ontologies/{ontologyName}/properties/{propertyName}",
            method = RequestMethod.POST)
    public ResponseEntity<?> createNewProperty(@PathVariable String ontologyName,
                                                             @PathVariable String propertyName,
                                                             @RequestParam("isObjectProperty") boolean isObjectProperty,
                                                             @RequestParam("domain") String domain,
                                                             @RequestParam("range") String range) {
        try {
            return new ResponseEntity<>(ontologyContentService.createNewProperty(ontologyName, propertyName,
                    isObjectProperty, domain, range, true),
                    HttpStatus.OK);
        } catch (SimilarPropertiesExistException e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    @RequestMapping(value = "/api/ontologies/{ontologyName}/classes", method = RequestMethod.GET)
    public ResponseEntity<SubclassGraph> getSubclassGraphOfOntology(@PathVariable String ontologyName) {
        return new ResponseEntity<>(ontologyContentService.buildSubclassGraph(ontologyName), HttpStatus.OK);
    }

    //TODO: Rename both endpoints!

    @RequestMapping(value = "/api/ontologies/{ontologyName}/class", method = RequestMethod.GET)
    public ResponseEntity<List<OntologyClass>> getAllClassesOfOntology(@PathVariable String ontologyName) {
        return new ResponseEntity<>(ontologyContentService.getAllClassesOfOntology(ontologyName), HttpStatus.OK);
    }

    @RequestMapping(value = "/api/ontologies/{ontologyName}/individuals/{individualName}", method = RequestMethod.POST,
            consumes = "application/json;charset=UTF-8")
    public ResponseEntity<String> editIndividual(@PathVariable String ontologyName, @PathVariable String individualName,
                                 @RequestBody Map<String, String[]> form) {
        Dataset dataset = TDB2Factory.connectDataset(ontologyDirectory);
        dataset.begin(ReadWrite.WRITE);
        try {
            var uri = ontologyContentService.editIndividual(dataset, ontologyName, individualName, form);
            var res = ontologyContentService.validateOntology(dataset, ontologyName);
            if (res.isConsistent()) {
                dataset.commit();
                return ResponseEntity.ok("Instance was created and validated");
            } else {
                dataset.abort();
                return ResponseEntity.badRequest().body(res.getReason());
            }
        } catch (RuntimeException | OWLOntologyCreationException e) {
            dataset.abort();
            return ResponseEntity.internalServerError().body("An error occurred while validating the ontology.");
        } finally {
            dataset.end();
        }
    }

    @RequestMapping(value = "/api/ontologies/{ontologyName}/targetClasses", method = RequestMethod.GET)
    public ResponseEntity<List<OntologyClass>> getTargetClasses(@PathVariable String ontologyName) {
        return new ResponseEntity<>(ontologyContentService.getTargetClasses(ontologyName), HttpStatus.OK);
    }

    @RequestMapping(value = "/api/ontologies/{ontologyName}/individual", method = RequestMethod.DELETE)
    public String deleteIndividual(@PathVariable String ontologyName,
                                   @RequestParam("uri") String individualUri,
                                   Model model) {
        ontologyContentService.deleteIndividual(ontologyName, individualUri);
        return loadIndexPage(model);
    }
    // TODO: Generell bei allen Löschvorgängen prüfen, ob alle Rückstände (FormElements, etc.) gelöscht werden

    private String loadIndexPage(Model model) {
        model.addAttribute("ontologies", ontologyOverviewService.getImportedOntologies());
        model.addAttribute("forms", formOverviewService.getAllForms());

        return "index";
    }



}
