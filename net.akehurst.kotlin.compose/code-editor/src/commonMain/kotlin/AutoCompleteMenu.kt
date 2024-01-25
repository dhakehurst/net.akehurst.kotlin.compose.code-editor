/**
 * Copyright (C) 2024 Dr. David H. Akehurst (http://dr.david.h.akehurst.net)
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

package net.akehurst.kotlin.compose.editor

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun AutocompletePopup(
    state: AutocompleteState,
    offset: IntOffset
) {
    if (state.isVisible) {
        Surface(
            shadowElevation = 1.dp,
            border = BorderStroke(Dp.Hairline, MaterialTheme.colorScheme.onSurface),
            modifier = Modifier
                .absoluteOffset({ offset })
                .widthIn(min = 150.dp, max = 300.dp)
                .padding(vertical = 2.dp)
        ) {
            Column {
                // List
                LazyColumn(
                    modifier = Modifier
                        .onKeyEvent { ev ->
                            when (ev.key) {
                                Key.Escape -> {
                                    state.close()
                                    true
                                }

                                Key.Enter -> {
                                    state.chooseSelected()
                                    true
                                }

                                else -> false
                            }
                        }
                ) {
                    itemsIndexed(state.items) { _, item ->
                        Row(
                            modifier = Modifier
                                .background(color = if (state.isSelected(item)) MaterialTheme.colorScheme.onBackground else Color.Transparent)
                                .padding(top = 2.dp, bottom = 3.dp, start = 2.dp, end = 2.dp)
                                .onClick(
                                    onClick = { state.choose(item) },
                                )
                        ) {
                            Text(item.text)
                            Spacer(Modifier.width(8.dp))
                            Text(item.name)
                        }
                    }
                }
                when {
                    state.isLoading -> {
                        CircularProgressIndicator()
                    }

                    state.items.isEmpty() -> {
                        Text("No suggestions")
                    }
                }
            }
        }
    }
}

@Stable
internal class AutocompleteState(
    val editorState: EditorState,
    val requestAutocompleteSuggestions: AutocompleteFunction
) {
    var isVisible by mutableStateOf(false)
    var isLoading by mutableStateOf(true)
    var selection by mutableStateOf<AutocompleteItem?>(null)
    val items = mutableStateListOf<AutocompleteItem>()

    suspend fun open() {
        isVisible = true
        isLoading = true
        val result = object : AutocompleteSuggestion {
            override fun provide(items: List<AutocompleteItem>) {
                this@AutocompleteState.items.addAll(items)
                isLoading = false
            }
        }
        requestAutocompleteSuggestions.invoke(editorState.inputSelection.start, editorState.inputText, result)
    }

    fun choose(item: AutocompleteItem) {
        selection = item
        chooseSelected()
    }

    fun isSelected(item: AutocompleteItem): Boolean = null != selection && item.equalTo(selection!!)

    fun chooseSelected() {

        close()
    }

    fun close() {
        isVisible = false
        items.clear()
        selection = null
    }
}