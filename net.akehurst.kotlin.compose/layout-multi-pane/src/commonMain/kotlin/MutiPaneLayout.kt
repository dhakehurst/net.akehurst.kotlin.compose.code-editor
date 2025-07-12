/**
 * Copyright (C) 2025 Dr. David H. Akehurst (http://dr.david.h.akehurst.net)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.akehurst.kotlin.compose.layout.multipane

import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt

// --- Multiplatform UUID Generation ---
// This is an 'expect' declaration in commonMain

// --- 1. Data Model for Layout ---

/**
 * Represents a node in the layout tree. It can be either a Pane or a Split.
 */
sealed class LayoutNode {
    companion object {
        private var next = 0;
        private fun generateID(): String = "id${next++}"
    }

    /**
     * Unique ID for each node
     */
    abstract val id: String

    fun addPane(rootLayout: LayoutNode, newPane: Pane):LayoutNode {
        val dropTarget = DropTarget(
            rect = Rect.Zero,
            type = DropTargetType.SPLIT_BOTTOM,
            targetNodeId = id,
        )
        return insertPaneIntoLayout(rootLayout, newPane, dropTarget)
    }

    /**
     * Represents a single content pane in the layout.
     * @param id Unique identifier for the pane.
     * @param title The title displayed in the pane's header.
     * @param content The Composable content to display inside the pane.
     */
    data class Pane(
        override val id: String = generateID(),
        val title: String,
        val content: @Composable () -> Unit
    ) : LayoutNode()

    /**
     * Represents a division in the layout, containing multiple child nodes.
     * @param id Unique identifier for the split.
     * @param orientation The orientation of the split (Horizontal for Row, Vertical for Column).
     * @param children The list of child LayoutNodes within this split.
     * @param weights The proportional sizes of the children. Must match the size of `children`.
     */
    data class Split(
        override val id: String = generateID(),
        val orientation: SplitOrientation,
        val children: List<LayoutNode>,
        val weights: List<Float>
    ) : LayoutNode() {
        init {
            require(children.size == weights.size) {
                "Number of children (${children.size}) must match number of weights (${weights.size})"
            }
            require(weights.all { it >= 0f }) { "Weights must be non-negative" }
            require(weights.sum() > 0f) { "Total weight must be greater than 0" }
        }
    }

    data class Tabbed(
        override val id: String = generateID(),
        val children: List<LayoutNode>
    ): LayoutNode(){}
}

/**
 * Defines the orientation of a Split.
 */
enum class SplitOrientation { Horizontal, Vertical }

// --- 2. Global Drag State (for communication between composables) ---

/**
 * Represents the state of a drag operation for a pane.
 * @param isDragging True if a pane is currently being dragged.
 * @param draggedPaneId The ID of the pane being dragged.
 * @param initialTouchOffsetInPane The offset from the top-left of the *entire pane* to the initial touch point, in local coordinates.
 * @param currentTouchScreenPosition The current absolute screen position of the touch point.
 * @param paneContent The Composable content of the dragged pane for visual representation.
 * @param paneTitle The title of the dragged pane for visual representation.
 * @param paneSize The size of the pane being dragged.
 */
data class DragState(
    val isDragging: Boolean = false,
    val draggedPaneId: String? = null,
    val initialTouchOffsetInPane: Offset = Offset.Zero,
    val currentTouchScreenPosition: Offset = Offset.Zero,
    val paneContent: (@Composable () -> Unit)? = null,
    val paneTitle: String = "",
    val paneBounds: Rect = Rect.Zero
)

/**
 * CompositionLocal to provide and consume the global drag state.
 * This now correctly provides a MutableState<DragState>.
 */
val LocalDragState = compositionLocalOf { mutableStateOf(DragState()) } // Corrected: Provides MutableState<DragState>

/**
 * Represents a potential drop target during a drag operation.
 * This would be used for the "preview" ghosting.
 */
data class DropTarget(
    val rect: Rect,
    val type: DropTargetType, // e.g., INSERT_LEFT, INSERT_RIGHT, SPLIT_TOP, REORDER
    val targetNodeId: String, // The ID of the node being targeted
    val targetParentId: String? = null // The ID of the target's parent split, useful for reordering
)

enum class DropTargetType {
    INSERT_LEFT, INSERT_RIGHT, INSERT_TOP, INSERT_BOTTOM, // Insert into an empty space or new split
    SPLIT_LEFT, SPLIT_RIGHT, SPLIT_TOP, SPLIT_BOTTOM,     // Split an existing pane
    TAB_LEFT, TAB_RIGHT,
    REORDER_BEFORE, REORDER_AFTER,                        // Reorder within an existing split
    NONE // No valid drop target
}

