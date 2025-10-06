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