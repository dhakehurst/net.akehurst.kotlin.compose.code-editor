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
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.RowScopeInstance.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.MyCoreTextField
import androidx.compose.foundation.text.MygetLineForVerticalPosition
import androidx.compose.foundation.text.TextFieldScrollerPosition
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.*
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.max

interface EditorLineToken {
    val style: SpanStyle
    val start: Int
    val finish: Int
}

interface AutocompleteItem {
    val text: String
    val name: String

    fun equalTo(other: AutocompleteItem): Boolean
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

val KeyEvent.isCtrlSpace
    get() = if ((key == Key.Spacebar || utf16CodePoint == ' '.toInt()) && isCtrlPressed) {
        true
    } else {
        false
    }

//FIXME: bug on JS getLineEnd does not work - workaround
val rx = Regex("\n")
val String.lineEndsAt get() = rx.findAll(this+"\n")


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

    //FIXME: bug on JS getLineEnd does not work - workaround
    val lineEndsAt = remember { mutableStateListOf<MatchResult>(*initialText.lineEndsAt.toList().toTypedArray()) }

    fun handlePreviewKeyEvent(ev: KeyEvent): Boolean = when (ev.type) {
        KeyEventType.KeyDown -> when {
            state.autocompleteState.isVisible -> when (ev.key) {
                Key.Escape -> {
                    state.autocompleteState.close()
                    true
                }

                Key.Enter -> {
                    state.autocompleteState.chooseSelected()
                    true
                }

                Key.DirectionDown -> {
                    state.autocompleteState.selectNext()
                    true
                }

                Key.DirectionUp -> {
                    state.autocompleteState.selectPrevious()
                    true
                }

                else -> false
            }

            else -> when {
                ev.isCtrlSpace -> {
                    // autocomplete
                    scope.launch { state.autocompleteState.open() }
                    true
                }

                else -> false
            }
        }

        else -> when {
            ev.isCtrlSpace -> true
            else -> false
        }
    }

    Row(
        modifier = modifier.weight(1f)
    ) {
        LazyColumn(
            modifier = Modifier
                .width(20.dp)
                .fillMaxHeight()
                .background(color = Color.Yellow)
        ) {
            itemsIndexed(state.annotationsState.annotations) { idx, ann ->
                Row(
                ) { }
            }
        }
        //Text editing
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
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
 //                       .background(color = Color.Green)
                        .fillMaxSize()
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

                        //FIXME: bug on JS getLineEnd does not work - workaround
                        lineEndsAt.clear()
                        lineEndsAt.addAll(it.text.lineEndsAt)
                    },
                    onTextLayout = {
                        state.viewTextValue = state.viewTextValue.copy(selection = state.viewSelection)
                    },
                    modifier = Modifier
                        .fillMaxSize(),
 //                       .onPreviewKeyEvent { ev -> handlePreviewKeyEvent(ev) },
                    textScrollerPosition = inputScrollerPosition,
                    onScroll = { textLayoutResult ->
                        if (textLayoutResult != null) {
                            val st = inputScrollerPosition.offset
                            val len = inputScrollerPosition.viewportSize
                            val firstLine = textLayoutResult.MygetLineForVerticalPosition(st)
                            val lastLine = textLayoutResult.MygetLineForVerticalPosition(st + len)//-1
                            //val firstPos = textLayoutResult.getLineStart(firstLine)
                            //val lastPos = textLayoutResult.getLineEnd(lastLine)

                            //FIXME: bug on JS getLineEnd does not work - workaround
                            val fp = if (firstLine==0) {
                                0
                            } else {
                                lineEndsAt.getOrNull(firstLine-1)?.range?.last ?: -1
                            }
                            val lp = lineEndsAt.getOrNull(lastLine)?.range?.first ?: -1

                            state.viewFirstLinePos = fp //firstPos

                            //val viewText = state.inputTextValue.text.substring(firstPos, lastPos)
                            val annotated = buildAnnotatedString {
                                for (lineNum in firstLine..lastLine) {
                                    //val lineStartPos = textLayoutResult.getLineStart(lineNum)
                                    //val lineFinishPos = textLayoutResult.getLineEnd(lineNum)
                                    //FIXME: bug on JS getLineEnd does not work - workaround
                                    val lineStartPos = if (lineNum==0) {
                                        0
                                    } else {
                                        lineEndsAt.getOrNull(lineNum-1)?.range?.last ?: -1
                                    }
                                    val lineFinishPos = lineEndsAt.getOrNull(lineNum)?.range?.first ?: -1

                                    val lineText = state.inputTextValue.text.substring(lineStartPos, lineFinishPos)
                                    if (lineNum != firstLine) {
                                        append("\n")
                                    }
                                    append(lineText)
                                    addStyle(
                                        defaultTextStyle,
                                        lineStartPos - fp, //firstPos,
                                        lineFinishPos - fp //firstPos
                                    )
                                    val toks = try {
                                        getLineTokens(lineNum, lineText)
                                    } catch (t: Throwable) {
                                        //TODO: log error!
                                        emptyList()
                                    }
                                    for (tk in toks) {
                                        val offsetStart = tk.start - fp //firstPos
                                        val offsetFinish = tk.finish - fp //firstPos
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
        }
    }
    // for autocomplete popup
    AutocompletePopup(
        state = state.autocompleteState
    )
}

@Stable
internal class EditorState(
    initialText: String = "",
    requestAutocompleteSuggestions: AutocompleteFunction = { _, _, _ -> }
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

    val annotationsState by mutableStateOf(AnnotationState())
}