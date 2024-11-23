// Initialization of the form filler page

/**
 * Initializes an object property by collecting all possible individuals from the ontology and setting up the selectize
 * @param promises a list of promises for all object properties, where the current one will be added
 * @param element the HTML element of the object property select
 */
function initObjectProperties(promises, element) {
    const fieldName = element.name;
    const fieldId = element.id;
    const currentFormElement = allFormElements
        .find(formElement => formElement.ontologyProperty.name === fieldName)
    const className = currentFormElement.ontologyProperty.objectRange.name;
    const maximumValues = currentFormElement.maximumValues;

    promises.push(fetch(`/api/ontologies/${ontologyName}/classes/${className}/individuals?restrictionDomain=${encodeURIComponent(targetClass.uri)}&restrictionProperty=${encodeURIComponent(currentFormElement.ontologyProperty.uri)}&classIri=${encodeURIComponent(currentFormElement.ontologyProperty.objectRange.uri)}`)
        .then(async function (response) {
            let allIndividuals = await response.json();
            $('#' + fieldId).selectize({
                valueField: 'name',
                labelField: 'name',
                searchField: 'name',
                dropdownParent: 'body',
                maxItems: 1,
                options: allIndividuals,
                render: {
                    option: function (item, escape) {
                        if (item.imported) {
                            return '<div class="option" style="color:#FD0000">' + escape(item.name) + '</div>';
                        } else {
                            return '<div class="option" style="color:#000000">' + escape(item.name) + '</div>';
                        }
                    },
                    item: function (item, escape) {
                        if (item.imported) {
                            return '<div class="option" style="color:#FD0000">' + escape(item.name) + '</div>';
                        } else {
                            return '<div class="option" style="color:#000000">' + escape(item.name) + '</div>';
                        }
                    }
                }

            });
        }));
}

/**
 * Initializes the form filler page by loading all object properties of the form, and loading the draft if it exists
 */
function initFormFiller() {
    JsLoadingOverlay.show({'spinnerIcon': 'ball-clip-rotate'});
    let promises = [];
    $(".object-select").each(function (index, element) {
        initObjectProperties(promises, element);
    });

    Promise.all(promises).then(() => {
        if (setElements) {
            loadDraft();
        }
        JsLoadingOverlay.hide();
    });
}

//---------------------------------------------------------------------------------------------------------------------




// Draft functionality

/**
 * Creates a draft of the form and returns a promise that resolves when the draft is created
 * @returns {Promise<Response>} a promise that resolves when the draft is created containing the response of the server
 *  to the draft creation request
 */
async function createDraft() {
    const form = document.querySelector('#fillform');
    const formData = new FormData(form);
    if (formData.get('instanceName') === '') {
        formData.set('instanceName', generateInstanceName());
    }
    let normalFields = {};
    let additionalFields = {};
    formData.forEach(function (value, key) {
        if (additionalElements.find(field => field.name === key)) {
            if (additionalFields[key] === undefined) {
                additionalFields[key] = [value];
            } else {
                additionalFields[key].push(value);
            }
        } else {
            if (normalFields[key] === undefined) {
                normalFields[key] = [value];
            } else {
                normalFields[key].push(value);
            }
        }
    });
    let formString = {
        'normalFields': normalFields,
        'additionalFields': additionalFields
    };
    if (normalFields['instanceName'] === '') {
        alert('Instance name cannot be empty');
        throw 0;
    }
    let formJSON = JSON.stringify(formString);
    return fetch(`/api/forms/${formName}/draft`, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
        },
        body: formJSON,
    });
}

/**
 * Loads a draft of the form and refills the fields for that if one exists
 */
function loadDraft() {
    document.getElementById('instanceName').value = individualName
    for (let i = 0; i < setElements.length; i++) {
        const element = setElements[i];
        for (let j = 0; j < allFormElements.length; j++) {
            if (allFormElements[j].ontologyProperty.name === element.fieldName) {
                const input = document.getElementById('input-' + allFormElements[j].name);
                const objectSelect = document.getElementById("object-select-" + allFormElements[j].name);
                for (let k = 0; k < element.values.length; k++) {
                    if (k === 0) {
                        if (input) {
                            input.value = element.values[k];
                        } else {
                            objectSelect.selectize.setValue(element.values[k]);
                        }
                    } else {
                        if (input) {
                            let clone = addField('main-container-' + allFormElements[j].name,
                                'template-' + allFormElements[j].name, false);
                            clone.value = element.values[k];
                        } else {
                            let clone = addField('main-container-' + allFormElements[j].name,
                                'template-' + allFormElements[j].name, true);
                            clone.selectize.setValue(element.values[k]);
                        }
                    }
                }
            }
        }
    }
}