// --- 3. Main Multi-Window Layout Composable ---

/**
 * The main composable for the multi-window tiled layout.
 * Manages the root layout node and global drag state.
 * @param initialLayout The initial layout structure.
 * @param modifier Modifier for the overall layout.
 * @param splitterThickness The thickness of the splitters.
 * @param onLayoutChanged Callback triggered when the layout structure changes (e.g., after a drag/drop).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiPaneLayout(
    layoutState: MultiPaneLayoutState,
    modifier: Modifier = Modifier,
    splitterThickness: Dp = 8.dp,
    onLayoutChanged: (LayoutNode) -> Unit = {}
) {
    var state by remember { mutableStateOf(layoutState) }
    // dragState is now the MutableState<DragState> object
    val dragState = remember { mutableStateOf(DragState()) }
    val coroutineScope = rememberCoroutineScope()

    // State for the potential drop target (for preview ghosting)
    var potentialDropTarget by remember { mutableStateOf<DropTarget?>(null) }

    // Map to store the global bounds of all panes, updated via onGloballyPositioned
    val paneBoundsMap = remember { mutableStateMapOf<String, Rect>() }

    // State to hold the LayoutCoordinates of the root Box (MultiWindowLayout itself)
    var rootLayoutCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    // Provide the MutableState<DragState> object to all children via CompositionLocal
    CompositionLocalProvider(LocalDragState provides dragState) { // Corrected: Provides the MutableState object
        Box(
            modifier = modifier
                .fillMaxSize()
                .onGloballyPositioned { coordinates ->
                    rootLayoutCoordinates = coordinates // Capture coordinates of the root Box
                }
        ) {
            // Render the actual layout
            LayoutNodeRenderer(
                node = state.rootLayout,
                splitterThickness = splitterThickness,
                onSplitterDrag = { splitId, newWeights ->
                    state.rootLayout = updateSplitWeights(state.rootLayout, splitId, newWeights)
                    onLayoutChanged(state.rootLayout)
                },
                // Pass callback to update pane bounds map
                onPaneBoundsChanged = { paneId, rect ->
                    paneBoundsMap[paneId] = rect
                },
                // Callbacks for drag events from panes
                onPaneDragStart = { paneId, title, content, initialTouchOffsetInPane, initialTouchScreenPosition, paneBounds ->
                    dragState.value = DragState( // Corrected: Update the value of the MutableState
                        isDragging = true,
                        draggedPaneId = paneId,
                        initialTouchOffsetInPane = initialTouchOffsetInPane,
                        currentTouchScreenPosition = initialTouchScreenPosition,
                        paneContent = content,
                        paneTitle = title,
                        paneBounds = paneBounds // Pass the pane size
                    )
                },
                onPaneDrag = { currentScreenPosition ->
                    println("** currentScreenPosition: $currentScreenPosition")
                    dragState.value = dragState.value.copy(currentTouchScreenPosition = currentScreenPosition) // Corrected: Update the value

                    // Calculate potential drop target based on current drag position and pane bounds
                    potentialDropTarget = calculateDropTarget(
                        rootLayout = state.rootLayout,
                        draggedPaneId = dragState.value.draggedPaneId!!,
                        currentDragPosition = currentScreenPosition,
                        paneBoundsMap = paneBoundsMap
                    )
                },
                onPaneDragEnd = {
                    // Implement the actual layout modification based on potentialDropTarget
                    if (potentialDropTarget != null && dragState.value.draggedPaneId != null) {
                        state.rootLayout = applyDrop(
                            root = state.rootLayout,
                            draggedPaneId = dragState.value.draggedPaneId!!,
                            draggedPaneContent = dragState.value.paneContent!!,
                            draggedPaneTitle = dragState.value.paneTitle,
                            dropTarget = potentialDropTarget!!
                        )
                        onLayoutChanged(state.rootLayout)
                    }
                    dragState.value = DragState() // Corrected: Reset the value
                    potentialDropTarget = null // Clear preview
                }
            )

            // Render the floating dragged pane visual
            if (dragState.value.isDragging && dragState.value.draggedPaneId != null) { // Corrected: Access value
                val renderPosition = dragState.value.currentTouchScreenPosition -dragState.value.initialTouchOffsetInPane //+ dragState.value.paneBounds.topLeft

                println("--- Debug Render DragState ---")
                println("DragState: ${dragState.value}")
                println("renderPosition: ${renderPosition}")
                println("------------------------------")
                // Animate the position of the dragged pane visual
                // The target value is the absolute screen position of the pane's top-left
                val animatedOffsetInWindow by animateOffsetAsState(
                    targetValue = renderPosition, // Corrected: Access value
                    animationSpec = tween(durationMillis = 50), // Smooth movement
                    label = "draggedPaneOffsetAnimation"
                )
                // Convert window coordinates to local coordinates relative to the root Box
                val localOffset = rootLayoutCoordinates?.windowToLocal(animatedOffsetInWindow) ?: Offset.Zero
                Surface(
                    modifier = Modifier
                        .offset { IntOffset(localOffset.x.roundToInt(), localOffset.y.roundToInt()) }
                        .zIndex(100f) // Ensure it's on top
                        .alpha(0.7f) // Semi-transparent
                        .graphicsLayer {
                            // Optional: Add a subtle scale or shadow for visual feedback
                            shadowElevation = 8.dp.toPx()
                            scaleX = 1.05f
                            scaleY = 1.05f
                        }
                        // Use the actual pane size for the floating visual
                        .width(with(LocalDensity.current) { dragState.value.paneBounds.width.toDp() })
                        .height(with(LocalDensity.current) { dragState.value.paneBounds.height.toDp() })
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .border(1.dp, MaterialTheme.colorScheme.primary)
                    ) {
                        Text(
                            text = dragState.value.paneTitle, // Corrected: Access value
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(8.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Drop me!",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                    }
                }
            }

            potentialDropTarget?.let { target ->
                // Convert target rect bounds from window coordinates to local coordinates of the root Box
                val localTargetRect = rootLayoutCoordinates?.windowToLocal(target.rect.topLeft) ?: target.rect.topLeft

                Box(
                    modifier = Modifier
                        .offset { IntOffset(localTargetRect.x.roundToInt(), localTargetRect.y.roundToInt()) } // Use localTargetRect
                        .size(with(LocalDensity.current) { target.rect.width.toDp() }, with(LocalDensity.current) {  target.rect.height.toDp() })
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                        .border(2.dp, MaterialTheme.colorScheme.primary)
                        .zIndex(99f) // Below dragged pane, above regular content
                )
            }
        }
    }
}

@Stable
class MultiPaneLayoutState(
    initialLayout: LayoutNode
) {
    var rootLayout by mutableStateOf(initialLayout)
}

// --- 4. Recursive Layout Node Renderer ---

/**
 * Recursively renders a LayoutNode tree.
 * @param node The current LayoutNode to render.
 * @param splitterThickness Thickness of splitters.
 * @param onSplitterDrag Callback for when a splitter is dragged.
 * @param onPaneBoundsChanged Callback to report the global bounds of a pane.
 * @param onPaneDragStart Callback when a pane drag starts.
 * @param onPaneDrag Callback during a pane drag.
 * @param onPaneDragEnd Callback when a pane drag ends.
 * @param modifier Modifier for the current node.
 */
