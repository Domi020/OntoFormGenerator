// Ontology section

/**
 * Deletes the ontology with the given name and IRI, and then reloads the page.
 * @param ontologyName the name of the ontology to delete
 * @param ontologyIri the IRI of the ontology to delete
 */
function deleteOntology(ontologyName, ontologyIri) {
    fetch("/ontologies/" + ontologyName, {
        method: 'DELETE'
    }).then(function (response) {
        if (response.ok) {
            location.reload();
        }
    });
}


// Form section

/**
 * Populates the draft list of the form with the given name.
 * @param buttonId the id of the button that was clicked to show the drafts (at the corresponding form)
 */
async function showDrafts(buttonId) {
    let formName = buttonId.replace('show-drafts-button-', '');
    let i = forms.findIndex(form => form.formName === formName);
    let drafts = formDrafts[i];
    let showDraftsList = document.querySelector('#showDraftsList');
    showDraftsList.innerHTML = '';
    for (let i = 0; i < drafts.length; i++) {
        let draft = drafts[i];
        let listItem = document.createElement('li');
        listItem.classList.add('mdl-list__item');
        listItem.innerHTML = `
                <span class="mdl-list__item-primary-content">` + draft.name + `</span>
                <a class="mdl-list__item-secondary-action clickable-button"
                                       href="/fill/` + formName + `/draft/` + draft.firstDraftName + `">
                                        <i class="material-icons">edit</i></a>
                <button class="mdl-list__item-secondary-action clickable-button"
                                       onclick="fetch('/api/forms/${formName}/draft?uri=${encodeURIComponent(draft.iri)}',
                                       {method: 'DELETE'}).then(async () => location.reload())">
                                        <i class="material-icons">delete</i></button>`
        showDraftsList.appendChild(listItem);
    }
}

/**
 * Populates the individual list of the form with the given name.
 * @param buttonId the id of the button that was clicked to show the individuals (at the corresponding form)
 */
async function showIndividuals(buttonId) {
    let ontologyAndFormName = buttonId.replace('show-individuals-button--', '');
    let ontologyName = ontologyAndFormName.split('--')[0];
    let formName = ontologyAndFormName.split('--')[1];
    let individuals = await fetch('/api/forms/' + formName + "/individuals").then(response => response.json());
    let showIndividualsList = document.querySelector('#showIndividualsList');
    showIndividualsList.innerHTML = '';
    for (let i = 0; i < individuals.length; i++) {
        let individual = individuals[i];
        let n = individual.name;
        let listItem = document.createElement('li');
        listItem.classList.add('mdl-list__item');
        listItem.innerHTML = `
                <span class="mdl-list__item-primary-content">` + individual.label + `</span>
                <a class="mdl-list__item-secondary-action clickable-button"
                                       href="/edit/ontologies/` + ontologyName + `/individuals/` + n + `">
                                        <i class="material-icons">edit</i></a>
                        <button class="mdl-list__item-secondary-action clickable-button"
                                       onclick="fetch('/api/ontologies/${ontologyName}/individual?uri=${encodeURIComponent(individual.iri)}',
                                       {method: 'DELETE'}).then(async () => location.reload())">
                                        <i class="material-icons">delete</i></button>`
        showIndividualsList.appendChild(listItem);
    }
}

/**
 * Fills the readonly URI field in the form create dialog, based on the selected ontology.
 */
function autoFillURI() {
    const ontologyName = document.getElementById('ontologyNameInFormCreate').value;
    for (let i = 0; i < ontologies.length; i++) {
        if (ontologies[i].name === ontologyName) {
            document.getElementById('ontologyURIInFormCreate').value = ontologies[i].iri;
            break;
        }
    }
}

/**
 * Reloads all available classes of the selected ontology in the form create dialog.
 */
function refreshAvailableClasses() {
    const ontologyName = document.getElementById('ontologyNameInFormCreate').value;
    fetch('/api/ontologies/' + ontologyName + '/classes')
        .then(response => response.json())
        .then(classes => {
            $('#classSelectInFormCreate')[0].selectize.clearOptions();
            for (let i = 0; i < classes.length; i++) {
                $('#classSelectInFormCreate')[0].selectize.addOption(classes[i]);
            }
            $('#classSelectInFormCreate')[0].selectize.refreshOptions(true);
        });
}

