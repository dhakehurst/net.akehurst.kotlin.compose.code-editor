package net.akehurst.kotlin.compose.components.vtabs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.akehurst.kotlin.compose.components.table.SelectiveRoundedCornerShape

@Composable
fun VerticalTabbedPane(
    tabNames: List<String>,
    paneModifier: Modifier = Modifier,
    tabListModifier: Modifier = Modifier,
    tabContent: @Composable (tabName: String) -> Unit = { Text(text = it, modifier = Modifier.padding(5.dp)) },
    body: @Composable (selectedTab: Int) -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    Row(
        modifier = paneModifier
    ) {
        Column(
            modifier = tabListModifier
        ) {
            tabNames.forEachIndexed { index, tabName ->
                VerticalTab(tabName, index == selectedTab, onClick = { selectedTab = index }, tabContent)
            }
        }
        body.invoke(selectedTab)
    }
}

@Composable
fun VerticalTab(tabName: String, isSelected: Boolean, onClick: () -> Unit, content: @Composable (tabName: String) -> Unit) {
    val shape = SelectiveRoundedCornerShape(
        cornerRadius = 5.dp,
        hasRight = isSelected.not(),
        topRightCurved = false,
        bottomRightCurved = false,
    )
    val bg = if (isSelected) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceDim
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .border(width = 1.dp, color = MaterialTheme.colorScheme.onBackground, shape = shape)
            .background(bg)
    ) {
        content.invoke(tabName)
    }
}