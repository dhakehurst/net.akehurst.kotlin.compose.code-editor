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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
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
import net.akehurst.kotlin.compose.layout.multipane.LayoutNode.Companion.generateID
import kotlin.math.roundToInt

/**
 * Represents a single content pane in the layout.
 * @param id Unique identifier for the pane.
 * @param title The title displayed in the pane's header.
 * @param content The Composable content to display inside the pane.
 */
data class Pane(
    val id: String = generateID(),
    val title: String,
    val content: @Composable () -> Unit
) {
    var tabBounds: Rect? = null
    var contentBounds: Rect? = null
}


/**
 * Defines the orientation of a Split.
 */
enum class SplitOrientation { Horizontal, Vertical }

/**
 * Represents the state of a drag operation for a pane.
 * @param isDragging True if a pane is currently being dragged.
 * @param draggedPaneId The ID of the pane being dragged.
 * @param initialTouchOffsetInPane The offset from the top-left of the *entire pane* to the initial touch point, in local coordinates.
 * @param currentTouchScreenPosition The current absolute screen position of the touch point.
 * @param paneContent The Composable content of the dragged pane for visual representation.
 * @param paneTitle The title of the dragged pane for visual representation.
 * @param paneBounds The bounds of the pane being dragged.
 */
data class DragState(
    val isDragging: Boolean = false,
    val draggedPaneId: String?,
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
val LocalDragState: ProvidableCompositionLocal<MutableState<DragState?>> = compositionLocalOf { mutableStateOf(null) }

/**
 * Represents a potential drop target during a drag operation.
 * This would be used for the "preview" ghosting.
 */
sealed class DropTarget {
    data class Tabbed(
        override val rect: Rect,
        val type: Kind, // Insert new Pane as Tab BEFORE / AFTER otherId
        override val targetNodeId: String, // The ID of the node being dropped into
        val otherId: String? = null // The ID of the pane/tabbed that is before/after
    ) : DropTarget() {
        enum class Kind { BEFORE, AFTER }

        override fun previewRect(offset: Offset): Rect = when (type) {
            DropTarget.Tabbed.Kind.BEFORE -> Rect(offset.x, offset.y, offset.x + 5, offset.y + rect.height)
            DropTarget.Tabbed.Kind.AFTER -> Rect(offset.x + rect.width - 5, offset.y, offset.x + rect.width, offset.y + rect.height)
        }

        override fun toString(): String = "DropTarget.Tabbed $type $targetNodeId $otherId $rect"
    }

    data class Split(
        override val rect: Rect,
        val type: Kind, // e.g., Split target (Tabbed) placing newPane/Tabbed BEFORE/AFTER otherId
        override val targetNodeId: String, // The ID of the node being dropped into
        val otherId: String? = null // The ID of the pane/tabbed that is before/after
    ) : DropTarget() {
        enum class Kind { LEFT, RIGHT, TOP, BOTTOM }

        override fun previewRect(offset: Offset): Rect = when (type) {
            Kind.LEFT -> Rect(offset.x, offset.y, offset.x + rect.width / 2, offset.y + rect.height)
            Kind.RIGHT -> Rect(offset.x + rect.width / 2, offset.y, offset.x + rect.width, offset.y + rect.height)
            Kind.TOP -> Rect(offset.x, offset.y, offset.x + rect.width, offset.y + rect.height / 2)
            Kind.BOTTOM -> Rect(offset.x, offset.y + rect.height / 2, offset.x + rect.width, offset.y + rect.height)
        }

        override fun toString(): String = "DropTarget.Split $type $targetNodeId $otherId $rect"
    }

    data class Reorder(
        override val rect: Rect,
        val type: Kind, // e.g., Reorder Split content placing target (Tabbed) BEFORE / AFTER otherId
        override val targetNodeId: String, // The ID of the node being dropped into
        val otherId: String? = null // The ID of the pane/tabbed that is before/after
    ) : DropTarget() {
        enum class Kind { BEFORE, AFTER }

        override fun previewRect(offset: Offset): Rect = when (type) {
            Kind.BEFORE -> Rect(offset.x, offset.y, offset.x + rect.width, offset.y + rect.height / 2)
            Kind.AFTER -> Rect(offset.x, offset.y + rect.height / 2, offset.x + rect.width, offset.y + rect.height)
        }

        override fun toString(): String = "DropTarget.Reorder $type $targetNodeId $otherId $rect"
    }

    abstract val rect: Rect
    abstract val targetNodeId: String?

    abstract fun previewRect(offset: Offset): Rect
}

/**
 * The main composable for the multi-window tiled layout.
 * Manages the root layout node and global drag state.
 * @param layoutState The MultiPaneLayoutState.
 * @param modifier Modifier for the overall layout.
 * @param splitterThickness The thickness of the splitters.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiPaneLayout(
    layoutState: MultiPaneLayoutState,
    modifier: Modifier = Modifier,
    splitterThickness: Dp = 8.dp
) {
    println("*** Composing ***")
    var state by remember { mutableStateOf(layoutState) }
    // dragState is now the MutableState<DragState> object
    val dragState: MutableState<DragState?> = remember { mutableStateOf(null) }

    // State for the potential drop target (for preview ghosting)
    var potentialDropTarget by remember { mutableStateOf<DropTarget?>(null) }

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
                state = state,
                node = state.rootLayout,
                splitterThickness = splitterThickness,
                onSplitterDrag = { splitId, newWeights ->
                    val newLayout = state.updateSplitWeights(state.rootLayout, splitId, newWeights)
                    state.setRootLayout(newLayout)
                },
                // Pass callback to update pane bounds map
                onPaneBoundsChanged = { paneId, isTab, rect ->
                    // if (isTab) println("** onPaneBoundsChanged: $paneId, $isTab, $rect")
                    //    state.paneBoundsMap[Pair(isTab, paneId)] = rect
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
                    val ds = dragState.value?.copy(currentTouchScreenPosition = currentScreenPosition)
                    dragState.value = ds
                    // Calculate potential drop target based on current drag position and pane bounds
                    val dt = state.calculateDropTarget(
                        rootLayout = state.rootLayout,
                        draggedPaneId = ds!!.draggedPaneId!!,
                        currentDragPosition = currentScreenPosition,
                        //    paneBoundsMap = state.paneBoundsMap
                    )
                    potentialDropTarget = dt
                },
                onPaneDragEnd = {
                    // Implement the actual layout modification based on potentialDropTarget
                    val ds = dragState.value!!
                    if (potentialDropTarget != null && ds.draggedPaneId != null) {
                        state.applyDrop(
                            root = state.rootLayout,
                            draggedPaneId = ds.draggedPaneId,
                            draggedPaneContent = ds.paneContent!!,
                            draggedPaneTitle = ds.paneTitle,
                            dropTarget = potentialDropTarget!!
                        )

                    }
                    dragState.value = null // Corrected: Reset the value
                    potentialDropTarget = null // Clear preview
                }
            )

            // Render the floating dragged pane visual
            val ds = dragState.value
            if (null != ds && ds.isDragging && ds.draggedPaneId != null) {
                val renderPosition = ds.currentTouchScreenPosition - ds.initialTouchOffsetInPane
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
                        .width(with(LocalDensity.current) { ds.paneBounds.width.toDp() })
                        .height(with(LocalDensity.current) { ds.paneBounds.height.toDp() })
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .border(1.dp, MaterialTheme.colorScheme.primary)
                    ) {
                        Text(
                            text = dragState.value!!.paneTitle, // Corrected: Access value
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(8.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Drop ${potentialDropTarget}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                    }
                }
            }

            potentialDropTarget?.let { target ->
                // Convert target rect bounds from window coordinates to local coordinates of the root Box
                val offset = rootLayoutCoordinates?.windowToLocal(target.rect.topLeft) ?: target.rect.topLeft
                val rect = target.previewRect(offset)
                Box(
                    modifier = Modifier
//                        .offset { IntOffset(localTargetRect.x.roundToInt(), localTargetRect.y.roundToInt()) } // Use localTargetRect
//                        .size(with(LocalDensity.current) { target.rect.width.toDp() }, with(LocalDensity.current) { target.rect.height.toDp() })
                        .offset { IntOffset(rect.left.roundToInt(), rect.top.roundToInt()) } // Use localTargetRect
                        .size(with(LocalDensity.current) { rect.width.toDp() }, with(LocalDensity.current) { rect.height.toDp() })
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                        .border(2.dp, MaterialTheme.colorScheme.primary)
                        .zIndex(99f) // Below dragged pane, above regular content
                )
            }
        }
    }
}

interface MultiPaneLayoutIcons {
    val Close: ImageVector
}

interface MultiPaneLayoutTheme {
    val icons: MultiPaneLayoutIcons
}

object MultiPaneLayoutIconsDefault : MultiPaneLayoutIcons {
    private var _Close: ImageVector? = null
    override val Close: ImageVector
        get() {
            if (_Close != null) {
                return _Close!!
            }
            _Close = ImageVector.Builder(
                name = "Close",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 960f,
                viewportHeight = 960f
            ).apply {
                path(
                    fill = SolidColor(Color.Black),
                    fillAlpha = 1.0f,
                    stroke = null,
                    strokeAlpha = 1.0f,
                    strokeLineWidth = 1.0f,
                    strokeLineCap = StrokeCap.Butt,
                    strokeLineJoin = StrokeJoin.Miter,
                    strokeLineMiter = 1.0f,
                    pathFillType = PathFillType.NonZero
                ) {
                    moveTo(256f, 760f)
                    lineToRelative(-56f, -56f)
                    lineToRelative(224f, -224f)
                    lineToRelative(-224f, -224f)
                    lineToRelative(56f, -56f)
                    lineToRelative(224f, 224f)
                    lineToRelative(224f, -224f)
                    lineToRelative(56f, 56f)
                    lineToRelative(-224f, 224f)
                    lineToRelative(224f, 224f)
                    lineToRelative(-56f, 56f)
                    lineToRelative(-224f, -224f)
                    close()
                }
            }.build()
            return _Close!!
        }
}

object MultiPaneLayoutThemeDefault : MultiPaneLayoutTheme {
    override val icons: MultiPaneLayoutIcons = MultiPaneLayoutIconsDefault
}

@Stable
class MultiPaneLayoutState(
    initialLayout: LayoutNode,
    val theme: MultiPaneLayoutTheme = MultiPaneLayoutThemeDefault,
    val onLayoutChanged: (LayoutNode) -> Unit = {}
) {
    private var _rootLayout by mutableStateOf(initialLayout)
    val rootLayout: LayoutNode get() = _rootLayout

    internal fun setRootLayout(value: LayoutNode) {
        _rootLayout = value
        // update paneBoundsMap to only contain the ids in layout
//        val currentPaneIds = getAllLayoutAndPaneIds()
//        val mapPaneIds = paneBoundsMap.keys.map { it.second }.toSet()
//        val stalePaneIds = mapPaneIds - currentPaneIds
//        if (stalePaneIds.isNotEmpty()) {
//            paneBoundsMap.keys.removeAll { it.second in stalePaneIds }
//        }
        onLayoutChanged.invoke(value)
    }

    /**
     * Recursively updates the weights of a specific Split node in the layout tree.
     * Returns a new LayoutNode tree with the updated split.
     */
    internal fun updateSplitWeights(
        root: LayoutNode,
        splitIdToUpdate: String,
        newWeights: List<Float>
    ): LayoutNode {
        return when (root) {
            is LayoutNode.Split -> {
                if (root.id == splitIdToUpdate) {
                    root.copy(weights = newWeights)
                } else {
                    root.copy(children = root.children.map {
                        updateSplitWeights(it, splitIdToUpdate, newWeights)
                    })
                }
            }

            is LayoutNode.Tabbed -> root
        }
    }

    fun getAllPaneIds(): Set<String> {
        val ids = mutableSetOf<String>()
        val queue = mutableListOf<LayoutNode>(rootLayout)

        while (queue.isNotEmpty()) {
            when (val currentNode = queue.removeAt(0)) {
                is LayoutNode.Split -> queue.addAll(currentNode.children)
                is LayoutNode.Tabbed -> currentNode.children.forEach { pane -> ids.add(pane.id) }
            }
        }
        return ids
    }

    fun getAllLayoutAndPaneIds(): Set<String> {
        val ids = mutableSetOf<String>()
        val queue = mutableListOf<LayoutNode>(rootLayout)
        while (queue.isNotEmpty()) {
            val currentNode = queue.removeAt(0)
            ids.add(currentNode.id)
            when (currentNode) {
                is LayoutNode.Split -> queue.addAll(currentNode.children)
                is LayoutNode.Tabbed -> currentNode.children.forEach { pane -> ids.add(pane.id) }
            }
        }
        return ids
    }

    fun findPaneOrNull(paneId: String): Pane? = findPaneOrNull { it.id == paneId }

    fun addPane(targetTabbedId: String, afterId: String, newPane: Pane) {
        val dropTarget = DropTarget.Tabbed(
            rect = Rect.Zero,
            type = DropTarget.Tabbed.Kind.AFTER,
            targetNodeId = targetTabbedId,
            otherId = afterId
        )
        val layout = rootLayout.insertPaneIntoLayout(newPane, dropTarget)
        setRootLayout(layout)
    }

    fun removePane(paneId: String) {
        val (layoutWithoutDragged, _) = removePaneFromLayout(rootLayout, paneId)
        setRootLayout(layoutWithoutDragged ?: rootLayout)
    }

    fun selectTab(tabbedNodeId: String, index: Int) {
        setRootLayout(layoutWithSelectedTab(rootLayout, tabbedNodeId, index))
    }

    fun findPaneOrNull(predicate: (Pane) -> Boolean): Pane? = rootLayout.firstPaneOfOrNull { split, tabbed, pane ->
        if (predicate.invoke(pane)) pane else null
    }

    fun findTabbedOrNull(predicate: (LayoutNode.Tabbed) -> Boolean): LayoutNode.Tabbed? = rootLayout.firstPaneOfOrNull { split, tabbed, pane ->
        if (predicate.invoke(tabbed)) tabbed else null
    }

    fun findSplitOrNull(predicate: (LayoutNode.Split) -> Boolean): LayoutNode.Split? = rootLayout.firstPaneOfOrNull { split, tabbed, pane ->
        if (null != split && predicate.invoke(split)) split else null
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
    internal fun applyDrop(
        root: LayoutNode,
        draggedPaneId: String,
        draggedPaneContent: @Composable () -> Unit,
        draggedPaneTitle: String,
        dropTarget: DropTarget
    ) {
        // If the drop target is NONE, or if the dragged pane is dropped onto itself, do nothing.
        if (draggedPaneId == dropTarget.targetNodeId) {
            return
        } else {
            // 1. Create the new pane object that will be inserted
            val newPane = Pane(id = draggedPaneId, title = draggedPaneTitle, content = draggedPaneContent)

            // 2. Remove the dragged pane from its original location
            // This also handles collapsing empty splits or promoting single children.
            val (layoutWithoutDragged, _) = removePaneFromLayout(root, draggedPaneId)
            // If the dragged pane was the root and removed, layoutWithoutDragged might be null.
            // In that case, the newPane becomes the root if there's a valid drop target.
            val newLayout = layoutWithoutDragged ?: LayoutNode.Split(orientation = SplitOrientation.Horizontal, children = emptyList(), weights = emptyList())

            // 3. Insert the dragged pane into the new location based on dropTarget
            val layout = newLayout.insertPaneIntoLayout(newPane, dropTarget)
            setRootLayout(layout)
        }
    }

    /**
     * Recursively removes a pane from the layout tree.
     * Handles collapsing of splits if they become empty or have only one child after removal.
     *
     * @param parent The current node being traversed.
     * @param paneIdToRemove The ID of the pane to remove.
     * @return A Pair where:
     * - first: The new LayoutNode tree (or null if the current node was removed/collapsed).
     * - second: The Pane that was removed (or null if not found in this branch).
     */
    private fun removePaneFromLayout(
        parent: LayoutNode,
        paneIdToRemove: String
    ): Pair<LayoutNode?, Pane?> {
        return when (parent) {
            is LayoutNode.Split -> {
                var removedPane: Pane? = null
                val newChildren = mutableListOf<LayoutNode>()
                val newWeights = mutableListOf<Float>()

                parent.children.forEachIndexed { index, child ->
                    val (updatedChild, foundPane) = removePaneFromLayout(child, paneIdToRemove)
                    if (foundPane != null) {
                        removedPane = foundPane // Pane found in a child branch
                    }

                    if (updatedChild != null) {
                        newChildren.add(updatedChild)
                        newWeights.add(parent.weights[index]) // Keep original weight for remaining children
                    }
                }

                if (removedPane != null) {
                    // Logic to collapse the split if necessary
                    return when (newChildren.size) {
                        0 -> null to removedPane // Split is now empty, remove it
                        1 -> newChildren.first() to removedPane // Split has one child, promote the child
                        else -> parent.copy(children = newChildren, weights = newWeights) to removedPane // Split still valid
                    }
                } else {
                    parent to null // Pane is not found in this branch
                }
            }

            is LayoutNode.Tabbed -> {
                var removedPane: Pane? = null
                val newChildren = mutableListOf<Pane>()
                parent.children.forEachIndexed { index, child ->
                    if (child.id == paneIdToRemove) {
                        removedPane = child
                    } else {
                        newChildren.add(child)
                    }
                }
                when {
                    null == removedPane -> Pair(parent, null) // Pane is not found in this branch
                    else -> when (newChildren.size) {
                        0 -> Pair(null, removedPane) // Tabbed is empty, remove it
                        else -> Pair(parent.copy(children = newChildren), removedPane)
                    }
                }
            }
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
    internal fun calculateDropTarget(
        rootLayout: LayoutNode,
        draggedPaneId: String,
        currentDragPosition: Offset
    ): DropTarget? {
        // Iterate through all panes
        return rootLayout.firstPaneOfOrNull { split, tabbed, pane ->
            //if (draggedPaneId == pane.id) {
            //    null
            //} else {
            when {
                (true == pane.tabBounds?.contains(currentDragPosition)) -> calculateDropTargetTabbed(tabbed, pane, currentDragPosition)
                (true == pane.contentBounds?.contains(currentDragPosition)) -> calculateDropTargetSplit(split, tabbed, pane, currentDragPosition)
                else -> null
            }
            //}
        }
    }

    internal fun calculateDropTargetTabbed(
        tabbed: LayoutNode.Tabbed,
        targetPane: Pane,
        currentDragPosition: Offset,
    ): DropTarget? {
        val rect = targetPane.tabBounds!!
        val relativeX = (currentDragPosition.x - rect.left) / rect.width
        val threshold = 0.5f
        return when {
            relativeX < threshold -> DropTarget.Tabbed(rect, DropTarget.Tabbed.Kind.BEFORE, tabbed.id, targetPane.id)
            relativeX > (1f - threshold) -> DropTarget.Tabbed(rect, DropTarget.Tabbed.Kind.AFTER, tabbed.id, targetPane.id)
            else -> null
        }
    }

    internal fun calculateDropTargetSplit(
        split: LayoutNode.Split?,
        tabbed: LayoutNode.Tabbed,
        targetPane: Pane,
        currentDragPosition: Offset
    ): DropTarget? {

        val rect = targetPane.contentBounds!!
        // Cursor is over this pane, now determine the specific drop zone
        val relativeX = (currentDragPosition.x - rect.left) / rect.width
        val relativeY = (currentDragPosition.y - rect.top) / rect.height
        // Define drop zone thresholds (e.g., 25% for split, 50% for reorder)
        val splitThreshold = 0.25f // 25% from edges for splitting
        val reorderThreshold = 0.5f // Middle 50% for reordering
        return when {
            // Split Left
            relativeX < splitThreshold -> DropTarget.Split(rect, DropTarget.Split.Kind.LEFT, tabbed.id)
            // Split Right
            relativeX > (1f - splitThreshold) -> DropTarget.Split(rect, DropTarget.Split.Kind.RIGHT, tabbed.id)
            // Split Top
            relativeY < splitThreshold -> DropTarget.Split(rect, DropTarget.Split.Kind.TOP, tabbed.id)
            // Split Bottom
            relativeY > (1f - splitThreshold) -> DropTarget.Split(rect, DropTarget.Split.Kind.BOTTOM, tabbed.id)
            // Reorder (middle zone)
            relativeX >= splitThreshold && relativeX <= (1f - splitThreshold) &&
                    relativeY >= splitThreshold && relativeY <= (1f - splitThreshold) -> when {
                null == split -> null
                else -> {
                    // Determine if reorder before or after based on orientation
                    if (split.orientation == SplitOrientation.Horizontal) {
                        if (relativeX < reorderThreshold) {
                            DropTarget.Reorder(rect, DropTarget.Reorder.Kind.BEFORE, split.id, tabbed.id)
                        } else {
                            DropTarget.Reorder(rect, DropTarget.Reorder.Kind.AFTER, split.id, tabbed.id)
                        }
                    } else { // Vertical orientation
                        if (relativeY < reorderThreshold) {
                            DropTarget.Reorder(rect, DropTarget.Reorder.Kind.BEFORE, split.id, tabbed.id)
                        } else {
                            DropTarget.Reorder(rect, DropTarget.Reorder.Kind.AFTER, split.id, tabbed.id)
                        }
                    }
                }
            }
            else -> null // No specific drop zone
        }
    }

    private fun layoutWithSelectedTab(root: LayoutNode, targetNodeId: String, index: Int): LayoutNode {
        return when (root) {
            is LayoutNode.Split -> if (root.id == targetNodeId) {
                root // id is a split not a tabbed
            } else {
                val newChildren = root.children.map { layoutWithSelectedTab(it, targetNodeId, index) }
                root.copy(children = newChildren)
            }

            is LayoutNode.Tabbed -> if (root.id == targetNodeId) root.copy(selectedTabIndex = index) else root
        }
    }

}

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
    state: MultiPaneLayoutState,
    node: LayoutNode,
    splitterThickness: Dp,
    onSplitterDrag: (String, List<Float>) -> Unit,
    onPaneBoundsChanged: (String, Boolean, Rect) -> Unit, // New callback
    onPaneDragStart: (String, String, @Composable () -> Unit, Offset, Offset, Rect) -> Unit,
    onPaneDrag: (Offset) -> Unit,
    onPaneDragEnd: () -> Unit,
    modifier: Modifier = Modifier
) {

    // State to hold the LayoutCoordinates of the drag handle Surface
    var dragHandleCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    when (node) {
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
                        key(child) {
                            LayoutNodeRenderer(
                                state = state,
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
                        key(child) {
                            LayoutNodeRenderer(
                                state = state,
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
        }

        is LayoutNode.Tabbed -> {
            Column(
                modifier = modifier
                    .fillMaxSize()
            ) {
                PrimaryScrollableTabRow(
                    edgePadding = 0.dp,
                    selectedTabIndex = node.selectedTabIndex,
                    modifier = Modifier
                        .border(width = Dp.Hairline, color = MaterialTheme.colorScheme.onBackground)
                        .height(30.dp)
                        .fillMaxWidth()
                ) {
                    node.children.forEachIndexed { idx, child ->
                        key(child) {
                            Tab(
                                selected = node.selectedTabIndex == idx,
                                onClick = {
                                    state.selectTab(node.id, idx)
                                },
                                content = {
                                    Row {
                                        Text(text = "${child.title} (${child.id})")
                                        Icon(
                                            imageVector = state.theme.icons.Close,
                                            contentDescription = null,
                                            modifier = Modifier
                                                .clickable { state.removePane(child.id) }
                                                .size(15.dp)
                                        )
                                    }
                                },
                                modifier = Modifier
                                    .border(width = Dp.Hairline, color = MaterialTheme.colorScheme.onBackground)
                                    .padding(10.dp)
                                    .onGloballyPositioned { coordinates ->
                                        dragHandleCoordinates = coordinates // Store coordinates of the drag handle
                                        child.tabBounds = coordinates.boundsInWindow()
                                    }
                                    .pointerInput(node.id) { // Use child.id as key to restart detector if ID changes
                                        detectDragGestures(
                                            onDragStart = { initialTouchOffsetInSurface ->
                                                val dragHandleCoords = dragHandleCoordinates
                                                if (dragHandleCoords == null) {
                                                    println("ERROR: dragHandleCoordinates is null on drag start for pane ${child.id}")
                                                    return@detectDragGestures // Cannot proceed without coordinates
                                                }
                                                // Get the absolute screen position of where the touch started
                                                // convert the local touch offset in handle to window coordinates
                                                val initialTouchScreenPosition = dragHandleCoords.localToWindow(initialTouchOffsetInSurface)
                                                // Get the absolute screen bounds of the *entire pane*
                                                // Use the stored paneBoundsInWindow, which is updated by the outer Card's onGloballyPositioned
                                                // Calculate the offset from the *pane's* top-left to the initial touchpoint
                                                val offsetRelativeToPaneTopLeft = initialTouchScreenPosition - child.tabBounds!!.topLeft
                                                onPaneDragStart(
                                                    child.id,
                                                    child.title,
                                                    child.content,
                                                    offsetRelativeToPaneTopLeft, // Corrected: Offset from pane's top-left to touch
                                                    initialTouchScreenPosition, // Absolute screen position of touch
                                                    child.contentBounds ?: Rect(0f, 0f, 50f, 50f) //.size.toIntSize() // Pass the actual size of the pane
                                                )
                                            },
                                            onDrag = { change: PointerInputChange, _: Offset ->
                                                val dragHandleCoords = dragHandleCoordinates
                                                if (dragHandleCoords == null) {
                                                    println("ERROR: dragHandleCoordinates is null on drag for pane ${child.id}")
                                                    return@detectDragGestures // Cannot proceed without coordinates
                                                }
                                                val currentScreenPosition = dragHandleCoords.localToWindow(change.position)
                                                // Update the current drag position based on pointer changes
                                                onPaneDrag(currentScreenPosition)
                                                change.consume() // Consume the event so it doesn't propagate
                                            },
                                            onDragEnd = { onPaneDragEnd() },
                                            onDragCancel = { onPaneDragEnd() }
                                        )
                                    },
                            )
                        }
                    }
                }
                // Pane Content
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .onGloballyPositioned { coordinates ->
                            val contentBounds = coordinates.boundsInWindow()
                            node.children.forEach { it.contentBounds = contentBounds }
                        }
                ) {
                    node.children.getOrNull(node.selectedTabIndex)?.content()
                }
            }
        }
    }
}

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