/**
 * Creates a draft of the form and then redirects to the homepage
 */
function createDraftAndGoToHomepage() {
    createDraft().then(
        () => location.href = '/'
    ).catch();
}



//---------------------------------------------------------------------------------------------------------------------

// Form extension features
/**
 * Create a new form element (for the form, NOT a new ontology property)
 */
function createField() {
    let propName = document.getElementById('new-property-class-select').value;
    let draftName = document.getElementById('instanceName').value;
    createDraft()
        .then(() => fetch(`/api/forms/${formName}/drafts/${draftName}/field/${propName}`, {
                method: 'POST'
            })
                .then(() => location.href = `/fill/${formName}/draft/${draftName}`)
        );
}

/**
 * Add a field for a multiple-value property
 * @param containerId the HTML element id of the container where the field should be added too
 * @param templateId the HTML element id of the template for the field
 * @param isObjectSelect whether the field is an object select or a datatype input
 * @returns the created HTML element
 */
function addField(containerId, templateId, isObjectSelect) {
    let container = document.getElementById(containerId);
    let copies = container.getElementsByClassName('copy-' + containerId);
    let template = document.getElementById(templateId);
    let formElementName = templateId.replace('template-', '');
    let formElement = allFormElements.find(formElement => formElement.name === formElementName);
    if (copies.length >= formElement.maximumValues - 1) {
        alert('Maximum number of values reached');
    } else {
        let clone = template.cloneNode(true);
        clone.id = 'copy-' + templateId + Math.random().toString(36).substring(7);
        clone.classList.add('copy-' + containerId);
        container.appendChild(clone);
        if (isObjectSelect) {
            let brokenSelect = clone.querySelector('.selectize-control');
            brokenSelect.remove();
            let select = clone.querySelector('select');
            select.id = 'object-select-' + formElementName + '-' + (copies.length + 1);
            $('#' + select.id).selectize({
                valueField: 'name',
                labelField: 'name',
                searchField: 'name',
                dropdownParent: 'body',
                maxItems: 1,
                options: Object.values($('#object-select-' + formElementName)[0].selectize.options),
                render: {
                    option: function (item, escape) {
                        if (item.imported) {
                            return '<div class="option" style="color:#FD0000">' + escape(item.name) + '</div>';
                        } else {
                            return '<div class="option" style="color:#000000">' + escape(item.name) + '</div>';
                        }
                    },
                    item: function (item, escape) {
                        if (item.imported) {
                            return '<div class="option" style="color:#FD0000">' + escape(item.name) + '</div>';
                        } else {
                            return '<div class="option" style="color:#000000">' + escape(item.name) + '</div>';
                        }
                    }
                }
            });
            return select;
        } else {
            let input = clone.querySelector('input');
            input.value = '';
            return input;
        }
    }
}

// noinspection JSUnusedGlobalSymbols
/**
 * Remove a field from a multiple-value property
 * @param containerId the HTML element id of the container where the field should be removed from
 */
function removeField(containerId) {
    let container = document.getElementById(containerId);
    let copies = container.getElementsByClassName('copy-' + containerId);
    if (copies.length > 0) {
        container.removeChild(copies[copies.length - 1]);
    }
}

/**
 * Prepare the dialog to add a new form element to the form by selecting all ontology properties for the domain class
 * and setting up the selectize, and other dialog elements
 */
function prepareNewFieldDialog() {
    JsLoadingOverlay.show({'spinnerIcon': 'ball-clip-rotate'});
    fetch(`/api/ontologies/${ontologyName}/classes/${targetClass.name}/properties?classIri=${encodeURIComponent(targetClass.uri)}`)
        .then(async function (response) {
            let allProperties = await response.json();
            allPropertiesOfTargetClass = allProperties;
            $('#new-property-class-select').selectize({
                valueField: 'name',
                labelField: 'name',
                searchField: 'name',
                maxItems: 1,
                options: allProperties,
                onChange: value => {
                    let string;
                    if (value !== '') {
                        let objectProp = allProperties.find(property => property.name === value).objectProperty;
                        let comment = allProperties.find(property => property.name === value).rdfsComment;
                        if (objectProp) {
                            let range = allProperties.find(property => property.name === value).objectRange;
                            if (range) {
                                string = "Another instance: " + range.name;
                            } else {
                                string = "Another instance";
                            }
                        } else {
                            string = allProperties.find(property => property.name === value).datatypeRange;
                        }
                        document.getElementById('new-property-class-range').innerText = string;
                        if (comment === undefined) {
                            comment = '';
                        }
                        document.getElementById('new-property-comment').innerText = comment;
                    } else {
                        document.getElementById('new-property-class-range').innerText = '';
                        document.getElementById('new-property-comment').innerText = '';
                    }

                }
            });
            JsLoadingOverlay.hide();
            newFieldDialog.showModal();
        });
}