@Composable
private fun LayoutNodeRenderer(
    node: LayoutNode,
    splitterThickness: Dp,
    onSplitterDrag: (String, List<Float>) -> Unit,
    onPaneBoundsChanged: (String, Rect) -> Unit, // New callback
    onPaneDragStart: (String, String, @Composable () -> Unit, Offset, Offset, Rect) -> Unit, // Added IntSize
    onPaneDrag: (Offset) -> Unit,
    onPaneDragEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dragState = LocalDragState.current.value // Corrected: Access the value from MutableState

    // State to hold the pane's absolute bounds in the window
    var paneBoundsInWindow by remember { mutableStateOf(Rect.Zero) }

    // State to hold the LayoutCoordinates of the drag handle Surface
    var dragHandleCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }


    when (node) {
        is LayoutNode.Pane -> {
            Card(
                modifier = modifier
                    .fillMaxSize() // Pane should fill its allocated space
                    .padding(1.dp) // Small padding to visually separate panes
                    // Use graphicsLayer for alpha to avoid layout shifts
                    .graphicsLayer { alpha = if (dragState.isDragging && dragState.draggedPaneId == node.id) 0f else 1f }
                    .onGloballyPositioned { coordinates ->
                        // Report the pane's bounds to the parent MultiWindowLayout
                        paneBoundsInWindow = coordinates.boundsInWindow() // Update local state for pane bounds
                        onPaneBoundsChanged(node.id, paneBoundsInWindow) // Report to parent
                    }
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Title Tab - Drag Handle
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .onGloballyPositioned { coordinates ->
                                dragHandleCoordinates = coordinates // Store coordinates of the drag handle
                            }
                            .pointerInput(node.id) { // Use node.id as key to restart detector if ID changes
                                detectDragGestures(
                                    onDragStart = { initialTouchOffsetInSurface ->
                                        val dragHandleCoords = dragHandleCoordinates
                                        if (dragHandleCoords == null) {
                                            println("ERROR: dragHandleCoordinates is null on drag start for pane ${node.id}")
                                            return@detectDragGestures // Cannot proceed without coordinates
                                        }

                                        // 1. Get the absolute screen position of where the touch started
                                        // This is the most critical part: convert the local touch offset in handle to window coordinates
                                        val initialTouchScreenPosition = dragHandleCoords.localToWindow(initialTouchOffsetInSurface)

                                        // 2. Get the absolute screen bounds of the *entire pane*
                                        // Use the stored paneBoundsInWindow, which is updated by the outer Card's onGloballyPositioned
                                        val currentPaneBoundsInWindow = paneBoundsInWindow

                                        // 3. Calculate the offset from the *pane's* top-left to the initial touch point
                                        val offsetRelativeToPaneTopLeft = initialTouchScreenPosition - currentPaneBoundsInWindow.topLeft

                                        println("--- DRAG START DEBUG ---")
                                        println("Pane ID: ${node.id}")
                                        println("currentPaneBoundsInWindow): $currentPaneBoundsInWindow") // Use the directly obtained bounds
                                        //println("dragHandleCoords.boundsInWindow(): ${dragHandleCoords.boundsInWindow()}")
                                        println("initialTouchOffsetInSurface: $initialTouchOffsetInSurface")
                                        println("initialTouchScreenPosition: $initialTouchScreenPosition")
                                        println("offsetRelativeToPaneTopLeft: $offsetRelativeToPaneTopLeft")
                                        println("------------------------")

                                        onPaneDragStart(
                                            node.id,
                                            node.title,
                                            node.content,
                                            offsetRelativeToPaneTopLeft, // Corrected: Offset from pane's top-left to touch
//                                            initialTouchOffsetInSurface, // Corrected: Offset from pane's top-left to touch
                                            initialTouchScreenPosition, // Absolute screen position of touch
                                            currentPaneBoundsInWindow //.size.toIntSize() // Pass the actual size of the pane
                                        )
                                    },
                                    onDrag = { change: PointerInputChange, _: Offset ->
                                        val dragHandleCoords = dragHandleCoordinates
                                        if (dragHandleCoords == null) {
                                            println("ERROR: dragHandleCoordinates is null on drag for pane ${node.id}")
                                            return@detectDragGestures // Cannot proceed without coordinates
                                        }
                                        val currentScreenPosition = dragHandleCoords.localToWindow(change.position)
//                                        val currentScreenPosition =change.position
                                        // Update the current drag position based on pointer changes
                                        onPaneDrag(currentScreenPosition)
                                        change.consume() // Consume the event so it doesn't propagate
                                    },
                                    onDragEnd = { onPaneDragEnd() },
                                    onDragCancel = { onPaneDragEnd() }
                                )
                            }
                    ) {
                        Text(
                            text = node.title,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    // Pane Content
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        node.content()
                    }
                }
            }
        }
        is LayoutNode.Split -> {
            // State to hold the size of the current Split container (Row or Column)
            var splitContainerSize by remember { mutableStateOf(IntSize.Zero) }

            // Ensure weights sum to 1 for correct proportional distribution
            val totalWeight = node.weights.sum()
            val normalizedWeights = node.weights.map { it / totalWeight }

            if (node.orientation == SplitOrientation.Horizontal) {
                Row(
                    modifier = modifier
                        .fillMaxSize()
                        .onGloballyPositioned { coordinates ->
                            splitContainerSize = coordinates.size
                        }
                ) {
                    node.children.forEachIndexed { index, child ->
                        LayoutNodeRenderer(
                            node = child,
                            splitterThickness = splitterThickness,
                            onSplitterDrag = onSplitterDrag,
                            onPaneBoundsChanged = onPaneBoundsChanged, // Pass the callback down
                            onPaneDragStart = onPaneDragStart,
                            onPaneDrag = onPaneDrag,
                            onPaneDragEnd = onPaneDragEnd,
                            modifier = Modifier.weight(normalizedWeights[index])
                        )
                        if (index < node.children.size - 1) {
                            Splitter(
                                orientation = SplitOrientation.Vertical,
                                thickness = splitterThickness,
                                onDrag = { dragAmount ->
                                    val newWeights = node.weights.toMutableList()
                                    // Use the captured splitContainerSize for totalSize
                                    val totalSize = splitContainerSize.width.toFloat()
                                    val deltaRatio = dragAmount / totalSize

                                    // Adjust weights, ensuring they stay positive
                                    val weightBefore = newWeights[index]
                                    val weightAfter = newWeights[index + 1]

                                    val newWeightBefore = (weightBefore + deltaRatio).coerceAtLeast(0.01f)
                                    val newWeightAfter = (weightAfter - deltaRatio).coerceAtLeast(0.01f)

                                    // Re-normalize if necessary to maintain sum
                                    val adjustment = (weightBefore + weightAfter) - (newWeightBefore + newWeightAfter)
                                    newWeights[index] = newWeightBefore + adjustment / 2
                                    newWeights[index + 1] = newWeightAfter + adjustment / 2

                                    // Ensure final weights are still positive after adjustment
                                    newWeights[index] = newWeights[index].coerceAtLeast(0.01f)
                                    newWeights[index + 1] = newWeights[index + 1].coerceAtLeast(0.01f)


                                    onSplitterDrag(node.id, newWeights)
                                }
                            )
                        }
                    }
                }
            } else { // Vertical Split
                Column(
                    modifier = modifier
                        .fillMaxSize()
                        .onGloballyPositioned { coordinates ->
                            splitContainerSize = coordinates.size
                        }
                ) {
                    node.children.forEachIndexed { index, child ->
                        LayoutNodeRenderer(
                            node = child,
                            splitterThickness = splitterThickness,
                            onSplitterDrag = onSplitterDrag,
                            onPaneBoundsChanged = onPaneBoundsChanged, // Pass the callback down
                            onPaneDragStart = onPaneDragStart,
                            onPaneDrag = onPaneDrag,
                            onPaneDragEnd = onPaneDragEnd,
                            modifier = Modifier.weight(normalizedWeights[index])
                        )
                        if (index < node.children.size - 1) {
                            Splitter(
                                orientation = SplitOrientation.Horizontal,
                                thickness = splitterThickness,
                                onDrag = { dragAmount ->
                                    val newWeights = node.weights.toMutableList()
                                    // Use the captured splitContainerSize for totalSize
                                    val totalSize = splitContainerSize.height.toFloat()
                                    val deltaRatio = dragAmount / totalSize

                                    val weightBefore = newWeights[index]
                                    val weightAfter = newWeights[index + 1]

                                    val newWeightBefore = (weightBefore + deltaRatio).coerceAtLeast(0.01f)
                                    val newWeightAfter = (weightAfter - deltaRatio).coerceAtLeast(0.01f)

                                    val adjustment = (weightBefore + weightAfter) - (newWeightBefore + newWeightAfter)
                                    newWeights[index] = newWeightBefore + adjustment / 2
                                    newWeights[index + 1] = newWeightAfter + adjustment / 2

                                    newWeights[index] = newWeights[index].coerceAtLeast(0.01f)
                                    newWeights[index + 1] = newWeights[index + 1].coerceAtLeast(0.01f)

                                    onSplitterDrag(node.id, newWeights)
                                }
                            )
                        }
                    }
                }
            }
        }
        is LayoutNode.Tabbed -> TODO()
    }
}

