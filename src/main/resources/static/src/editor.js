// Handling form elements

/**
 * Removes the form element with the given id.
 * @param id the id of the form element to remove
 */
function removeFormElement(id) {
    let item = document.getElementById(id);
    const fieldContainer = document.querySelector('.field-container');
    fieldContainer.removeChild(item);
}

/**
 * Creates a new form element and inserts it into the DOM.
 */
function addNewFormElement() {
    const fieldContainer = document.querySelector('.field-container');
    const newField = document.createElement('div');
    newField.className = "mdl-card mdl-shadow--8dp property-card";
    newField.id = `field-${elements}`;
    const newFieldFirstRow = document.createElement('div');
    newFieldFirstRow.className = "property-row";
    newFieldFirstRow.innerHTML = `
                <div class="mdl-textfield mdl-js-textfield property-field">
                    <label>Field name</label>
                    <input required class="mdl-textfield__input" type="text" name="fieldName">
                </div>
                <div class="property-field">
                    <label>Property</label>
                    <select required id="prop-select-` + elements + `" class="property-select" name="propertyName"></select>
                </div>
                <div class="mdl-textfield mdl-js-textfield property-field">
                    <label>Range</label>
                    <input id="range-field-` + elements + `" class="mdl-textfield__input" type="text" name="propertyRange" readonly>

                </div>
                <div class="property-field property-checkbox">
                    <label>Object property</label>
                    <input type="checkbox" id="object-property-checkbox-` + elements + `" name="object-property-checkbox" disabled>
                    <input type="hidden" id="hidden-object-property-checkbox-` + elements + `" name="isObjectProperty">
                </div>

            `;
    newField.appendChild(newFieldFirstRow);
    const newFieldSecondRow = document.createElement('div');
    newFieldSecondRow.className = "property-row";
    newFieldSecondRow.innerHTML = `
            <div class="property-field" style="vertical-align: middle">
                    <p id="minimum-values-field-constraint-` + elements + `">Min: -</p>
                    <p id="maximum-values-field-constraint-` + elements + `">Max: -</p>
                </div>
                <div class="property-field" style="vertical-align: middle">
                    <div>
                    <input required id="minimum-values-field-` + elements + `" class="mdl-textfield__input" type="number"
                    placeholder="1" name="minimumValues">
                    <label>Minimum values</label>
                    </div>
                    <div>
                    <input required id="maximum-values-field-` + elements + `" class="mdl-textfield__input" type="number"
                    placeholder="1" name="maximumValues">
                    <label>Maximum values</label>
                    </div>
                </div>
                <div class="property-field">
                    <label>Required</label>
                    <input type="checkbox" id="required-checkbox-` + elements + `" name="required-checkbox"
                     value="required-checkbox-` + elements + `" checked>

                </div>
                <div class="property-field">
                    <button type="button" class="mdl-button mdl-button--raised mdl-button--accent delete-form-element"
                    onclick="removeFormElement('field-` + elements + `')">Remove</button>
                </div>
            `;
    newField.appendChild(newFieldSecondRow);
    fieldContainer.appendChild(newField);
    const currentElement = elements;
    $('#prop-select-' + elements).selectize({
        options: availableProperties,
        valueField: 'name',
        labelField: 'name',
        searchField: 'name',
        maxItems: 1,
        closeAfterSelect: true,
        onChange: function(value) {
            const rangeField = document.getElementById('range-field-' + currentElement);
            const objectPropertyCheckbox = document.getElementById('object-property-checkbox-' + currentElement);
            const hiddenObjectPropertyCheckbox = document.getElementById('hidden-object-property-checkbox-' + currentElement);
            const prop = availableProperties.find(prop => prop.name === value);
            if (prop.objectProperty) {
                objectPropertyCheckbox.checked = true;
                hiddenObjectPropertyCheckbox.value = true;
                rangeField.value = prop.objectRange.name;
            } else {
                objectPropertyCheckbox.checked = false;
                hiddenObjectPropertyCheckbox.value = false;
                rangeField.value = prop.datatypeRange;
            }
            let relevantConstraints = constraints
                .filter(con => con.onProperty.name === value);
            for (let i = 0; i < relevantConstraints.length; i++) {
                if (relevantConstraints[i].constraintType === "MIN") {
                    const minField = document.getElementById('minimum-values-field-constraint-' + currentElement);
                    minField.innerText = "Min: " + relevantConstraints[i].value;
                } else if (relevantConstraints[i].constraintType === "MAX") {
                    const maxField = document.getElementById('maximum-values-field-constraint-' + currentElement);
                    maxField.innerText = "Max: " + relevantConstraints[i].value;
                }
            }
        }
    });
    elements++;
}


// Initialization

/**
 * Initializes the form creation page by loading already existing form elements, if any.
 */
function initEditorForm() {
    let divs = [];
    let items = [];
    formElements.forEach((element, index) => {
        addNewFormElement();
        const prop = element.ontologyProperty;
        const field = document.getElementById(`field-${index}`);
        field.querySelector('input[name="fieldName"]').value = element.name;
        divs.push(field.querySelector('select[name="propertyName"]'));
        items.push(prop.domain.name);
        field.querySelector('input[name="isObjectProperty"]').value = prop.objectProperty;
        field.querySelector('input[name="object-property-checkbox"]').checked = prop.objectProperty;
        if (prop.objectProperty) {
            field.querySelector('input[name="propertyRange"]').value = prop.objectRange.name;
        } else {
            field.querySelector('input[name="propertyRange"]').value = prop.datatypeRange;
        }
        field.querySelector('input[name="maximumValues"]').value = element.maximumValues;
        field.querySelector('input[name="minimumValues"]').value = element.minimumValues;
        field.querySelector('input[name="required-checkbox"]').checked = element.required;
    });
    firstRefreshAvailableProperties(divs, items);
}

/**
 * Loads all available properties for the domain class of the form.
 * @param divs all property selects of all already existing form elements
 * @param items already selected properties of the corresponding selects in 'divs'
 */
function firstRefreshAvailableProperties(divs, items) {
    const ontology = document.getElementById("ontologyName").value;
    fetch(`/api/ontologies/${ontology}/classes/${targetClass.name}/properties?classIri=${encodeURIComponent(targetClass.uri)}`)
        .then(async function (response) {
            availableProperties = await response.json();

            for (let i = 0; i < divs.length; i++) {
                availableProperties.forEach((prop) => {
                    divs[i].selectize.addOption(prop);
                });
                divs[i].selectize.refreshOptions(false);
                divs[i].selectize.setValue(items[i], false);
            }
        })
}