/**
 * Query the properties of the domain class of the form by the given query string in the dialog field
 */
function queryProperties() {
    let query = document.getElementById('new-property-input').value;
    fetch(`/api/ontologies/${ontologyName}/classes/${targetClass.name}/properties?classIri=${encodeURIComponent(targetClass.uri)}&query=${query}`)
        .then(async function (response) {
            let allProperties = await response.json();
            let select = $('#new-property-class-select')[0].selectize;
            select.clear();
            select.clearOptions();
            for (let i = 0; i < allProperties.length; i++) {
                select.addOption(allProperties[i]);
            }
        });
}


//---------------------------------------------------------------------------------------------------------------------


// Ontology extension features

/**
 * Changes the new-property-dialog depending on the selected range option
 */
function checkOptionOfNewProperty() {
    let option = document.querySelector('input[name="create-property-dialog-option"]:checked').value;
    if (option === '1') {
        document.getElementById('div-datatype-option').style.display = 'block';
        document.getElementById('div-object-option').style.display = 'none';
    } else {
        document.getElementById('div-datatype-option').style.display = 'none';
        document.getElementById('div-object-option').style.display = 'block';
    }
}

/**
 * Creates a new property based on the stated property values
 */
function createNewProperty() {
    let option = document.querySelector('input[name="create-property-dialog-option"]:checked').value;
    let propName = document.getElementById('new-property-name').value;
    if (propName === '') {
        alert('Property name cannot be empty');
        return;
    }
    let propDescription = document.getElementById('new-property-description').value;
    let range;
    let isObjectProperty;
    let validate = !document.getElementById('create-property-skip-validate').checked;
    if (option === '1') {
        range = document.getElementById('create-property-with-datatype').value;
        isObjectProperty = false;
    } else {
        range = document.getElementById('create-property-with-object').value;
        isObjectProperty = true;
    }
    let body = {
        'propName': propName,
        'propDescription': propDescription,
        'domain': targetClass,
        'isObjectProperty': isObjectProperty,
        'range': range,
    };
    fetch(`/api/ontologies/${ontologyName}/properties/${propName}?validate=${validate}`, {
        method: 'POST',
        body: JSON.stringify(body),
        headers: {
            'Content-Type': 'application/json',
        }
    }).then(async response => {
        if (!response.ok) {
            if (response.status === 400) {
                alert(await response.text());
            } else {
                alert('An error has occurred');
            }
        } else {
            location.reload()
        }
    });
}

/**
 * Creates a new class based on the stated values in the dialog
 */
function createSubclass() {
    const newClassName = document.getElementById('newClassName').value;
    const superclass = document.getElementById('add-subclass-dialog-superclass').textContent;
    fetch('/api/ontologies/' + ontologyName + '/classes/' + newClassName + '?superClass=' + superclass, {
        method: 'POST'
    }).then(async response => {
        if (response.ok) {
            location.reload();
        } else {
            if (response.status === 400) {
                alert(await response.text());
            } else {
                alert('An error has occurred');
            }
        }
    });
}

/**
 * Opens the dialog to create a new property
 */
function openNewPropertyDialog() {
    const newDialog = document.querySelector('#add-property-dialog');
    newDialog.close();
    const dialog = document.querySelector('#add-new-property-dialog');
    dialog.querySelector('.close').addEventListener('click', function () {
        dialog.close();
    });
    dialog.showModal();
}

/**
 * Opens the subclass graph dialog, and builds the graph if it has not been built yet
 */
function selectObjectPropertyFromGraph() {
    const newDialog = document.querySelector('#select-class-for-property-dialog');
    newDialog.querySelector('.close').addEventListener('click', function () {
        let output = document.getElementById('new-object-property-range');
        let outputOnOtherDialog = document.getElementById('create-property-with-object');
        outputOnOtherDialog.value = output.value
        newDialog.close();
    });
    newDialog.showModal();
    if (!subclassGraphBuild) {
        fetch(`/api/ontologies/${ontologyName}/graph`)
            .then(async function (response) {
                let graph = await response.json();
                let output = document.getElementById('new-object-property-range');
                let container = document.getElementById('class-select-container');
                myFuncs.buildSubclassGraphForPropertyRangeSelection(graph, container, output);
                subclassGraphBuild = true;
            });
    }
}


//---------------------------------------------------------------------------------------------------------------------