/**
 * Deletes the form with the given name and then reloads the page.
 * @param buttonId the id of the button that was clicked to delete the form
 */
function deleteForm(buttonId) {
    let formName = buttonId.replace('delete-form-button--', '');
    fetch('/api/forms/' + formName, {
        method: 'DELETE'
    }).then(function (response) {
        if (response.ok) {
            location.reload();
        }
    });
}

// Individual table section

/**
 * Fetches classes of the selected ontology where own individuals were created with OntoFormGenerator
 * and displays them in the class table.
 * @param ontologyButtonId the id of the button that was clicked to select the ontology
 */
async function onSelectOntology(ontologyButtonId) {
    let ontologyName = ontologyButtonId.replace('select-ontology-', '');
    let classes = await fetch('/api/ontologies/' + ontologyName + '/targetClasses').then(response => response.json());
    let classTable = document.querySelector('#class-table');
    classTable.innerHTML = '';
    for (let i = 0; i < classes.length; i++) {
        let curClass = classes[i];
        let row = document.createElement('tr');
        row.innerHTML = `
                <td>` + curClass.name + `</td>
                <td>
                    <button class="mdl-button
                                    mdl-js-button
                                    mdl-button--raised
                                    select-class"
                                    data-ontology="` + ontologyName + `"
                                    data-class-name="` + curClass.name + `"
                                    data-class-uri="` + curClass.uri + `"
                                    onclick="onSelectClass(this.getAttribute(\'data-class-name\'), this.getAttribute('data-class-uri'),
                                    this.getAttribute(\'data-ontology\'))">
                            Select
                    </button>
                </td>`;
        classTable.appendChild(row);
    }
}

/**
 * Fetches individuals of the selected class which were created with OntoFormGenerator
 * and displays them in the individual table.
 * @param ontClassName the name of the selected class
 * @param ontClassUri the URI of the selected class
 * @param ontName the name of the selected ontology
 */
async function onSelectClass(ontClassName, ontClassUri, ontName) {
    JsLoadingOverlay.show({'spinnerIcon': 'ball-clip-rotate'});
    let individuals = await fetch('/api/ontologies/' + ontName + '/classes/' + ontClassName + '/individuals?withImportedIndividuals=false&classIri=' + encodeURIComponent(ontClassUri))
        .then(response => response.json());
    let individualTable = document.querySelector('#individual-table');
    individualTable.innerHTML = '';
    for (let i = 0; i < individuals.length; i++) {
        let individual = individuals[i];
        let row = document.createElement('tr');
        row.innerHTML = `
                <td>` + individual.label + `</td>
                <td>
                    <button class="mdl-button
                                    mdl-js-button
                                    mdl-button--raised
                                    select-individual"
                                    data-ontology="` + ontName + `"
                                    data-individual="` + individual.name + `"
                                    onclick="onSelectIndividual(this.getAttribute(\'data-individual\'),
                                    this.getAttribute(\'data-ontology\'))">
                            Edit
                    </button>
                </td>
                <td>
                    <button class="mdl-button
                                    mdl-js-button
                                    mdl-button--raised
                                    select-individual"
                                    data-ontology="` + ontName + `"
                                    data-individual="` + encodeURIComponent(individual.iri) + `"
                                    onclick="fetch('/api/ontologies/' + this.getAttribute(\'data-ontology\') + '/individual?uri=' + this.getAttribute(\'data-individual\'),
                                    {method: 'DELETE'}).then(async () => location.reload())">
                            Delete
                   </td>`;
        individualTable.appendChild(row);
    }
    JsLoadingOverlay.hide();
}

/**
 * Redirects to the edit page of the selected individual in the individual table.
 * @param individualName the name of the selected individual
 * @param ontologyName the name of the ontology of the selected individual
 */
function onSelectIndividual(individualName, ontologyName) {
    location.href = '/edit/ontologies/' + ontologyName + '/individuals/' + individualName;
}