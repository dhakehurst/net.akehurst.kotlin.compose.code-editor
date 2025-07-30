package net.akehurst.kotlin.components.layout.graph

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

//data class Node(val id: String)
//data class Edge(val source: Node, val target: Node)
//data class Graph(val nodes: List<Node>, val edges: List<Edge>)

//data class Point(val x: Double, val y: Double)
data class SugiyamaLayoutData<NT : Any>(
    val totalWidth: Double,
    val totalHeight: Double,
    val nodePositions: Map<NT, Pair<Double, Double>>,
    val edgeRoutes: Map<Pair<NT, NT>, List<Pair<Double, Double>>>
) {
}

enum class EdgeRouting {
    DIRECT,
    RECTILINEAR
}

/**
 * Implements the Sugiyama-style layered graph layout algorithm.
 *
 * The algorithm works in several steps:
 * 1. Cycle Removal: Makes the graph acyclic by reversing some edges.
 * 2. Layer Assignment: Assigns each node to a layer (y-coordinate).
 * 3. Crossing Reduction: Reorders nodes within layers to minimize edge crossings.
 * 4. Coordinate Assignment: Assigns x and y coordinates to each node.
 *
 * @param nodeWidth The width of each node.
 * @param nodeHeight The height of each node.
 * @param layerSpacing The vertical distance between layers.
 * @param nodeSpacing The horizontal distance between nodes in the same layer.
 */
