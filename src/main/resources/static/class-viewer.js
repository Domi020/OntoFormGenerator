// build with
// browserify src/main/resources/static/class-viewer.js --standalone myFuncs -o src/main/resources/static/bundle.js

const forceLayout = require("graphology-layout-forceatlas2");
const circ = require("graphology-layout/circular")

function buildSubclassGraphForPropertyRangeSelection(subclassGraph, containerElement, targetElement) {
    const graph = new graphology.DirectedGraph();
    for (const node of subclassGraph.classes) {
        graph.addNode(node.name, {label: node.name,
            size: 10, color: "black"});
    }
    for (const edge of subclassGraph.edges) {
        graph.addEdge(edge.subClass.name, edge.superClass.name, {size: 1, color: "purple",
            type: 'arrow'});
    }
    circ.assign(graph)
    // const positions = forceLayout(graph, {maxIterations: 100});
    forceLayout.assign(graph, 1000);
    const sigmaInstance = new Sigma(graph, containerElement);
    sigmaInstance.on("clickNode", (e) => {
        targetElement.value = e.node;
    });
}

function buildSubclassGraph() {
    const graph = new graphology.DirectedGraph();
    for (const node of subclassGraph.classes) {
        graph.addNode(node.name, {label: node.name,
        size: 10, color: "black"});
    }
    for (const edge of subclassGraph.edges) {
        graph.addEdge(edge.subClass.name, edge.superClass.name, {size: 1, color: "purple",
        type: 'arrow'});
    }
    circ.assign(graph)
    // const positions = forceLayout(graph, {maxIterations: 100});
    forceLayout.assign(graph, 1000);
    const sigmaInstance = new Sigma(graph, document.getElementById("container"));
    sigmaInstance.on("clickNode", (e) => {
        const dialogSuperclassName = document.getElementById("add-subclass-dialog-superclass");
        dialogSuperclassName.innerHTML = e.node;
        const dialog = document.getElementById("add-subclass-dialog")
        dialog.showModal();
        dialog.querySelector('.close').addEventListener('click', function() {
            dialog.close();
        });
    });
}

module.exports = {'buildSubclassGraph': buildSubclassGraph,
'buildSubclassGraphForPropertyRangeSelection': buildSubclassGraphForPropertyRangeSelection};