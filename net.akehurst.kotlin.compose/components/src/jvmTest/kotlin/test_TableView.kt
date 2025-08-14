package net.akehurst.kotlin.compose.components.table

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material.Text
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.singleWindowApplication
import kotlin.test.Test


class test_TableView {
    @Test
    fun main() {
        singleWindowApplication(
            title = "Demo MultiPaneLayout",
        ) {
            val state = remember { TableState() }
            TableView(
                state,
                modifier = Modifier
                    .border(width = 1.dp, color = Color.Black)
                    .background(Color.Green)
            ) {
                tableHeader(
                    rowModifier = Modifier
                        .border(width = 1.dp, color = Color.Black)
                        .background(Color.Red)
                ) {
                    tableCell(boxModifier = Modifier
                        .border(width = 1.dp, color = Color.Black)
                        .weight(0.1f)) { Text("Column 1") }
                    tableCell(boxModifier = Modifier
                        .border(width = 1.dp, color = Color.Black)
                        .weight(0.1f)) { Text("Column 2") }
                }
                tableRow {
                    tableCell {}
                }
            }
        }
    }
}