// --- 5. Splitter Composable ---

/**
 * A draggable splitter that allows resizing of adjacent panes.
 * @param orientation The orientation of the splitter (Vertical for horizontal resizing, Horizontal for vertical resizing).
 * @param thickness The visual thickness of the splitter.
 * @param onDrag Callback providing the drag amount.
 */
@Composable
private fun Splitter(
    orientation: SplitOrientation,
    thickness: Dp,
    onDrag: (Float) -> Unit
) {
    val draggableState = rememberDraggableState { delta ->
        onDrag(delta)
    }

    Spacer(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.outlineVariant) // Neutral color for splitter
            .run {
                if (orientation == SplitOrientation.Vertical) {
                    width(thickness).fillMaxHeight()
                } else {
                    height(thickness).fillMaxWidth()
                }
            }
            .draggable(
                state = draggableState,
                orientation = if (orientation == SplitOrientation.Vertical) Orientation.Horizontal else Orientation.Vertical
            )
    )
}

// --- 6. Helper Functions for Layout Tree Manipulation ---

/**
 * Recursively updates the weights of a specific Split node in the layout tree.
 * Returns a new LayoutNode tree with the updated split.
 */
private fun updateSplitWeights(
    root: LayoutNode,
    splitIdToUpdate: String,
    newWeights: List<Float>
): LayoutNode {
    return when (root) {
        is LayoutNode.Pane -> root
        is LayoutNode.Split -> {
            if (root.id == splitIdToUpdate) {
                root.copy(weights = newWeights)
            } else {
                root.copy(children = root.children.map {
                    updateSplitWeights(it, splitIdToUpdate, newWeights)
                })
            }
        }
        is LayoutNode.Tabbed -> TODO()
    }
}

