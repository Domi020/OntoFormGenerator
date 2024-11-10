package fau.fdm.OntoFormGenerator.controller;

import fau.fdm.OntoFormGenerator.data.*;
import fau.fdm.OntoFormGenerator.exception.OntologyValidationException;
import fau.fdm.OntoFormGenerator.exception.SimilarPropertiesExistException;
import fau.fdm.OntoFormGenerator.service.*;
import fau.fdm.OntoFormGenerator.tdb.GeneralTDBService;
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
import java.util.*;

@Controller
public class OntologyController {

    private final FormFillService formFillService;
    private final OntologyConstraintService ontologyConstraintService;
    private final OntologyValidationService ontologyValidationService;
    private final GeneralTDBService generalTDBService;
    Logger logger = LoggerFactory.getLogger(OntologyOverviewService.class);

    private final OntologyOverviewService ontologyOverviewService;

    private final OntologyContentService ontologyContentService;

    private final FormOverviewService formOverviewService;

    @Value("${ontoformgenerator.ontologyDirectory}")
    private String ontologyDirectory;

    public OntologyController(OntologyOverviewService ontologyOverviewService, OntologyContentService ontologyContentService, FormOverviewService formOverviewService, FormFillService formFillService, OntologyConstraintService ontologyConstraintService, OntologyValidationService ontologyValidationService, GeneralTDBService generalTDBService) {
        this.ontologyOverviewService = ontologyOverviewService;
        this.ontologyContentService = ontologyContentService;
        this.formOverviewService = formOverviewService;
        this.formFillService = formFillService;
        this.ontologyConstraintService = ontologyConstraintService;
        this.ontologyValidationService = ontologyValidationService;
        this.generalTDBService = generalTDBService;
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
                                                                             @RequestParam(value = "query", required = false) String query,
                                                                             @RequestParam(value = "classIri", required = false) String iri) {
        if (iri == null) {
            iri = generalTDBService.getClassURIInOntology(ontologyName, className);
        }
        if (query != null) {
            return new ResponseEntity<>(ontologyContentService.queryProperties(ontologyName, iri, query),
                    HttpStatus.OK);
        } else {
            return new ResponseEntity<>(ontologyContentService.getAllPropertiesOfDomain(ontologyName, iri),
                    HttpStatus.OK);
        }
    }

    @RequestMapping(value = "/api/ontologies/{ontologyName}/classes/{className}/individuals", method = RequestMethod.GET)
    public ResponseEntity<List<Individual>> getAllIndividualsFromClass(@PathVariable String ontologyName,
                                                                       @PathVariable String className,
                                                                       @RequestParam(value = "withImportedIndividuals",
                                                                               required = false,
                                                                               defaultValue = "true") boolean withImportedIndividuals,
                                                                       @RequestParam(value = "restrictionDomain",
                                                                               required = false) String restrictionDomain,
                                                                       @RequestParam(value = "restrictionProperty",
                                                                               required = false) String restrictionProperty,
                                                                       @RequestParam(value = "classIri", required = false) String iri) {
        if (iri == null) {
            iri = generalTDBService.getClassURIInOntology(ontologyName, className);
        }
        var individuals = ontologyContentService.getAllIndividualsOfClass(ontologyName, iri);
        if (!withImportedIndividuals) {
            individuals.removeIf(Individual::isImported);
        }
        if (restrictionDomain != null && restrictionProperty != null) {
            individuals = ontologyConstraintService.filterForAllValuesFromIndividuals(individuals, ontologyName,
                    restrictionDomain, restrictionProperty);
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
                                                     @RequestParam("superClass") String superClass) {
        return new ResponseEntity<>(ontologyContentService.addNewClass(ontologyName, className, superClass),
                HttpStatus.OK);
    }

    @RequestMapping(value = "/api/ontologies/{ontologyName}/classes/{className}/individuals/{individualName}",
            method = RequestMethod.POST)
    public ResponseEntity<Boolean> addEmptyIndividual(@PathVariable String ontologyName,
                                                      @PathVariable String className,
                                                      @PathVariable String individualName,
                                                      @RequestParam(value = "classURI", required = false) String classURI) {
        if (classURI == null) {
            classURI = generalTDBService.getClassURIInOntology(ontologyName, className);
        }
        return new ResponseEntity<>(ontologyContentService.addEmptyIndividual(ontologyName, classURI, individualName),
                HttpStatus.OK);
    }

    @RequestMapping(value = "/api/ontologies/{ontologyName}/properties/{propertyName}",
            method = RequestMethod.POST)
    public ResponseEntity<?> createNewProperty(@PathVariable String ontologyName,
                                               @PathVariable String propertyName,
                                               @RequestBody Map<String, Object> body,
                                               @RequestParam("validate") boolean validate) {

        try {
            var isObjectProperty = (boolean) body.get("isObjectProperty");
            var domain = (String) ((HashMap) body.get("domain")).get("uri");
            var range = (String) body.get("range");
            var propDescription = (String) body.get("propDescription");
            return new ResponseEntity<>(ontologyContentService.createNewProperty(ontologyName, propDescription,
                    propertyName, isObjectProperty, domain, range, validate),
                    HttpStatus.OK);
        } catch (OntologyValidationException e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    @RequestMapping(value = "/api/ontologies/{ontologyName}/graph", method = RequestMethod.GET)
    public ResponseEntity<SubclassGraph> getSubclassGraphOfOntology(@PathVariable String ontologyName) {
        return new ResponseEntity<>(ontologyContentService.buildSubclassGraph(ontologyName), HttpStatus.OK);
    }

    @RequestMapping(value = "/api/ontologies/{ontologyName}/classes", method = RequestMethod.GET)
    public ResponseEntity<List<OntologyClass>> getAllClassesOfOntology(@PathVariable String ontologyName) {
        return new ResponseEntity<>(ontologyContentService.getAllClassesOfOntology(ontologyName), HttpStatus.OK);
    }

    //URI
    @RequestMapping(value = "/api/ontologies/{ontologyName}/individuals/{individualName}", method = RequestMethod.POST,
            consumes = "application/json;charset=UTF-8")
    public ResponseEntity<String> editIndividual(@PathVariable String ontologyName,
                                                 @PathVariable String individualName,
                                                 @RequestParam("individualUri") String individualUri,
                                                 @RequestBody Map<String, String[]> form) {
        Dataset dataset = TDB2Factory.connectDataset(ontologyDirectory);
        dataset.begin(ReadWrite.WRITE);
        try {
            if (individualUri == null) {
                individualUri = generalTDBService.getIndividualURIInOntology(dataset, ontologyName, individualName);
            }
            var uri = ontologyContentService.editIndividual(dataset, ontologyName, individualUri, form);
            var res = ontologyValidationService.validateOntology(dataset, ontologyName);
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

    @RequestMapping(value = "/api/ontologies/{ontologyName}/restrictions", method = RequestMethod.GET)
    public ResponseEntity<List<Constraint>> getConstraints(@PathVariable String ontologyName,
                                                           @RequestParam(value = "domainUri") String domainUri,
                                                           @RequestParam(value = "propertyUri", required = false)
                                                           String propertyUri) {
        return new ResponseEntity<>(ontologyConstraintService.getConstraints(ontologyName, domainUri, propertyUri),
                HttpStatus.OK);
    }

    private String loadIndexPage(Model model) {
        model.addAttribute("ontologies", ontologyOverviewService.getImportedOntologies());
        model.addAttribute("forms", formOverviewService.getAllForms());

        return "index";
    }


}
