package net.akehurst.kotlin.compose.components.table

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Stable
class TableState {
    val columnWidthPx = mutableStateMapOf<Int, Int>()
}

@Composable
fun TableView(
    state: TableState,
    tableModifier: Modifier = Modifier,
    headerModifier: Modifier = Modifier,
    bodyModifier: Modifier = Modifier,
    footerModifier: Modifier = Modifier,
    headerContent: @Composable TableHeaderScope.() -> Unit = {},
    footerContent: @Composable TableFooterScope.() -> Unit = {},
    bodyContent: TableBodyScope.() -> Unit = {}
) {
    Column(
        modifier = tableModifier
    ) {
        // header
        Row(
            modifier = headerModifier,
        ) {
            headerContent.invoke(object : TableHeaderScope, RowScope by this {
                @Composable
                override fun tableHeader(rowModifier: Modifier, content: @Composable (TableHeaderRowScope.() -> Unit)) {
                    content.invoke(object : TableHeaderRowScope, RowScope by this {
                        @Composable
                        override fun tableHeaderCell(column: Int, boxModifier: Modifier, content: @Composable BoxScope.() -> Unit) {
                            Box(modifier = boxModifier.onSizeChanged { size -> state.columnWidthPx[column] = size.width }) {
                                content.invoke(this)
                            }
                        }
                    })
                }
            })
        }
        // body
        LazyColumn(
            modifier = bodyModifier
        ) {
            bodyContent.invoke(object : TableBodyScope, LazyListScope by this {
                override fun tableRow(rowModifier:  @Composable ()->Modifier, content: @Composable (TableRowScope.() -> Unit)) {
                    item {
                        Row(modifier = rowModifier()) {
                            val tableRowScope = object : TableRowScope, RowScope by this {
                                @Composable
                                override fun tableCell(column: Int, boxModifier: Modifier, content: @Composable BoxScope.() -> Unit) {
                                    val widthPx = state.columnWidthPx[column] ?: 50
                                    val widthDp = with(LocalDensity.current) { widthPx.toDp() }
                                    Box(modifier = boxModifier.width(widthDp)) {
                                        content.invoke(this)
                                    }
                                }
                            }
                            content.invoke(tableRowScope)
                        }
                    }
                }
            })
        }
        // footer
        Row(
            modifier = footerModifier,
        ) {
            footerContent.invoke(object : TableFooterScope, RowScope by this {
                @Composable
                override fun tableFooter(rowModifier: Modifier, content: @Composable TableFooterRowScope.() -> Unit) {
                    content.invoke(object : TableFooterRowScope, RowScope by this {
                        @Composable
                        override fun tableFooterCell(boxModifier: Modifier, content: @Composable (BoxScope.() -> Unit)) {
                            Box(modifier = boxModifier) {
                                content.invoke(this)
                            }
                        }
                    })
                }
            })
        }
    }
}

interface TableScope : ColumnScope {}
interface TableHeaderScope : RowScope {
    @Composable
    fun tableHeader(rowModifier: Modifier = Modifier, content: @Composable TableHeaderRowScope.() -> Unit = {})
}

interface TableHeaderRowScope : RowScope {
    @Composable
    fun tableHeaderCell(column: Int, boxModifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit)
}

interface TableFooterScope : RowScope {
    @Composable
    fun tableFooter(rowModifier: Modifier = Modifier, content: @Composable TableFooterRowScope.() -> Unit = {})
}

interface TableFooterRowScope : RowScope {
    @Composable
    fun tableFooterCell(boxModifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit)
}

interface TableBodyScope : LazyListScope {
    fun tableRow(rowModifier:  @Composable ()->Modifier = {Modifier}, content: @Composable TableRowScope.() -> Unit = {})
}

interface TableRowScope : RowScope {
    @Composable
    fun tableCell(column: Int, boxModifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit)
}