/**
 * Helper to find a pane's content by ID (for drag visual).
 */
private fun findPaneContent(root: LayoutNode, paneId: String): Pair<String, @Composable () -> Unit>? {
    return when (root) {
        is LayoutNode.Pane -> {
            if (root.id == paneId) root.title to root.content else null
        }
        is LayoutNode.Split -> {
            root.children.firstNotNullOfOrNull { findPaneContent(it, paneId) }
        }
        is LayoutNode.Tabbed -> TODO()
    }
}

/**
 * Applies a drop operation to the layout tree.
 * This function orchestrates the removal of the dragged pane from its original location
 * and its insertion into the new location based on the drop target.
 *
 * @param root The current root of the LayoutNode tree.
 * @param draggedPaneId The ID of the pane that was dragged.
 * @param draggedPaneContent The Composable content of the dragged pane.
 * @param draggedPaneTitle The title of the dragged pane.
 * @param dropTarget The calculated DropTarget indicating where the pane should be dropped.
 * @return A new LayoutNode tree after applying the drop operation.
 */
private fun applyDrop(
    root: LayoutNode,
    draggedPaneId: String,
    draggedPaneContent: @Composable () -> Unit,
    draggedPaneTitle: String,
    dropTarget: DropTarget
): LayoutNode {
    // If the drop target is NONE, or if the dragged pane is dropped onto itself, do nothing.
    if (dropTarget.type == DropTargetType.NONE || draggedPaneId == dropTarget.targetNodeId) {
        return root
    }

    // 1. Create the new pane object that will be inserted
    val newPane = LayoutNode.Pane(id = draggedPaneId, title = draggedPaneTitle, content = draggedPaneContent)

    // 2. Remove the dragged pane from its original location
    // This also handles collapsing empty splits or promoting single children.
    val (layoutWithoutDragged, _) = removePaneFromLayout(root, draggedPaneId)
    // If the dragged pane was the root and removed, layoutWithoutDragged might be null.
    // In that case, the newPane becomes the root if there's a valid drop target.
    val currentLayout = layoutWithoutDragged ?: LayoutNode.Split(orientation = SplitOrientation.Horizontal, children = emptyList(), weights = emptyList())

    // 3. Insert the dragged pane into the new location based on dropTarget
    return insertPaneIntoLayout(currentLayout, newPane, dropTarget)
}

