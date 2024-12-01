# OntoFormGenerator

## Building the application
The application can be built using Maven and the pom.xml file.  
For the frontend part of the application (especially the class viewer), npm and browserify are required to build the required JS libraries.
The required libraries are listed in the `package.json` file.

To build the required bundle.js including all libraries run:
```
browserify src/main/resources/static/class-viewer.js --standalone myFuncs -o src/main/resources/static/bundle.js
```

## Starting the application
This application is a Spring Boot application, so you can start it by running the main method in the `OntoFormGeneratorApplication` class.  
Configuration is done in the `src/resources/application.properties` file.
There the following values can be set:
- ontologyDirectory: The directory where the ontologies are stored
- validator.mode: The used validator. Can be `Hermit` or `JFact`

## Usage
The application provides a REST API to generate forms from ontologies.
The index page is available at `http://localhost:8080/`.

## Code structure
The code is structured in the following way:
- `src/main/java/fau/fdm/OntoFormGenerator`: Contains the Java code
  - `controller`: Contains the controllers for the REST API
  - `data`: Contains DAO classes
  - `service`: Contains the service classes for the main backend logic
  - `tdb`: Contains the classes which access the TDB database
  - `validation`: Contains the classes for the OWL reasoner validation of the ontologies
  - `exception` : Contains own exceptions especially for validation
- `src/main/resources`: Contains the static resources especially the HTML templates, CSS and JS files
- `src/test/java/fau/fdm/OntoFormGenerator`: Contains a few test classes using a test TDB database. Can be run using standard JUnit / Maven Tests.
- `owl`: Contains the used form and restaurant example ontologies

