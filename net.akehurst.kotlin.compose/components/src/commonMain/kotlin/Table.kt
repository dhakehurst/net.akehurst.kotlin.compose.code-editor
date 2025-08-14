package net.akehurst.kotlin.compose.components.table

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier

@Stable
class TableState {

}

@Composable
fun TableView(
    state: TableState,
    modifier: Modifier = Modifier,
    content: TableScope.() -> Unit = {}
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
    ) {
        val tableScope = object : TableScope, LazyListScope by this {}
        content.invoke(tableScope)
    }
}
interface TableScope : LazyListScope {}
interface TableRowScope : RowScope {}

fun TableScope.tableHeader(rowModifier: Modifier = Modifier, content: @Composable TableRowScope.() -> Unit = {}){
    item {
        Row(modifier = rowModifier) {
            val tableRowScope = object : TableRowScope, RowScope by this {}
            content.invoke(tableRowScope)
        }
    }
}

fun TableScope.tableRow(rowModifier: Modifier = Modifier, content: @Composable TableRowScope.() -> Unit = {}){
    item {
        Row(modifier = rowModifier) {
            val tableRowScope = object : TableRowScope, RowScope by this {}
            content.invoke(tableRowScope)
        }
    }
}

@Composable
fun TableRowScope.tableCell(boxModifier: Modifier = Modifier, content: @Composable (() -> Unit)) {
    Box(modifier = boxModifier) {
        content.invoke()
    }
}