package fau.fdm.OntoFormGenerator.service;

import fau.fdm.OntoFormGenerator.data.OntologyClass;
import fau.fdm.OntoFormGenerator.data.OntologyProperty;
import fau.fdm.OntoFormGenerator.tdb.PropertyService;
import org.apache.jena.query.Dataset;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class OntologyValidationService {

    private final PropertyService propertyService;
    @Value("${ontoformgenerator.ontologyDirectory}")
    private String ontologyDirectory;

    private final RestTemplate restTemplate;

    public OntologyValidationService(PropertyService propertyService) {
        this.propertyService = propertyService;
        this.restTemplate = new RestTemplate();
    }

    public List<OntologyProperty> findPotentialSimilarProperties(Dataset dataset,
                                                                 String ontologyName,
                                                                 String domainName,
                                                                 String newPropertyName) {
        List<OntologyProperty> resultList = new ArrayList<>();
        String url = "https://api.datamuse.com/words?rel_trg=" + newPropertyName;
        var response = restTemplate.getForObject(url, List.class);
        var synonyms = (List<Map<String, Object>>) response;
        assert synonyms != null;
        for (var synonym : synonyms) {
            var props = propertyService.searchProperties(dataset, ontologyName, domainName, synonym.get("word").toString());
            for (var property : props) {
                var ontologyProperty = new OntologyProperty();
                ontologyProperty.setName(property.getLocalName());
                if (property.getDomain() != null) {
                    ontologyProperty.setDomain(new OntologyClass(property.getDomain().getLocalName(), property.getDomain().getURI()));
                }
                ontologyProperty.setObjectProperty(property.isObjectProperty());
                if (property.isObjectProperty()) {
                    if (property.getRange() != null)
                        ontologyProperty.setObjectRange(new OntologyClass(property.getRange().getLocalName(), property.getRange().getURI()));
                } else {
                    ontologyProperty.setDatatypeRange(property.getRange().getLocalName());
                }
                ontologyProperty.setRdfsLabel(property.getLabel(null));
                ontologyProperty.setRdfsComment(property.getComment(null));
                resultList.add(ontologyProperty);
            }
        }
        return resultList;
    }
}

