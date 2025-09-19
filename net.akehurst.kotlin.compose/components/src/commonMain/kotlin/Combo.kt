package net.akehurst.kotlin.compose.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun <T> Combo(initValue: String, values: ()->Map<String, T>, onValueChange: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf(initValue) }
    var filteredValues by remember { mutableStateOf(values.invoke()) }

        TextField(
            value = text,
            onValueChange = {
                text = it
                filteredValues = values.invoke().filter { (key, _) -> key.contains(it, ignoreCase = true) }
                if (filteredValues.containsKey(text)) {
                    onValueChange(filteredValues[text]!!)
                }
                expanded = true
            },
            modifier = Modifier
                .padding(0.dp),
            trailingIcon = {
                Box {
                    Button(
                        onClick = { expanded = true },
                        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 0.dp),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier
                            .padding(0.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "Dropdown"
                        )
                    }
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surface),
                    ) {
                        filteredValues.forEach { (key, value) ->
                            DropdownMenuItem(
                                text = { Text(key) },
                                onClick = {
                                    text = key
                                    onValueChange(value)
                                    expanded = false
                                },
                            )
                        }
                    }
                }
            }
        )

}