class SugiyamaLayout<NT : Any>(
    private val nodeWidth: (NT) -> Double = { 100.0 },
    private val nodeHeight: (NT) -> Double = { 50.0 },
    private val layerSpacing: Double = 80.0,
    private val nodeSpacing: Double = 50.0,
    private val edgeRouting: EdgeRouting = EdgeRouting.DIRECT
) {

    private var dummyIdCounter = 0
    private fun nextDummyId(): String = "dummy_${dummyIdCounter++}"

    // Internal representation of a node in the layered graph
    private data class SNode<NT : Any>(
        val originalNode: NT?, // null for dummy nodes
        val layer: Int,
        val id: String
    ) {
        val isDummy: Boolean get() = originalNode == null
        var posInLayer: Int = 0
    }

    // Internal representation of an edge in the layered graph
    private data class SEdge<NT : Any>(val from: SNode<NT>, val to: SNode<NT>)

    /**
     * Computes the layout for the given graph.
     */
    fun layoutGraph(nodes: List<NT>, edges: List<Pair<NT, NT>>): SugiyamaLayoutData<NT> {
        dummyIdCounter = 0

        // 1. Cycle Removal
        val (newEdges, reversedEdges) = makeAcyclic(nodes, edges)

        // 2. Layer Assignment
        val layers = assignLayers(nodes, newEdges)

        // 3. Add dummy nodes for long edges
        val (layeredNodes, layeredEdges) = createLayeredGraph(nodes, newEdges, layers)

        // 4. Crossing Reduction
        reduceCrossings(layeredNodes, layeredEdges)

        // 5. Coordinate Assignment
        return assignCoordinates(layeredNodes, layeredEdges, nodes, newEdges, reversedEdges, edges)
    }

    /**
     * Step 1: Makes the graph acyclic by reversing edges that form cycles.
     * This implementation uses a DFS-based approach to find and reverse back edges.
     * return edges, reveresedEdges
     */
    private fun makeAcyclic(nodes: List<NT>, edges: List<Pair<NT, NT>>): Pair<List<Pair<NT, NT>>, List<Pair<NT, NT>>> {
        val backEdges = mutableSetOf<Pair<NT, NT>>()
        val visitedNodes = mutableSetOf<NT>()
        val recursionStackNodes = mutableSetOf<NT>()
        val adj = edges.groupBy { it.first }

        fun findBackEdges(u: NT) {
            visitedNodes.add(u)
            recursionStackNodes.add(u)
            adj[u]?.forEach { edge ->
                val v = edge.second
                if (v in recursionStackNodes) {
                    backEdges.add(edge)
                } else if (v !in visitedNodes) {
                    findBackEdges(v)
                }
            }
            recursionStackNodes.remove(u)
        }

        for (node in nodes) {
            if (node !in visitedNodes) {
                findBackEdges(node)
            }
        }

        val newEdges = edges.toMutableList()
        val reversedEdges = mutableListOf<Pair<NT, NT>>()
        backEdges.forEach {
            newEdges.remove(it)
            newEdges.add(Pair(it.second, it.first))
            reversedEdges.add(it)
        }

        return Pair(newEdges, reversedEdges)
    }

    /**
     * Step 2: Assigns each node to a layer using the longest path algorithm (topological sort).
     */
    private fun assignLayers(nodes: List<NT>, edges: List<Pair<NT, NT>>): Map<NT, Int> {
        val layers = mutableMapOf<NT, Int>() //Node -> Layer
        val inDegree = nodes.associateWith { 0 }.toMutableMap()
        edges.forEach { inDegree[it.second] = inDegree.getValue(it.second) + 1 }

        val queueNodes = ArrayDeque<NT>()
        nodes.filter { inDegree[it] == 0 }.forEach {
            queueNodes.add(it)
            layers[it] = 0
        }

        while (queueNodes.isNotEmpty()) {
            val u = queueNodes.removeFirst()
            edges.filter { it.first == u }.forEach { edge ->
                val v = edge.second
                layers[v] = max(layers.getOrElse(v) { 0 }, layers.getValue(u) + 1)
                inDegree[v] = inDegree.getValue(v) - 1
                if (inDegree.getValue(v) == 0) {
                    queueNodes.add(v)
                }
            }
        }

        nodes.forEach { node ->
            if (node !in layers) {
                val qNodes = ArrayDeque<NT>()
                if (node !in layers) {
                    qNodes.add(node)
                    layers[node] = 0
                    while (qNodes.isNotEmpty()) {
                        val u = qNodes.removeFirst()
                        edges.filter { it.first == u }.forEach { edge ->
                            val v = edge.second
                            if (v !in layers) {
                                layers[v] = layers.getValue(u) + 1
                                qNodes.add(v)
                            }
                        }
                    }
                }
            }
        }

        return layers
    }

    /**
     * Step 3: Creates an intermediate graph representation with dummy nodes for edges spanning multiple layers.
     */
    private fun createLayeredGraph(nodes: List<NT>, edges: List<Pair<NT, NT>>, layers: Map<NT, Int>): Pair<List<MutableList<SNode<NT>>>, List<SEdge<NT>>> {
        val nodeMap = nodes.associateWith { SNode(it, layers.getValue(it), it.toString()) }
        val allSNodes = nodeMap.values.toMutableList()
        val allSEdges = mutableListOf<SEdge<NT>>()

        edges.forEach { edge ->
            val fromNode = nodeMap.getValue(edge.first)
            val toNode = nodeMap.getValue(edge.second)
            var u = fromNode
            for (i in (fromNode.layer + 1) until toNode.layer) {
                val dummy = SNode<NT>(null, i, nextDummyId())
                allSNodes.add(dummy)
                allSEdges.add(SEdge(u, dummy))
                u = dummy
            }
            allSEdges.add(SEdge(u, toNode))
        }

        val groupedByLayer = allSNodes.groupBy { it.layer }
        val layeredNodes = groupedByLayer.keys.sorted().map {
            groupedByLayer.getValue(it).toMutableList()
        }

        return layeredNodes to allSEdges
    }

    /**
     * Step 4: Reduces edge crossings using the barycenter heuristic.
     * Nodes are reordered in each layer based on the average position of their neighbors in adjacent layers.
     */
    private fun reduceCrossings(layeredNodes: List<MutableList<SNode<NT>>>, edges: List<SEdge<NT>>) {
        val adj = edges.groupBy { it.from.id }
        val revAdj = edges.groupBy { it.to.id }
        val nodeMap = layeredNodes.flatten().associateBy { it.id }

        layeredNodes.forEach { layer ->
            layer.forEachIndexed { index, node -> node.posInLayer = index }
        }

        val maxIterations = 24
        for (i in 0 until maxIterations) {
            // Sweep down
            for (layerIndex in 1 until layeredNodes.size) {
                val layer = layeredNodes[layerIndex]
                val barycenters = layer.associate { node ->
                    val predecessors = revAdj[node.id]?.mapNotNull { nodeMap[it.from.id] } ?: emptyList()
                    val center = if (predecessors.isEmpty()) -1.0 else predecessors.map { it.posInLayer }.average()
                    node.id to center
                }
                layer.sortBy { barycenters[it.id] }
                layer.forEachIndexed { index, node -> node.posInLayer = index }
            }
            // Sweep up
            for (layerIndex in layeredNodes.size - 2 downTo 0) {
                val layer = layeredNodes[layerIndex]
                val barycenters = layer.associate { node ->
                    val successors = adj[node.id]?.mapNotNull { nodeMap[it.to.id] } ?: emptyList()
                    val center = if (successors.isEmpty()) -1.0 else successors.map { it.posInLayer }.average()
                    node.id to center
                }
                layer.sortBy { barycenters[it.id] }
                layer.forEachIndexed { index, node -> node.posInLayer = index }
            }
        }
    }

    private fun computeNodeBorderIntersection(
        nodeCenter: Pair<Double, Double>,
        nodeSize: Pair<Double, Double>,
        externalPoint: Pair<Double, Double>
    ): Pair<Double, Double> {
        val (nodeCenterX, nodeCenterY) = nodeCenter
        val (nodeWidth, nodeHeight) = nodeSize
        val (externalX, externalY) = externalPoint

        val dx = externalX - nodeCenterX
        val dy = externalY - nodeCenterY

        if (dx == 0.0 && dy == 0.0) return nodeCenter

        val halfWidth = nodeWidth / 2
        val halfHeight = nodeHeight / 2

        if (halfWidth == 0.0 || halfHeight == 0.0) return nodeCenter

        // Calculate the slope of the line from the node center to the external point
        val slope = if (dx != 0.0) dy / dx else Double.POSITIVE_INFINITY

        // Calculate the slope of the diagonals of the node's bounding box
        val diagonalSlope = halfHeight / halfWidth

        val intersectX: Double
        val intersectY: Double

        if (abs(slope) < diagonalSlope) {
            // Intersection is with a vertical edge (left or right)
            intersectX = if (dx > 0) halfWidth else -halfWidth
            intersectY = slope * intersectX
        } else {
            // Intersection is with a horizontal edge (top or bottom)
            intersectY = if (dy > 0) halfHeight else -halfHeight
            intersectX = if (slope.isFinite() && slope != 0.0) intersectY / slope else 0.0
        }

        return Pair(nodeCenterX + intersectX, nodeCenterY + intersectY)
    }

    private data class Rect(val left: Double, val top: Double, val right: Double, val bottom: Double)

    private fun createRectilinearPath(pathPoints: List<Pair<Double, Double>>, nodePositions: Map<NT, Pair<Double, Double>>): List<Pair<Double, Double>> {
        if (pathPoints.size < 2) return pathPoints

        val nodeRects = nodePositions.map { (node, pos) ->
            val width = nodeWidth(node)
            val height = nodeHeight(node)
            Rect(pos.first, pos.second, pos.first + width, pos.second + height)
        }

        val finalPath = mutableListOf<Pair<Double, Double>>()
        finalPath.add(pathPoints.first())

        for (i in 0 until pathPoints.size - 1) {
            val p1 = pathPoints[i]
            val p2 = pathPoints[i + 1]

            val yMid = (p1.second + p2.second) / 2
            val xMin = min(p1.first, p2.first)
            val xMax = max(p1.first, p2.first)

            fun isClear(y: Double) = nodeRects.none { rect ->
                y > rect.top && y < rect.bottom && xMax > rect.left && xMin < rect.right
            }

            var routeY = yMid
            if (!isClear(yMid)) {
                val candidates = nodeRects
                    .filter { rect -> xMax > rect.left && xMin < rect.right }
                    .flatMap { listOf(it.top - 1, it.bottom + 1) } // 1 is padding

                routeY = candidates
                    .filter { isClear(it) }
                    .minByOrNull { abs(it - yMid) }
                    ?: yMid // Fallback to original yMid if no clear channel found
            }
            finalPath.add(Pair(p1.first, routeY))
            finalPath.add(Pair(p2.first, routeY))
        }
        finalPath.add(pathPoints.last())

        // Clean up the path to remove redundant points
        val cleanedPath = mutableListOf<Pair<Double, Double>>()
        if (finalPath.isNotEmpty()) {
            cleanedPath.add(finalPath.first())
            for (j in 1 until finalPath.size - 1) {
                val pPrev = cleanedPath.last()
                val pCurr = finalPath[j]
                val pNext = finalPath[j + 1]
                // Remove point if it's collinear with its neighbors
                val isCollinear = (pPrev.first == pCurr.first && pCurr.first == pNext.first) ||
                        (pPrev.second == pCurr.second && pCurr.second == pNext.second)
                if (!isCollinear) {
                    cleanedPath.add(pCurr)
                }
            }
            cleanedPath.add(finalPath.last())
        }
        return cleanedPath.distinct()
    }


    /**
     * Step 5: Assigns final coordinates to nodes and determines edge routes.
     */
    private fun assignCoordinates(
        layeredNodes: List<MutableList<SNode<NT>>>,
        layeredEdges: List<SEdge<NT>>,
        nodes: List<NT>,
        edges: List<Pair<NT, NT>>,
        reversedEdges: List<Pair<NT, NT>>,
        originalEdges: List<Pair<NT, NT>>
    ): SugiyamaLayoutData<NT> {
        val nodePositions = mutableMapOf<NT, Pair<Double, Double>>()
        val dummyPositions = mutableMapOf<SNode<NT>, Pair<Double, Double>>()

        val layerWidths = layeredNodes.map { it.sumOf { sNode -> sNode.originalNode?.let { nodeWidth(it) } ?: 0.0 } + (it.size - 1).coerceAtLeast(0) * nodeSpacing }
        val maxWidth = layerWidths.maxOrNull() ?: 0.0

        val layerHeights = layeredNodes.map { layer -> layer.maxOfOrNull { sNode -> sNode.originalNode?.let { nodeHeight(it) } ?: 0.0 } ?: 0.0 }
        val layerTops = mutableListOf(0.0)
        for (i in 0 until layeredNodes.size - 1) {
            layerTops.add(layerTops[i] + layerHeights[i] + layerSpacing)
        }

        layeredNodes.forEachIndexed { layerIndex, layer ->
            val layerWidth = layerWidths[layerIndex]
            val currentLayerTop = layerTops[layerIndex]
            var x = (maxWidth - layerWidth) / 2.0

            layer.forEach { sNode ->
                val nodeW = sNode.originalNode?.let(nodeWidth) ?: 0.0
                val pos = Pair(x, currentLayerTop) // this is top-left
                if (sNode.isDummy) {
                    dummyPositions[sNode] = pos
                } else {
                    nodePositions[sNode.originalNode!!] = pos
                }
                x += nodeW + nodeSpacing
            }
        }

        val sNodePositions = layeredNodes.flatten().associateWith { sNode ->
            val pos = if (sNode.isDummy) dummyPositions[sNode]!! else nodePositions[sNode.originalNode!!]!!
            val w = sNode.originalNode?.let(nodeWidth) ?: 0.0
            val h = sNode.originalNode?.let(nodeHeight) ?: 0.0
            Pair(pos.first + w / 2, pos.second + h / 2)
        }

        val nodeToSNode = layeredNodes.flatten().filterNot { it.isDummy }.associateBy { it.originalNode }
        val sAdj = layeredEdges.groupBy { it.from }
        val acyclicEdgeRoutes = mutableMapOf<Pair<NT, NT>, List<Pair<Double, Double>>>()

        edges.forEach { edge ->
            val startSNode = nodeToSNode.getValue(edge.first)
            val endSNode = nodeToSNode.getValue(edge.second)
            val q = ArrayDeque<List<SNode<NT>>>()
            q.add(listOf(startSNode))
            var pathSNodes: List<SNode<NT>> = emptyList()
            val visited = mutableSetOf(startSNode)

            while (q.isNotEmpty()) {
                val currentPath = q.removeFirst()
                val lastNode = currentPath.last()
                if (lastNode == endSNode) {
                    pathSNodes = currentPath
                    break
                }
                sAdj[lastNode]?.forEach { sEdge ->
                    val neighbor = sEdge.to
                    if (neighbor !in visited) {
                        visited.add(neighbor)
                        q.add(currentPath + neighbor)
                    }
                }
            }
            val pathPoints = pathSNodes.map { sNodePositions.getValue(it) } // These are centers.

            val unadjustedPath = if (edgeRouting == EdgeRouting.RECTILINEAR && pathPoints.size > 1) {
                createRectilinearPath(pathPoints, nodePositions)
            } else {
                pathPoints
            }

            var finalPathPoints: List<Pair<Double, Double>> = unadjustedPath

            if (unadjustedPath.size > 1) {
                val modifiedPath = unadjustedPath.toMutableList()

                // Modify start point
                val sourceNode = edge.first
                val sourceWidth = nodeWidth(sourceNode)
                val sourceHeight = nodeHeight(sourceNode)
                val sourceCenter = pathPoints.first()
                val nextPoint = unadjustedPath[1]
                modifiedPath[0] = computeNodeBorderIntersection(sourceCenter, Pair(sourceWidth, sourceHeight), nextPoint)

                // Modify end point
                val targetNode = edge.second
                val targetWidth = nodeWidth(targetNode)
                val targetHeight = nodeHeight(targetNode)
                val targetCenter = pathPoints.last()
                val prevPoint = unadjustedPath[unadjustedPath.size - 2]
                modifiedPath[modifiedPath.size - 1] = computeNodeBorderIntersection(targetCenter, Pair(targetWidth, targetHeight), prevPoint)
                finalPathPoints = modifiedPath
            }
            acyclicEdgeRoutes[edge] = finalPathPoints
        }

        val finalEdgeRoutes = mutableMapOf<Pair<NT, NT>, List<Pair<Double, Double>>>()
        originalEdges.forEach { edge ->
            if (edge in reversedEdges) {
                val reversed = Pair(edge.second, edge.first)
                acyclicEdgeRoutes[reversed]?.let { finalEdgeRoutes[edge] = it.reversed() }
            } else {
                acyclicEdgeRoutes[edge]?.let { finalEdgeRoutes[edge] = it }
            }
        }

        val totalWidth = maxWidth
        val totalHeight = (layerTops.lastOrNull() ?: 0.0) + (layerHeights.lastOrNull() ?: 0.0)
        return SugiyamaLayoutData(totalWidth, totalHeight, nodePositions, finalEdgeRoutes)
    }
}