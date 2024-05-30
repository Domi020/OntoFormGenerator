package fau.fdm.OntoFormGenerator.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

@Controller
public class RestController {

    @RequestMapping(value = "/", method = RequestMethod.GET)
    public String loadMainPage() {
        return "index";
    }
}