// Adding/editing individuals in the form

// noinspection JSUnusedGlobalSymbols
/**
 * Switch to the edit individual page for the currently selected individual
 * @param id the HTML element id of the individual select
 */
function editIndividual(id) {
    createDraft().then(
        () => {
            let indivName = $('#' + id)[0].value;
            if (indivName === '') {
                return;
            }
            location.href = '/edit/ontologies/' + ontologyName + '/individuals/' + indivName;
        }
    );
}

/**
 * Creates a new individual based on the selected option in the dialog, either by creating an empty individual or by
 * loading another existing form
 */
function createIndividual() {
    if (document.getElementById("create-individual-dialog-option-1").checked) {
        // Empty individual
        let individualName = document.getElementById("create-individual-name").value;
        let className = document.getElementById('create-individual-dialog-ontology-class').innerText
        let classUri = document.getElementById('create-individual-dialog-ontology-uri').innerText
        if (individualName === '') {
            alert('Individual name cannot be empty');
            return;
        }
        fetch(`/api/ontologies/${ontologyName}/classes/${className}/individuals/${individualName}?classURI=${encodeURIComponent(classUri)}`, {
            method: 'POST',
        }).then(() => {
            let draftName = document.getElementById('instanceName').value;
            createDraft().then(
                () => location.href = '/fill/' + formName + '/draft/' + draftName
            );
        });
    } else {
        // Create from form
        let formName = document.getElementById('create-individual-form-list').value;
        if (formName === '') {
            alert('Please select a form');
            return;
        }
        createDraft().then(
            () => location.href = '/fill/' + formName + "?ontology=" + ontologyName
        )
    }
}

// noinspection JSUnusedGlobalSymbols
/**
 * Set up the CreateIndividual dialog based on the selected property
 * @param propertyName the property name where the new individual should be created
 */
function prepareAddIndividualDialog(propertyName) {
    const dialog = document.querySelector('#add-individual-dialog');
    document.getElementById('create-individual-dialog-ontology-class').innerText =
        allFormElements.filter(formElement => formElement.ontologyProperty.name === propertyName)[0]
            .ontologyProperty.objectRange.name;
    document.getElementById('create-individual-dialog-ontology-uri').innerText =
        allFormElements.filter(formElement => formElement.ontologyProperty.name === propertyName)[0]
            .ontologyProperty.objectRange.uri;
    dialog.showModal();
}

//---------------------------------------------------------------------------------------------------------------------

// Saving the filled form

/**
 * Generates a random instance name based on the current date
 * @returns {string} the generated instance name
 */
function generateInstanceName() {
    let date = new Date();
    return date.toISOString() + "_" + Math.floor(Math.random() * 100);
}

/**
 * Checks if all required fields are filled and alerts the user if not
 * @returns {boolean} true if all required fields are filled, false otherwise
 */
function checkIfRequiredFieldsAreFilled() {
    let form = document.querySelector('#fillform');
    let formData = new FormData(form);
    let requiredFields = allFormElements.filter(formElement => formElement.required);
    for (let i = 0; i < requiredFields.length; i++) {
        let formElement = requiredFields[i];
        let propertyName = formElement.ontologyProperty.name;
        let values = formData.getAll(propertyName);
        if (values.length === 0 || values[0] === '') {
            alert("The field " + formElement.name + " is required");
            return false;
        }
    }
    return true;
}

/**
 * Converts the form data to a JSON string for the backend
 * @returns {string} the JSON string representation of the form data
 */
function formDataToJSON() {
    const form = document.querySelector('#fillform');
    const formData = new FormData(form);
    if (formData.get('instanceName') === '') {
        formData.set('instanceName', generateInstanceName());
    }
    let json = {};
    formData.forEach(function (value, key) {
        if (json[key] === undefined) {
            json[key] = [value];
        } else {
            json[key].push(value);
        }
    });
    return JSON.stringify(json);
}

/**
 * Saves the form, optionally validating the knowledge base
 * @param validate whether the knowledge base with the new individual should be validated before saving
 */
async function saveForm(validate) {
    JsLoadingOverlay.show({'spinnerIcon': 'ball-clip-rotate'});
    if (validate) {
        await createDraft();
    }
    if (!checkIfRequiredFieldsAreFilled()) return;
    const formJson = formDataToJSON();
    fetch(`/api/forms/${formName}/fill?validate=${validate.toString()}`, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
        },
        body: formJson
    }).then(async function (response) {
        JsLoadingOverlay.hide();
        if (response.ok) {
            location.href = '/';
        } else {
            let allErrors = await response.text();
            alert(allErrors);
        }
    });
}