/**
 * Recursively removes a pane from the layout tree.
 * Handles collapsing of splits if they become empty or have only one child after removal.
 *
 * @param node The current node being traversed.
 * @param paneIdToRemove The ID of the pane to remove.
 * @return A Pair where:
 * - first: The new LayoutNode tree (or null if the current node was removed/collapsed).
 * - second: The Pane that was removed (or null if not found in this branch).
 */
private fun removePaneFromLayout(
    node: LayoutNode,
    paneIdToRemove: String
): Pair<LayoutNode?, LayoutNode.Pane?> {
    return when (node) {
        is LayoutNode.Pane -> {
            if (node.id == paneIdToRemove) {
                null to node // Found and removed, return null for its place in the tree
            } else {
                node to null // Not the pane to remove
            }
        }
        is LayoutNode.Split -> {
            var removedPane: LayoutNode.Pane? = null
            val newChildren = mutableListOf<LayoutNode>()
            val newWeights = mutableListOf<Float>()

            node.children.forEachIndexed { index, child ->
                val (updatedChild, foundPane) = removePaneFromLayout(child, paneIdToRemove)
                if (foundPane != null) {
                    removedPane = foundPane // Pane found in a child branch
                }

                if (updatedChild != null) {
                    newChildren.add(updatedChild)
                    newWeights.add(node.weights[index]) // Keep original weight for remaining children
                }
            }

            if (removedPane != null) {
                // Logic to collapse the split if necessary
                return when (newChildren.size) {
                    0 -> null to removedPane // Split is now empty, remove it
                    1 -> newChildren.first() to removedPane // Split has one child, promote the child
                    else -> node.copy(children = newChildren, weights = newWeights) to removedPane // Split still valid
                }
            } else {
                node to null // Pane not found in this branch
            }
        }
        is LayoutNode.Tabbed -> TODO()
    }
}

/**
 * Recursively inserts a new pane into the layout tree based on the drop target.
 *
 * @param root The current root of the LayoutNode tree.
 * @param newPane The pane to insert.
 * @param dropTarget The calculated DropTarget.
 * @return A new LayoutNode tree with the pane inserted.
 */
