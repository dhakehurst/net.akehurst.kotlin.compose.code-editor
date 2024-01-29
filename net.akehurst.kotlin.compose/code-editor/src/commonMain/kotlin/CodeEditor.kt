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
import kotlin.math.min

interface EditorLineToken {
    /**
     * style for this segment of text
     */
    val style: SpanStyle

    /**
     * starting offset in the line for this segment. Lines start at offset 0
     */
    val start: Int

    /**
     * finish offset (exclusive) in the line for this segment. Lines start at offset 0
     */
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

typealias LineTokensFunction = ((lineNumber: Int, lineStartPosition:Int, lineText: String) -> List<EditorLineToken>)
typealias AutocompleteFunction = suspend (position: Int, text: String, result: AutocompleteSuggestion) -> Unit

data class CursorDetails(
    val brush: SolidColor
) {
    var inView = true
    var position = 0
    var rect = Rect.Zero

    //val top get() = rect.topCenter
    //val bot get() = Offset(top.x, top.y + rect.height)
    fun updatePos(newPos: Int, inView: Boolean) {
        this.inView = inView
        this.position = newPos
    }

    fun updateRect(newRect: Rect) {
        this.rect = newRect
    }
}

val KeyEvent.isCtrlSpace
    get() = if ((key == Key.Spacebar || utf16CodePoint == ' '.toInt()) && isCtrlPressed) {
        true
    } else {
        false
    }

//FIXME: bug on JS getLineEnd does not work - workaround

class LineMetrics(
    initialText: String
) {
    companion object {
        val eol = Regex("\n")
    }

    var lineEndsAt = eol.findAll(initialText).toList()

    val lineCount get() = lineEndsAt.size + 1
    val firstPosition = 0
    var lastPosition = initialText.length; private set

    fun update(newText: String) {
        lastPosition = newText.length
        lineEndsAt = eol.findAll(newText).toList()
    }

    fun lineStart(lineNumber: Int) = when {
        0 == lineNumber -> 0
        lineNumber >= (lineCount) -> lastPosition
        else -> lineEndsAt[lineNumber - 1].range.last + 1
    }

    fun lineFinish(lineNumber: Int) = when {
        lineNumber >= (lineCount - 1) -> lastPosition
        else -> lineEndsAt[lineNumber].range.first
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CodeEditor(
    initialText: String = "",
    defaultTextStyle: SpanStyle = SpanStyle(color = MaterialTheme.colorScheme.onBackground),
    onTextChange: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    getLineTokens: LineTokensFunction = { _, _, _ -> emptyList() },
    requestAutocompleteSuggestions: AutocompleteFunction = { _, _, _ -> },
) {

    val scope = rememberCoroutineScope()
    val state by remember {
        mutableStateOf(
            EditorState(
                initialText,
                defaultTextStyle = defaultTextStyle,
                getLineTokens,
                requestAutocompleteSuggestions
            )
        )
    }

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
                    enabled = false,
                    value = state.viewTextValue,
                    onValueChange = { state.viewTextValue = it },
                    onTextLayout = {},
                    onScroll = { if (null != it) state.updateViewCursorRect(it) },
                    modifier = Modifier
                        .fillMaxSize()
                        .drawWithContent {
                            drawContent()
                            // draw the cursors
                            // (can't see how to make the actual cursor visible unless the control has focus)
                            state.viewCursors.forEach {
                                if (it.inView) {
                                    drawLine(
                                        strokeWidth = 3f,
                                        brush = it.brush,
                                        start = it.rect.topCenter,
                                        end = it.rect.bottomCenter
                                    )
                                }
                            }
                        },
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
                        state.lineMetrics.value.update(it.text)
                    },
                    onTextLayout = { },
                    modifier = Modifier
                        .fillMaxSize()
                        .onPreviewKeyEvent { ev -> handlePreviewKeyEvent(ev) },
                    textScrollerPosition = state.inputScrollerPosition,
                    onScroll = { textLayoutResult ->
                        if (textLayoutResult != null) {
                            state.updateView(textLayoutResult)
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
    val defaultTextStyle: SpanStyle,
    val getLineTokens: LineTokensFunction = { _, _, _ -> emptyList() },
    requestAutocompleteSuggestions: AutocompleteFunction = { _, _, _ -> }
) {


    //FIXME: bug on JS getLineEnd does not work - workaround
    val lineMetrics = mutableStateOf(LineMetrics(initialText))

    val inputScrollerPosition by mutableStateOf(TextFieldScrollerPosition(Orientation.Vertical))
    var inputTextValue by mutableStateOf(TextFieldValue(initialText))
    var viewTextValue by mutableStateOf(TextFieldValue(""))
    var viewFirstLinePos by mutableStateOf(0)
    var viewLastLinePos by mutableStateOf(0)
    val viewCursors by mutableStateOf(mutableListOf(CursorDetails(SolidColor(Color.Red))))
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

    fun updateView(inputTextLayoutResult: TextLayoutResult) {
        if (0 == inputScrollerPosition.viewportSize) {
            // must have been set
        } else {
            val newText = inputTextLayoutResult.layoutInput.text
            val st = inputScrollerPosition.offset
            val len = inputScrollerPosition.viewportSize
            val firstLine = inputTextLayoutResult.MygetLineForVerticalPosition(st)
            val lastLine = inputTextLayoutResult.MygetLineForVerticalPosition(st + len)//-1
            //val firstPos = inputTextLayoutResult.getLineStart(firstLine)
            //val lastPos = textLayoutResult.getLineEnd(lastLine)

            //FIXME: bug on JS getLineEnd does not work - workaround using own line metrics
            val fp = lineMetrics.value.lineStart(firstLine)
            val lp = lineMetrics.value.lineFinish(lastLine)

            viewFirstLinePos = fp //firstPos
            viewLastLinePos = lp
                            println("View: lines [$firstLine-$lastLine] pos[$fp-$lp]")
            //val viewText = state.inputTextValue.text.substring(firstPos, lastPos)
            val annotated = buildAnnotatedString {
                for (lineNum in firstLine..lastLine) {
                    //val lineStartPos = textLayoutResult.getLineStart(lineNum)
                    //val lineFinishPos = textLayoutResult.getLineEnd(lineNum)
                    //FIXME: bug on JS getLineEnd does not work - workaround
                    val lineStartPos = lineMetrics.value.lineStart(lineNum)
                    val lineFinishPos = lineMetrics.value.lineFinish(lineNum)
                                    println("Line $lineNum: [$lineStartPos-$lineFinishPos]")
                    val lineText = newText.substring(lineStartPos, lineFinishPos)
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
                        getLineTokens(lineNum, lineStartPos, lineText)
                    } catch (t: Throwable) {
                        //TODO: log error!
                        emptyList()
                    }
                    for (tk in toks) {
                                        println("tok: [${tk.start}-${tk.finish}]")
                        val offsetStart = (lineStartPos+tk.start).coerceIn(lineStartPos, lineFinishPos+1)
                        val offsetFinish = (lineStartPos+tk.finish).coerceIn(lineStartPos, lineFinishPos+1)
                        addStyle(tk.style, offsetStart, offsetFinish)
                    }
                }
            }
            val sel = inputTextValue.selection.toView()
            updateViewCursorPos()
            viewTextValue = inputTextValue.copy(annotatedString = annotated, selection = sel)
        }
    }

    fun TextRange.toView(): TextRange {
        val start = (inputTextValue.selection.start - viewFirstLinePos).coerceIn(0, viewLastLinePos)
        val end = (inputTextValue.selection.end - viewFirstLinePos).coerceIn(0, viewLastLinePos)
        return TextRange(start, end)
    }

    fun updateViewCursorPos() {
        // update the drawn cursor position
        val selStart = inputTextValue.selection.start// - viewFirstLinePos
        val (vss, inView) = when {
            selStart < viewFirstLinePos -> Pair(0, false)
            selStart > viewLastLinePos -> Pair(viewLastLinePos - viewFirstLinePos, false)
            else -> Pair(selStart - viewFirstLinePos, true)
        }
        viewCursors[0].updatePos(vss, inView)
    }

    fun updateViewCursorRect(viewTextLayoutResult: TextLayoutResult) {
        // update the drawn cursor position
        val pos = viewCursors[0].position
        val p = min(pos, viewTextLayoutResult.layoutInput.text.length)
        val cr = viewTextLayoutResult.getCursorRect(p)
        viewCursors[0].updateRect(cr)
    }


    fun insertText(text: String) {
        val pos = viewCursors[0].position
        val before = this.inputText.substring(0, pos)
        val after = this.inputText.substring(pos)
        val newText = before + text + after
        this.inputTextValue = this.inputTextValue.copy(text = newText)
    }
}