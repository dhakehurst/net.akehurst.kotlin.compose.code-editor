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

// suppress these so we can access TextFieldScrollerPosition
@file:Suppress("UNUSED", "INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package net.akehurst.kotlin.compose.editor

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.MyCoreTextField
import androidx.compose.foundation.text.MygetLineForVerticalPosition
import androidx.compose.foundation.text.TextFieldScrollerPosition
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.text.*
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.launch
import kotlin.math.max

interface EditorLineToken {
    val style: SpanStyle
    val start: Int
    val end: Int
}

interface AutocompleteItem {
    val text: String
    val name: String

    fun equalTo(other:AutocompleteItem):Boolean
}

interface AutocompleteSuggestion {
    fun provide(items: List<AutocompleteItem>)
}

typealias LineTokensFunction = ((lineNumber: Int, lineText: String) -> List<EditorLineToken>)
typealias AutocompleteFunction = suspend (position: Int, text: String, result: AutocompleteSuggestion) -> Unit

data class CursorDetails(
    val brush: SolidColor,
    var start: Offset,
    var end: Offset
) {
    fun update(top: Offset, height: Float) {
        start = top
        end = Offset(top.x, top.y + height)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CodeEditor(
    initialText: String = "",
    onTextChange: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    getLineTokens: LineTokensFunction = { _, _ -> emptyList() },
    requestAutocompleteSuggestions: AutocompleteFunction = { _, _, _ -> },
) {
    val scope = rememberCoroutineScope()
    val defaultTextStyle = SpanStyle(color = MaterialTheme.colorScheme.onBackground)
    val state by remember { mutableStateOf(EditorState(initialText, requestAutocompleteSuggestions)) }
    val inputScrollerPosition = rememberSaveable(Orientation.Vertical, saver = TextFieldScrollerPosition.Saver) { TextFieldScrollerPosition(Orientation.Vertical) }

    fun handleCtrlKey(key: Key):Boolean =when (key) {
            Key.Spacebar -> { // autocomplete
                scope.launch { state.autocompleteState.open() }
                true
            }
            Key.Escape -> {
                state.autocompleteState.isVisible = false
                true
            }
            Key.Enter -> {
                if(state.autocompleteState.isVisible) {
                    state.autocompleteState.chooseSelected()
                    true
                } else {
                    false
                }
            }
            else -> false
        }

    Surface {
        Column(modifier) {
            Surface(
                modifier = Modifier.weight(1f, true),
                color = MaterialTheme.colorScheme.background
            ) {
                Box {
                    CompositionLocalProvider(
                        LocalTextSelectionColors provides TextSelectionColors(
                            handleColor = LocalTextSelectionColors.current.handleColor,
                            backgroundColor = Color.Red
                        )
                    ) {
                        // Visible Viewport
                        // A CoreTextField that displays the styled text
                        // just the subsection of text that is visible is formatted
                        // The 'input' CoreTextField is transparent and sits on top of this.
                        MyCoreTextField(
                            readOnly = false,
                            enabled = true,
                            value = state.viewTextValue,
                            onValueChange = { state.viewTextValue = it },
                            onTextLayout = {},
                            onScroll = {
                                // update the drawn cursor position
                                if (it != null) {
                                    state.viewCursorRect = it.getCursorRect(state.viewTextValue.selection.start)
                                    val cr = state.viewCursorRect
                                    state.viewCursors[0].update(cr.topCenter, cr.height)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .drawWithContent {
                                    drawContent()
                                    // draw the cursors
                                    // (can't see how to make the actual cursor visible unless the control has focus)
                                    state.viewCursors.forEach {
                                        drawLine(
                                            strokeWidth = 3f,
                                            brush = it.brush,
                                            start = it.start,
                                            end = it.end
                                        )
                                    }
                                },
                            //cursorBrush = SolidColor(MaterialTheme.colorScheme.onBackground),
                        )
                    }

                    // The input CoreTextField
                    // sits on top receives user interactions
                    // and contains the whole text - with no syling
                    // Transparent
                    CompositionLocalProvider(
                        // make selections transparent in the in
                        LocalTextSelectionColors provides TextSelectionColors(
                            handleColor = LocalTextSelectionColors.current.handleColor,
                            backgroundColor = Color.Transparent
                        )
                    ) {
                        MyCoreTextField(
                            cursorBrush = SolidColor(Color.Transparent),
                            textStyle = TextStyle(color = Color.Transparent),
                            value = state.inputTextValue,
                            onValueChange = {
                                onTextChange(it.text)
                                state.inputTextValue = it
                            },
                            onTextLayout = {
                                state.viewTextValue = state.viewTextValue.copy(selection = state.viewSelection)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .onKeyEvent { ev ->
                                    if (ev.isCtrlPressed) {
                                        handleCtrlKey(ev.key)
                                    } else {
                                        false
                                    }
                                },
                            textScrollerPosition = inputScrollerPosition,
                            onScroll = { textLayoutResult ->
                                if (textLayoutResult != null) {
                                    val st = inputScrollerPosition.offset
                                    val len = inputScrollerPosition.viewportSize
                                    val firstLine = textLayoutResult.MygetLineForVerticalPosition(st)
                                    val lastLine = textLayoutResult.MygetLineForVerticalPosition(st + len)//-1
                                    val firstPos = textLayoutResult.getLineStart(firstLine)
                                    state.viewFirstLinePos = firstPos
                                    val lastPos = textLayoutResult.getLineEnd(lastLine)
                                    val viewText = state.inputTextValue.text.substring(firstPos, lastPos)
                                    val annotated = buildAnnotatedString {
                                        for (lineNum in firstLine..lastLine) {
                                            val lineStartPos = textLayoutResult.getLineStart(lineNum)
                                            val lineFinishPos = textLayoutResult.getLineEnd(lineNum)
                                            val lineText = state.inputTextValue.text.substring(lineStartPos, lineFinishPos)
                                            if (lineNum != firstLine) {
                                                append("\n")
                                            }
                                            append(lineText)
                                            addStyle(
                                                defaultTextStyle,
                                                lineStartPos - firstPos,
                                                lineFinishPos - firstPos
                                            )
                                            val toks = try {
                                                getLineTokens(lineNum, lineText)
                                            } catch (t: Throwable) {
                                                //TODO: log error!
                                                emptyList()
                                            }
                                            for (tk in toks) {
                                                val offsetStart = tk.start - firstPos
                                                val offsetFinish = tk.end - firstPos
                                                addStyle(tk.style, offsetStart, offsetFinish)
                                            }
                                        }
                                    }
                                    val sel = state.inputTextValue.selection //.toView(textLayoutResult)
                                    state.viewTextValue = state.inputTextValue.copy(annotatedString = annotated, selection = sel)
                                }
                            }
                        )
                    }

                    // for autocomplete popup
                    AutocompletePopup(
                        state = state.autocompleteState,
                        offset = state.autocompleteOffset
                    )
                }
            }
        }
    }
}

@Stable
internal class EditorState(
    initialText: String,
    requestAutocompleteSuggestions: AutocompleteFunction
) {
    var inputTextValue by mutableStateOf(TextFieldValue(initialText))
    var viewTextValue by mutableStateOf(TextFieldValue(""))
    var viewFirstLinePos by mutableStateOf(0)
    var viewCursorRect by mutableStateOf(Rect.Zero)
    val viewCursors by mutableStateOf(mutableListOf(CursorDetails(SolidColor(Color.Red), Offset.Zero, Offset.Zero)))
    val autocompleteState by mutableStateOf(AutocompleteState(this, requestAutocompleteSuggestions))
    val autocompleteOffset by mutableStateOf(IntOffset(-1, -1))

    val inputText get() = inputTextValue.text
    val inputSelection get() = inputTextValue.selection
    val viewSelection: TextRange
        get() {
            val range = inputTextValue.selection
            val s = range.start
            val e = range.end
            return TextRange(
                max(0, range.start - viewFirstLinePos),
                max(0, range.end - viewFirstLinePos)
            )
        }

}