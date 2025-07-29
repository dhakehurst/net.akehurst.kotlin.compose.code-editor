package net.akehurst.kotlin.compose.layout.multipane

import androidx.compose.runtime.Composable
import androidx.compose.ui.layout.Layout

/**
 * Defines the orientation of a Split.
 */
enum class SplitOrientation { Horizontal, Vertical }

/**
 * Represents a single content pane in the layout.
 * @param id Unique identifier for the pane.
 * @param title The title displayed in the pane's header.
 * @param content The Composable content to display inside the pane.
 */
class Pane(
    val id: String = LayoutNode.generateID(),
    val title: String,
    val content: @Composable () -> Unit
) {
    var tabBounds: RectInWindow? = null
    var contentBounds: RectInWindow? = null

    fun asString(indent: String): String = """
        |${indent}pane ($id, $title) [$tabBounds, $contentBounds]
    """.trimMargin()

    // content must not be part of the identity: TODO: should title be part of it?
    override fun hashCode(): Int = id.hashCode()
    override fun equals(other: Any?): Boolean = when (other) {
        !is Pane -> false
        else -> id == other.id
    }
    override fun toString(): String = "Pane($id, $title) [$tabBounds, $contentBounds] "
}

/**
 * Represents a node in the layout tree. It can be either a Pane or a Split.
 */
sealed class LayoutNode {
    /**
     * only the root should ever be empty, otherwise layout nodes are collapsed away
     */
    object Empty : LayoutNode() {
        override val id: String = "<EMPTY>"

        override fun insertPaneIntoLayoutIterate(
            newPane: Pane,
            dropTarget: DropTarget
        ): LayoutNode {
            return LayoutNode.Tabbed(
                children = listOf(newPane)
            )
        }

        override fun insertPane(
            newPane: Pane,
            dropTarget: DropTarget
        ): LayoutNode {
            return LayoutNode.Tabbed(
                children = listOf(newPane)
            )
        }

        override fun asString(indent: String): String = "${indent}empty"
    }

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

        override fun insertPaneIntoLayoutIterate(newPane: Pane, dropTarget: DropTarget): LayoutNode {
            // copy to new layout
            val newChildren = mutableListOf<LayoutNode>()
            val newWeights = mutableListOf<Float>()
            children.forEachIndexed { index, child ->
                newChildren.add(child.insertPaneIntoLayout(newPane, dropTarget))
                newWeights.add(weights[index])
            }
            val finalTotalWeight = newWeights.sum()
            val finalNormalizedWeights = if (finalTotalWeight > 0) newWeights.map { it / finalTotalWeight } else newWeights
            return copy(children = newChildren, weights = finalNormalizedWeights)
        }

        override fun insertPane(newPane: Pane, dropTarget: DropTarget) = when (dropTarget) {
            is DropTarget.Tabbed ->  error("Should not happen $dropTarget")
            is DropTarget.Split ->  insertPaneViaNewSplit(newPane, dropTarget)
            is DropTarget.Reorder -> insertPaneViaReorder(newPane, dropTarget)
        }

        internal fun insertPaneViaNewSplit(newPane: Pane, dropTarget: DropTarget.Split): Split {
            val newChildren = mutableListOf<LayoutNode>()
            val newWeights = mutableListOf<Float>()
            val newTabbed = LayoutNode.Tabbed(children = listOf(newPane))
            children.forEachIndexed { index, child ->
                if(child.id==dropTarget.otherId) {
                    when (dropTarget.type) {
                        DropTarget.Split.Kind.LEFT -> {
                            newChildren.add(
                                LayoutNode.Split(
                                    orientation = SplitOrientation.Horizontal,
                                    children = listOf(newTabbed, child),
                                    weights = listOf(0.5f, 0.5f)
                                )
                            )
                            newWeights.add(weights[index])
                        }

                        DropTarget.Split.Kind.RIGHT -> {
                            newChildren.add(
                                LayoutNode.Split(
                                    orientation = SplitOrientation.Horizontal,
                                    children = listOf(child, newTabbed),
                                    weights = listOf(0.5f, 0.5f)
                                )
                            )
                            newWeights.add(weights[index])
                        }

                        DropTarget.Split.Kind.TOP -> {
                            newChildren.add(
                                LayoutNode.Split(
                                    orientation = SplitOrientation.Vertical,
                                    children = listOf(newTabbed, child),
                                    weights = listOf(0.5f, 0.5f)
                                )
                            )
                            newWeights.add(weights[index])
                        }

                        DropTarget.Split.Kind.BOTTOM -> {
                            newChildren.add(
                                LayoutNode.Split(
                                    orientation = SplitOrientation.Vertical,
                                    children = listOf(child, newTabbed),
                                    weights = listOf(0.5f, 0.5f)
                                )
                            )
                            newWeights.add(weights[index])
                        }
                    }
                } else {
                    newChildren.add(child)
                    newWeights.add(weights[index])
                }
            }
            // Re-normalize weights after insertion if a new pane was added
            val finalTotalWeight = newWeights.sum()
            val finalNormalizedWeights = if (finalTotalWeight > 0) newWeights.map { it / finalTotalWeight } else newWeights
            return copy(children = newChildren, weights = finalNormalizedWeights)
        }