private fun insertPaneIntoLayout(
    root: LayoutNode,
    newPane: LayoutNode.Pane,
    dropTarget: DropTarget
): LayoutNode {
    // Handle the case where the root itself is the target for a split operation
    if (root.id == dropTarget.targetNodeId) {
        return when (dropTarget.type) {
            DropTargetType.SPLIT_LEFT -> LayoutNode.Split(
                orientation = SplitOrientation.Horizontal,
                children = listOf(newPane, root),
                weights = listOf(0.5f, 0.5f)
            )
            DropTargetType.SPLIT_RIGHT -> LayoutNode.Split(
                orientation = SplitOrientation.Horizontal,
                children = listOf(root, newPane),
                weights = listOf(0.5f, 0.5f)
            )
            DropTargetType.SPLIT_TOP -> LayoutNode.Split(
                orientation = SplitOrientation.Vertical,
                children = listOf(newPane, root),
                weights = listOf(0.5f, 0.5f)
            )
            DropTargetType.SPLIT_BOTTOM -> LayoutNode.Split(
                orientation = SplitOrientation.Vertical,
                children = listOf(root, newPane),
                weights = listOf(0.5f, 0.5f)
            )
            // If the root is the target for reorder, and it's a Pane, this scenario is invalid
            // or implies a new root split if it was the only pane.
            // For simplicity, we assume reorder only happens within existing splits.
            else -> root // Should not happen if dropTarget is valid
        }
    }

    return when (root) {
        is LayoutNode.Pane -> root // Cannot insert into a Pane directly, only split it (handled above)
        is LayoutNode.Split -> {
            val newChildren = mutableListOf<LayoutNode>()
            val newWeights = mutableListOf<Float>()

            root.children.forEachIndexed { index, child ->
                if (child.id == dropTarget.targetNodeId) {
                    // This child is the direct target of the drop
                    when (dropTarget.type) {
                        DropTargetType.SPLIT_LEFT -> {
                            newChildren.add(
                                LayoutNode.Split(
                                    orientation = SplitOrientation.Horizontal,
                                    children = listOf(newPane, child),
                                    weights = listOf(0.5f, 0.5f)
                                )
                            )
                            newWeights.add(root.weights[index])
                        }
                        DropTargetType.SPLIT_RIGHT -> {
                            newChildren.add(
                                LayoutNode.Split(
                                    orientation = SplitOrientation.Horizontal,
                                    children = listOf(child, newPane),
                                    weights = listOf(0.5f, 0.5f)
                                )
                            )
                            newWeights.add(root.weights[index])
                        }
                        DropTargetType.SPLIT_TOP -> {
                            newChildren.add(
                                LayoutNode.Split(
                                    orientation = SplitOrientation.Vertical,
                                    children = listOf(newPane, child),
                                    weights = listOf(0.5f, 0.5f)
                                )
                            )
                            newWeights.add(root.weights[index])
                        }
                        DropTargetType.SPLIT_BOTTOM -> {
                            newChildren.add(
                                LayoutNode.Split(
                                    orientation = SplitOrientation.Vertical,
                                    children = listOf(child, newPane),
                                    weights = listOf(0.5f, 0.5f)
                                )
                            )
                            newWeights.add(root.weights[index])
                        }
                        DropTargetType.REORDER_BEFORE -> {
                            // Insert newPane before the target child in the current split
                            if (root.id == dropTarget.targetParentId) { // Ensure it's the correct parent split
                                newChildren.add(newPane)
                                newWeights.add(0.5f) // Assign a default weight for the new pane
                                newChildren.add(child)
                                newWeights.add(root.weights[index])
                            } else {
                                newChildren.add(insertPaneIntoLayout(child, newPane, dropTarget))
                                newWeights.add(root.weights[index])
                            }
                        }
                        DropTargetType.REORDER_AFTER -> {
                            // Insert newPane after the target child in the current split
                            if (root.id == dropTarget.targetParentId) { // Ensure it's the correct parent split
                                newChildren.add(child)
                                newWeights.add(root.weights[index])
                                newChildren.add(newPane)
                                newWeights.add(0.5f) // Assign a default weight for the new pane
                            } else {
                                newChildren.add(insertPaneIntoLayout(child, newPane, dropTarget))
                                newWeights.add(root.weights[index])
                            }
                        }
                        else -> {
                            newChildren.add(child)
                            newWeights.add(root.weights[index])
                        }
                    }
                } else {
                    // Recursively call for children that are not the direct target
                    newChildren.add(insertPaneIntoLayout(child, newPane, dropTarget))
                    newWeights.add(root.weights[index])
                }
            }
            // Re-normalize weights after insertion if a new pane was added
            val finalTotalWeight = newWeights.sum()
            val finalNormalizedWeights = if (finalTotalWeight > 0) newWeights.map { it / finalTotalWeight } else newWeights
            root.copy(children = newChildren, weights = finalNormalizedWeights)
        }
        is LayoutNode.Tabbed -> TODO()
    }
}

