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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.akehurst.kotlin.compose.editor.api.AutocompleteFunction
import net.akehurst.kotlin.compose.editor.api.AutocompleteItem
import net.akehurst.kotlin.compose.editor.api.AutocompleteState
import net.akehurst.kotlin.compose.editor.api.AutocompleteSuggestion

fun String.fixLength(maxLen: Int) = when {
    maxLen < 0 -> this
    maxLen > this.length -> this
    else -> this.substring(0, maxLen - 1) + "\u2026"
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun AutocompletePopup(
    modifier: Modifier = Modifier,
    state: AutocompleteStateCompose
) {
    if (state.isVisible) {
        Surface(
            shadowElevation = 1.dp,
            border = BorderStroke(Dp.Hairline, MaterialTheme.colorScheme.onSurface),
            modifier = modifier
                //.fillMaxWidth()
                .offset { state.getMenuOffset() }
                .padding(vertical = 2.dp)
                //.wrapContentSize(unbounded = true)
        ) {
            Column(
                modifier = Modifier
                    //.fillMaxWidth()
//                    .width(300.dp)
                    .heightIn(min = 30.dp, max = 250.dp)

            ) {
                // List
                LazyColumn(
                    state = state.lazyListState
                ) {
                    itemsIndexed(state.items) { idx, item ->
                        Row(
                            modifier = Modifier
                                .background(color = if (state.isSelected(idx)) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
                                .padding(top = 2.dp, bottom = 3.dp, start = 2.dp, end = 2.dp)
                                .onClick(
                                    onClick = { state.choose(item) },
                                )
                        ) {
                            Text(
                                item.text.fixLength(state.numTextCharactersToShow),
                                color = if (state.isSelected(idx)) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.width(20.dp))
                            item.label?.let {
                                Text("(${it.fixLength(10)})")
                            }
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
class AutocompleteStateCompose(
    //val editorState: EditorState,
    val getText: () -> CharSequence,
    val getCursorPosition: () -> Int,
    val getMenuOffset: () -> IntOffset,
    val insertText: (String) -> Unit,
    val requestAutocompleteSuggestions: AutocompleteFunction
) : AutocompleteState {
    var scope: CoroutineScope? = null

    var numTextCharactersToShow by mutableStateOf(-1)
    val lazyListState = LazyListState()
    override var isVisible by mutableStateOf(false)
    override var isLoading by mutableStateOf(true)
    var selectedIndex by mutableStateOf<Int>(-1)
    var items = mutableStateListOf<AutocompleteItem>()

    //val scrollState by mutableStateOf(ScrollState(0))

    val selectedItem get() = items.getOrNull(selectedIndex)

    fun open() {
        isVisible = true
        isLoading = true
        requestSuggestions()
    }

    override fun clear() {
        items.clear()
    }

    fun scrollToSelected() {
        scope?.launch {
            lazyListState.scrollToItem(selectedIndex, -20)
        }
    }

    fun selectNext() {
        when {
            selectedIndex < (items.size - 1) -> {
                selectedIndex++
                scrollToSelected()
            }

            else -> Unit
        }
    }

    fun selectPrevious() {
        when {
            selectedIndex > 0 -> {
                selectedIndex--
                scrollToSelected()
            }

            else -> Unit
        }
    }

    fun choose(item: AutocompleteItem) {
        selectedIndex = items.indexOf(item)
        chooseSelected()
    }

    fun isSelected(idx: Int): Boolean = selectedIndex == idx

    fun chooseSelected() {
        val textToInsert = this.selectedItem?.text ?: ""
        insertText(textToInsert)
        close()
    }

    fun close() {
        isVisible = false
        items.clear()
        selectedIndex = -1
    }

    fun handleKey(ev: KeyEvent): Boolean {
        var handled = true
        when {
            ev.isCtrlSpace -> requestSuggestions()
            else -> when (ev.key) {
                Key.Escape -> this.close()
                Key.Enter -> this.chooseSelected()
                Key.DirectionDown -> this.selectNext()
                Key.DirectionUp -> this.selectPrevious()
                else -> handled = false
            }
        }
        return handled
    }

    private fun requestSuggestions() {
        items.clear()
        val result = object : AutocompleteSuggestion {
            override fun provide(items: List<AutocompleteItem>) {
                this@AutocompleteStateCompose.items.addAll(items)
                isLoading = false
                if (items.isNotEmpty()) {
                    selectedIndex = 0
                }
            }
        }
        scope?.launch {
            requestAutocompleteSuggestions.invoke(getCursorPosition(), getText(), result)
        }
    }
}