        internal fun insertPaneViaReorder(newPane: Pane, dropTarget: DropTarget.Reorder): Split {
            val newChildren = mutableListOf<LayoutNode>()
            val newWeights = mutableListOf<Float>()
            val newTabbed = LayoutNode.Tabbed(children = listOf(newPane))
            children.forEachIndexed { index, child ->
                if (child.id == dropTarget.otherId) {
                    when (dropTarget.type) {
                        DropTarget.Reorder.Kind.BEFORE -> {
                            newChildren.add(newTabbed)
                            newWeights.add(0.5f) // Assign a default weight for the new pane
                            newChildren.add(child)
                            newWeights.add(weights[index])
                        }

                        DropTarget.Reorder.Kind.AFTER -> {
                            newChildren.add(child)
                            newWeights.add(weights[index])
                            newChildren.add(newTabbed)
                            newWeights.add(0.5f) // Assign a default weight for the new pane
                        }

                        else -> error("Should not happen")
                    }
                } else {
                    newChildren.add(child)
                    newWeights.add(weights[index])
                }
            }
            // Re-normalize weights after insertion if a new pane was added
            val finalTotalWeight = newWeights.sum()
            val finalNormalizedWeights = if (finalTotalWeight > 0) newWeights.map { it / finalTotalWeight } else newWeights
            return copy(children = newChildren, weights = finalNormalizedWeights)
        }

        override fun asString(indent: String): String = """
            |${indent}split ${orientation} ($id) {
            |${children.joinToString("\n") { it.asString("$indent  ") }}
            |${indent}}
        """.trimMargin()
    }

    data class Tabbed(
        override val id: String = generateID(),
        val children: List<Pane>,
        val selectedTabIndex: Int = 0
    ) : LayoutNode() {

        override fun insertPaneIntoLayoutIterate(newPane: Pane, dropTarget: DropTarget): LayoutNode  = this

        override fun insertPane(newPane: Pane, dropTarget: DropTarget) = when (dropTarget) {
            is DropTarget.Tabbed -> insertPaneViaAddTab( newPane, dropTarget)
            is DropTarget.Split -> insertPaneViaNewSplit( newPane, dropTarget)
            is DropTarget.Reorder -> error("Cannot Reorder a pane into a tabbed container: $dropTarget")
        }

        internal fun insertPaneViaAddTab(newPane: Pane, dropTarget: DropTarget.Tabbed): Tabbed {
            val newChildren = mutableListOf<Pane>()
            children.forEachIndexed { index, child ->
                if (child.id == dropTarget.otherId) {
                    when (dropTarget.type) {
                        DropTarget.Tabbed.Kind.BEFORE -> {
                            newChildren.add(newPane)
                            newChildren.add(child)
                        }

                        DropTarget.Tabbed.Kind.AFTER -> {
                            newChildren.add(child)
                            newChildren.add(newPane)
                        }
                    }
                } else {
                    newChildren.add(child)
                }
            }
            return copy(children = newChildren, selectedTabIndex = newChildren.size-1)
        }

        internal fun insertPaneViaNewSplit(newPane: Pane, dropTarget: DropTarget.Split): Split {
            val newTabbed = LayoutNode.Tabbed(children = listOf(newPane))
            return when (dropTarget.type) {
                DropTarget.Split.Kind.LEFT -> LayoutNode.Split(orientation = SplitOrientation.Horizontal, children = listOf(newTabbed, this), weights = listOf(0.5f, 0.5f))
                DropTarget.Split.Kind.RIGHT -> LayoutNode.Split(orientation = SplitOrientation.Horizontal, children = listOf(this, newTabbed), weights = listOf(0.5f, 0.5f))
                DropTarget.Split.Kind.TOP -> LayoutNode.Split(orientation = SplitOrientation.Vertical, children = listOf(newTabbed, this), weights = listOf(0.5f, 0.5f))
                DropTarget.Split.Kind.BOTTOM -> LayoutNode.Split(orientation = SplitOrientation.Vertical, children = listOf(this, newTabbed), weights = listOf(0.5f, 0.5f))
            }
        }

        override fun asString(indent: String): String = """
            |${indent}tabbed ($id) {
            |${children.joinToString("\n") { it.asString("$indent  ") }}
            |${indent}}
        """.trimMargin()
    }

    companion object {
        internal var next = 0
        fun generateID(): String = "id${next++}"
    }


    /**
     * Unique ID for each node
     */
    abstract val id: String

    abstract fun insertPaneIntoLayoutIterate(newPane: Pane, dropTarget: DropTarget): LayoutNode

    abstract fun insertPane(newPane: Pane, dropTarget: DropTarget): LayoutNode

    abstract fun asString(indent: String=""): String

    fun <R> firstPaneOfOrNull(parentSplit: Split? = null, transform: (Split?, Tabbed, pane: Pane) -> R?): R? {
        return when (this) {
            is LayoutNode.Empty -> null
            is Tabbed -> this.children.firstNotNullOfOrNull { transform(parentSplit, this, it) }
            is Split -> this.children.firstNotNullOfOrNull { it.firstPaneOfOrNull(this, transform) }
        }
    }

    /**
     * Recursively inserts a new pane into the layout tree based on the drop target.
     *
     * @param newPane The pane to insert.
     * @param dropTarget The calculated DropTarget.
     * @return A new LayoutNode tree with the pane inserted.
     */
    fun insertPaneIntoLayout(newPane: Pane, dropTarget: DropTarget): LayoutNode {
        return when {
            this.id == dropTarget.targetNodeId -> this.insertPane(newPane, dropTarget)
            else -> insertPaneIntoLayoutIterate(newPane, dropTarget)
        }
    }


}

