const forceLayout = require("graphology-layout-forceatlas2");
const random = require("graphology-layout/circular")

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
    random.assign(graph)
    // const positions = forceLayout(graph, {maxIterations: 100});
    forceLayout.assign(graph, 1000);
    const sigmaInstance = new Sigma(graph, document.getElementById("container"));
    // https://graphology.github.io/standard-library/layout-force.html
}

module.exports = {'buildSubclassGraph': buildSubclassGraph};