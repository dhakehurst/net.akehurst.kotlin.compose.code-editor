package net.akehurst.kotlin.components.layout.graph

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt

/**
 * change the id if you want the graph to update
 */
data class GraphLayoutGraph(
    /** identified the graph, change it to indicate a state change */
    val id: String
) {
    val nodesById = mutableStateMapOf<String, GraphLayoutNode>()
    val edgesById = mutableStateMapOf<String, GraphLayoutEdge>()
    val nodes get() = nodesById.values.toList()
    val edges get() = edgesById.values.toList()

    fun addNode(nodeId: String, content: @Composable () -> Unit = {}) {
        nodesById[nodeId] = GraphLayoutNode(nodeId, content)
    }

    fun addEdge(edgeId: String, sourceId: String, targetId: String, content: List<@Composable () -> Unit> = emptyList()) {
        edgesById[edgeId] = GraphLayoutEdge(edgeId, sourceId, targetId, content)
    }
}

data class GraphLayoutNode(val id: String) {
    constructor(id: String, content: @Composable () -> Unit) : this(id) {
        this.content = content
    }

    var content: @Composable () -> Unit = {}; private set
}

data class GraphLayoutEdge(
    val id: String,
    val sourceId: String,
    val targetId: String
) {
    constructor(id: String, sourceId: String, targetId: String, content: List<@Composable () -> Unit>) : this(id, sourceId, targetId) {
        this.content = content
    }

    var content: List<@Composable () -> Unit> = emptyList(); private set
}


@Stable
class GraphLayoutStateHolder {
    var zoom by mutableStateOf(1f)
    var offset by mutableStateOf(androidx.compose.ui.geometry.Offset.Zero)
    var routing by mutableStateOf(EdgeRouting.RECTILINEAR)

    val _graph = MutableStateFlow<GraphLayoutGraph>(GraphLayoutGraph("init"))
    var graph  = _graph.asStateFlow()

    fun updateGraph(graph: GraphLayoutGraph) {
        _graph.value = graph
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun GraphLayoutView(
    stateHolder: GraphLayoutStateHolder,
    modifier: Modifier = Modifier,
) {
    val graphState = stateHolder.graph.collectAsState().value
    Box(
        modifier = modifier
            .clip(RectangleShape) // Clip the content to the bounds of the Box
            .pointerInput(Unit) {
                // Detect pinch-to-zoom, drag, and rotation gestures.
                detectTransformGestures { centroid, pan, zoom, gestureRotation ->
                    // Calculate the new scale.
                    // Keep the scale within a reasonable range (e.g., 0.5f to 5f).
                    val oldScale = stateHolder.zoom
                    val newScale = (stateHolder.zoom * zoom).coerceIn(0.5f, 5f)

                    // Update the states.
                    stateHolder.zoom = newScale
                    stateHolder.offset += pan
                    //rotation += gestureRotation
                }
            }
            .onPointerEvent(PointerEventType.Scroll) { event ->
                // Handle mouse scroll for zooming
                val delta = event.changes.first().scrollDelta.y
                stateHolder.zoom = (stateHolder.zoom - delta * 0.1f).coerceIn(0.5f, 5f) // 0.1f is a sensitivity factor
            },
    ) {
        Box(
            modifier = Modifier
                .graphicsLayer(
                    scaleX = stateHolder.zoom,
                    scaleY = stateHolder.zoom,
                    translationX = stateHolder.offset.x,
                    translationY = stateHolder.offset.y,
                    // rotationZ = rotation
                )
        ) {
            var graphLayoutData by remember { mutableStateOf(SugiyamaLayoutData<GraphLayoutNode>()) }

            //layout the nodes
            Layout(
                modifier = modifier.fillMaxSize(),
                content = {
                    graphState.nodes.map { node -> node.content() }
                }
            ) { measurables, constraints ->
                val placeables = measurables.map { measurable -> measurable.measure(constraints.copy(minWidth = 0, minHeight = 0)) }

                val nodes = graphState.nodes
                val nodesById = graphState.nodesById
                val sgl = SugiyamaLayout<GraphLayoutNode>(
                    nodeWidth = { node -> placeables[nodes.indexOf(node)].width.toDouble() },
                    nodeHeight = { node -> placeables[nodes.indexOf(node)].height.toDouble() },
                    edgeRouting = stateHolder.routing
                )
                val edges = graphState.edges.map {
                    val src = nodesById[it.sourceId]!!
                    val tgt = nodesById[it.targetId]!!
                    Pair(src, tgt)
                }
                graphLayoutData = sgl.layoutGraph(nodes, edges) // cache for use laying out edges

                layout(width = graphLayoutData.totalWidth.roundToInt(), height = graphLayoutData.totalHeight.roundToInt()) {
                    placeables.forEachIndexed { index, placeable ->
                        val node = graphState.nodes[index]
                        val position = graphLayoutData.nodePositions[node]
                       println("** layout ${node.id} at $position")
                        if (null != position) {
                            placeable.placeRelative(x = position.first.roundToInt(), y = position.second.roundToInt())
                        }
                    }
                }
            }

            //layout the edges
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        layout(width = graphLayoutData!!.totalWidth.roundToInt(), height = graphLayoutData.totalHeight.roundToInt()) {
                            placeable.placeRelative(0, 0)
                        }
                    }
            ) {
                graphLayoutData!!.edgeRoutes.forEach { (edge, route) ->
                   println("** Edge: $edge ${route}")
                    drawPath(
                        path = Path().apply {
                            route.forEachIndexed { idx, pt ->
                                if (0 == idx) {
                                    moveTo(pt.first.toFloat(), pt.second.toFloat())
                                } else {
                                    lineTo(pt.first.toFloat(), pt.second.toFloat())
                                }
                            }
                        },
                        color = Color.Black,
                        style = Stroke(10f)
                    )
                }
            }
        }
    }
}