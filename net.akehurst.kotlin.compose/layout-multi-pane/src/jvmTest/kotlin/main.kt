package net.akehurst.kotlin.compose.layout.multipane

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.singleWindowApplication
import kotlin.test.Test


class test_MultiPaneLayout {

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun main() {
        val mainPane = LayoutNode.Pane(title = "Pane A") { Text("Pane A content", style = MaterialTheme.typography.headlineLarge) }
        // initial layout
        var layoutState = MultiPaneLayoutState(
            LayoutNode.Split(
                orientation = SplitOrientation.Horizontal,
                weights = listOf(0.4f, 0.6f), // Left half vs Right half
                children = listOf(
                    LayoutNode.Split(
                        orientation = SplitOrientation.Vertical,
                        weights = listOf(0.5f, 0.5f),
                        children = listOf(
                            mainPane,
                            LayoutNode.Pane(title = "Pane B") { Text("Pane B content", style = MaterialTheme.typography.headlineLarge) }
                        ),
                    ),
                    LayoutNode.Split(
                        orientation = SplitOrientation.Vertical,
                        weights = listOf(0.7f, 0.3f),
                        children = listOf(
                            LayoutNode.Pane(title = "Pane C") { Text("Pane C content", style = MaterialTheme.typography.headlineLarge) },
                            LayoutNode.Pane(title = "Pane D") { Text("Pane D content", style = MaterialTheme.typography.headlineLarge) }
                        )
                    )
                ),
            )
        )

        singleWindowApplication(
            title = "Demo MultiPaneLayout",
        ) {
            MaterialTheme {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("Demo MultiPaneLayout") },
                            actions = {
                                Button(
                                    onClick = {
                                        layoutState.rootLayout = mainPane.addPane( layoutState.rootLayout, LayoutNode.Pane(title = "New Pane") { Text("New Pane content", style = MaterialTheme.typography.headlineLarge) })
                                    }
                                ) {
                                    Text("Add Pane")
                                }
                            }
                        )
                    },
                    bottomBar = {
                        BottomAppBar(
                            modifier = Modifier
                                .padding(0.dp)
                                .height(20.dp)
                                .border(width = 1.dp, color = MaterialTheme.colorScheme.onBackground)
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(0.dp)
                            ) {

                            }
                        }
                    },
                ) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .padding(innerPadding)
                    ) {
                        MultiPaneLayout(
                            layoutState = layoutState,
                            onLayoutChanged = { newLayout -> layoutState.rootLayout = newLayout }
                        )
                    }
                }
            }
        }
    }
}


