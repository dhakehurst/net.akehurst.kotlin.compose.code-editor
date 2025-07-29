package net.akehurst.kotlin.compose.components.tree

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.ExperimentalCoroutinesApi

//TODO: use nak.kotlinx.Tree
data class TreeViewNode(
    val id: String
) {
    var content: @Composable () -> Unit = { Text(text = id, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    var hasChildren: Boolean = false
    var fetchChildren: suspend () -> List<TreeViewNode> = { emptyList() }
    var children by mutableStateOf<List<TreeViewNode>>(emptyList())
}

private fun toggleExpanded(expandedItems: MutableList<TreeViewNode>, node: TreeViewNode) {
    if (expandedItems.contains(node)) {
        expandedItems.remove(node)
    } else {
        expandedItems.add(node)
    }
}

@Composable
fun TreeView(
    modifier: Modifier = Modifier,
    state: TreeViewState,
    onSelectItem: (item: TreeViewNode) -> Unit = {},
    expanded: @Composable (Modifier) -> Unit = { modifier -> Icon(imageVector = Icons.AutoMirrored.Default.ArrowForward, contentDescription = "Close", modifier = modifier) },
    collapsed: @Composable (Modifier) -> Unit = { modifier -> Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = "Open", modifier = modifier) },
) {

    val expandedItems = remember { mutableStateListOf<TreeViewNode>() }

    LazyColumn(
        state = state.lazyListState
    ) {
        nodes(
            level = 0,
            nodes = state.items,
            isExpanded = {
                expandedItems.contains(it)
            },
            toggleExpanded = {
                if (expandedItems.contains(it)) {
                    expandedItems.remove(it)
                } else {
                    expandedItems.add(it)
                }
            },
            onSelectItem,
            expanded,
            collapsed
        )
    }
}

fun LazyListScope.nodes(
    level: Int,
    nodes: List<TreeViewNode>,
    isExpanded: (TreeViewNode) -> Boolean,
    toggleExpanded: (TreeViewNode) -> Unit,
    onSelectItem: (TreeViewNode) -> Unit,
    expanded: @Composable (Modifier) -> Unit,
    collapsed: @Composable (Modifier) -> Unit
) {
    nodes.forEach { node ->
        node(
            level,
            node,
            isExpanded = isExpanded,
            toggleExpanded = toggleExpanded,
            onSelectItem = onSelectItem,
            expanded,
            collapsed
        )
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
fun LazyListScope.node(
    level: Int,
    node: TreeViewNode,
    isExpanded: (TreeViewNode) -> Boolean,
    toggleExpanded: (TreeViewNode) -> Unit,
    onSelectItem: (TreeViewNode) -> Unit,
    expanded: @Composable (Modifier) -> Unit,
    collapsed: @Composable (Modifier) -> Unit
) {
    item {
        Row(
            modifier = Modifier
                .clickable { onSelectItem.invoke(node) }
        ) {
            Spacer(Modifier.width((20 * level).dp))
            when {
                node.hasChildren -> when {
                    isExpanded(node) -> expanded.invoke(Modifier.clickable { toggleExpanded(node) })
                    else -> collapsed.invoke(Modifier.clickable { toggleExpanded(node) })
                }

                else -> Spacer(Modifier.width(20.dp))
            }
            node.content.invoke()
        }
        LaunchedEffect(isExpanded(node)) {
            node.children = node.fetchChildren.invoke()
        }
    }
    if (isExpanded(node)) {
        nodes(
            level = level + 1,
            node.children,
            isExpanded = isExpanded,
            toggleExpanded = toggleExpanded,
            onSelectItem,
            expanded,
            collapsed
        )
    }
}

@Stable
class TreeViewState {

    var items by mutableStateOf(listOf(TreeViewNode("<no content>")))
    val lazyListState = LazyListState()

    fun setNewItems(newItems: List<TreeViewNode>) {
        items = newItems
    }
}