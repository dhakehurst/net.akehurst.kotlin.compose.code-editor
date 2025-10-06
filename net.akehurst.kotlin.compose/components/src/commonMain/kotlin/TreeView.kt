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
import net.akehurst.kotlin.compose.components.flowHolder.mutableStateFlowHolderOf

//TODO: use nak.kotlinx.Tree
data class TreeViewNode(
    val id: String
) {
    var content: @Composable () -> Unit = { Text(text = id, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    var hasChildren: Boolean = false
    var fetchChildren: suspend () -> List<TreeViewNode> = { emptyList() }
    var children = mutableStateFlowHolderOf<List<TreeViewNode>>(emptyList())

    val data:MutableMap<String,Any?> = mutableMapOf()
}

@Stable
class TreeViewStateHolder(

) {
    var items = mutableStateFlowHolderOf(listOf(TreeViewNode("<no content>")))
    val lazyListState = LazyListState()

    fun updateItems(newItems: List<TreeViewNode>) {
        items.update { newItems }
    }

}

/*
private fun toggleExpanded(expandedItems: MutableList<TreeViewNode>, node: TreeViewNode) {
    if (expandedItems.contains(node)) {
        expandedItems.remove(node)
    } else {
        expandedItems.add(node)
    }
}
*/

@Composable
fun TreeView(
    stateHolder: TreeViewStateHolder,
    onSelectItem: (item: TreeViewNode) -> Unit = {},
    expanded: @Composable (Modifier) -> Unit = { modifier -> Icon(imageVector = Icons.AutoMirrored.Default.ArrowForward, contentDescription = "Close", modifier = modifier) },
    collapsed: @Composable (Modifier) -> Unit = { modifier -> Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = "Open", modifier = modifier) },
    modifier: Modifier = Modifier,
) {

    val expandedItems = remember { mutableStateListOf<TreeViewNode>() }
    val nodes = stateHolder.items.collectAsState()
    LazyColumn(
        state = stateHolder.lazyListState,
        modifier = modifier,
    ) {
        nodes(
            level = 0,
            nodes = nodes.value,
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
            val fetched = node.fetchChildren.invoke()
            node.children.update { fetched }
        }
    }
    if (isExpanded(node)) {
        nodes(
            level = level + 1,
            node.children.value,
            isExpanded = isExpanded,
            toggleExpanded = toggleExpanded,
            onSelectItem,
            expanded,
            collapsed
        )
    }
}