/**
 * Calculates the potential drop target based on the current drag position.
 * This function determines which pane is being hovered over and which type of drop operation
 * (split, reorder) is implied by the cursor's position within that pane.
 *
 * @param rootLayout The current root of the LayoutNode tree.
 * @param draggedPaneId The ID of the pane currently being dragged.
 * @param currentDragPosition The absolute screen position of the cursor/touch.
 * @param paneBoundsMap A map of Pane IDs to their absolute screen Rect bounds.
 * @return A DropTarget object indicating the potential drop location, or null if no valid target.
 */
private fun calculateDropTarget(
    rootLayout: LayoutNode,
    draggedPaneId: String,
    currentDragPosition: Offset,
    paneBoundsMap: SnapshotStateMap<String, Rect>
): DropTarget? {
    // Iterate through all panes in the bounds map
    for ((paneId, rect) in paneBoundsMap) {
        // Don't consider the dragged pane itself as a drop target
        if (paneId == draggedPaneId) continue

        if (rect.contains(currentDragPosition)) {
            // Cursor is over this pane, now determine the specific drop zone
            val relativeX = (currentDragPosition.x - rect.left) / rect.width
            val relativeY = (currentDragPosition.y - rect.top) / rect.height

            // Define drop zone thresholds (e.g., 25% for split, 50% for reorder)
            val splitThreshold = 0.25f // 25% from edges for splitting
            val reorderThreshold = 0.5f // Middle 50% for reordering

            // Find the target pane's parent to get its orientation for reordering
            val targetPaneParent = findParentSplit(rootLayout, paneId)

            return when {
                // Split Left
                relativeX < splitThreshold -> DropTarget(rect, DropTargetType.SPLIT_LEFT, paneId)
                // Split Right
                relativeX > (1f - splitThreshold) -> DropTarget(rect, DropTargetType.SPLIT_RIGHT, paneId)
                // Split Top
                relativeY < splitThreshold -> DropTarget(rect, DropTargetType.SPLIT_TOP, paneId)
                // Split Bottom
                relativeY > (1f - splitThreshold) -> DropTarget(rect, DropTargetType.SPLIT_BOTTOM, paneId)
                // Reorder (middle zone)
                relativeX >= splitThreshold && relativeX <= (1f - splitThreshold) &&
                        relativeY >= splitThreshold && relativeY <= (1f - splitThreshold) -> {
                    if (targetPaneParent != null) {
                        // Determine if reorder before or after based on orientation
                        if (targetPaneParent.orientation == SplitOrientation.Horizontal) {
                            if (relativeX < reorderThreshold) {
                                DropTarget(rect, DropTargetType.REORDER_BEFORE, paneId, targetPaneParent.id)
                            } else {
                                DropTarget(rect, DropTargetType.REORDER_AFTER, paneId, targetPaneParent.id)
                            }
                        } else { // Vertical orientation
                            if (relativeY < reorderThreshold) {
                                DropTarget(rect, DropTargetType.REORDER_BEFORE, paneId, targetPaneParent.id)
                            } else {
                                DropTarget(rect, DropTargetType.REORDER_AFTER, paneId, targetPaneParent.id)
                            }
                        }
                    } else {
                        // If no parent split, it's a top-level pane, reorder is not directly applicable
                        // Could imply creating a new root split, but for now, treat as NONE
                        null
                    }
                }
                else -> null // No specific drop zone
            }
        }
    }
    return null // No pane is being hovered over
}

/**
 * Helper function to find the parent Split of a given node.
 * This is useful for reordering operations to ensure the reorder happens within the correct split.
 */
private fun findParentSplit(root: LayoutNode, targetNodeId: String, parent: LayoutNode.Split? = null): LayoutNode.Split? {
    return when (root) {
        is LayoutNode.Pane -> null // A pane has no children to search
        is LayoutNode.Split -> {
            if (root.children.any { it.id == targetNodeId }) {
                root // This split is the parent of the target node
            } else {
                root.children.firstNotNullOfOrNull { findParentSplit(it, targetNodeId, root) }
            }
        }

        is LayoutNode.Tabbed -> TODO()
    }
}
