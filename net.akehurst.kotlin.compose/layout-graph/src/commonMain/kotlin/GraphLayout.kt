package net.akehurst.kotlin.components.layout.graph

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import net.akehurst.kotlinx.collections.Graph

@Composable
fun GraphLayout(
    layoutState: GraphLayoutState,
    modifier: Modifier = Modifier,
) {
    var state by remember { mutableStateOf(layoutState) }
    Box(
        modifier = modifier
    ) {

    }
}

data class GraphLayoutGraph(
    /** identified the graph, change it to indicate a state change */
    val id: String
) {
    val nodes = mutableStateListOf<GraphLayoutNode>()
    val edges = mutableStateListOf<GraphLayoutEdge>()
}

data class GraphLayoutNode(
    val id: String,
    val content: @Composable () -> Unit
)

data class GraphLayoutEdge(
    val id: String,
    val sourceId: String,
    val targetId: String,
    val content: List<@Composable () -> Unit>
)


@Stable
class GraphLayoutState(
    initialGraph:GraphLayoutGraph = GraphLayoutGraph("initial"),
) {
    var graph by mutableStateOf(initialGraph)
    val sugiyama = SugiyamaLayout<String>()
    val graphLayout: SugiyamaLayoutData<String> by lazy {
        val nodes = graph.nodes.map { it.id}
        val edges = graph.edges.map { Pair(it.sourceId, it.targetId) }
        sugiyama.layoutGraph(nodes, edges)
    }
}