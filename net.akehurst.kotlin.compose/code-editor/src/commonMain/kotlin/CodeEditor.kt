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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.*
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.akehurst.kotlin.compose.editor.api.AutocompleteFunction
import net.akehurst.kotlin.compose.editor.api.EditorLineToken
import net.akehurst.kotlin.compose.editor.api.LineTokensFunction
import kotlin.math.min

data class CursorDetails(
    val brush: SolidColor,
    var rect: Rect,
    val inView: Boolean
) {
    //var inView = true
//    var position = 0
    //var rect = Rect.Zero

    //val top get() = rect.topCenter
    //val bot get() = Offset(top.x, top.y + rect.height)
//    fun updatePos(newPos: Int, inView: Boolean) {
//        this.inView = inView
//        this.position = newPos
//    }
//
    fun updateRect(newRect: Rect) {
        this.rect = newRect
    }
}

val KeyEvent.isCtrlSpace
    get() = (key == Key.Spacebar || utf16CodePoint == ' '.toInt()) && isCtrlPressed

//FIXME: bug on JS getLineEnd does not work - workaround

class LineMetrics(
    initialText: String
) {
    companion object {
        val eol = Regex("\n")
    }

    //private val mutex = Mutex()
    private var lineEndsAt = eol.findAll(initialText).toList()

    val lineCount get() = lineEndsAt.size + 1
    val firstPosition = 0
    var lastPosition = initialText.length; private set

//    fun update(newText: String) {
////        mutex.withLock {
//        lastPosition = newText.length
//        lineEndsAt = eol.findAll(newText).toList()
////        }
//    }

    fun lineForPosition(position: Int): Int {
        val ln = lineEndsAt.indexOfLast { it.range.first < position }
        return when (ln) {
            -1 -> 0 // position must be on first line
            else -> ln + 1
        }
    }

    /**
     * start of firstLine
     * finish of lastLine
     */
    fun viewEnds(firstLine: Int, lastLine: Int): Pair<Int, Int> {
//        mutex.withLock {
        val s = lineStart(firstLine)
        val f = lineFinish(lastLine)
        return Pair(s, f)
//        }
    }

    /**
     * line start and finish
     */
    fun lineEnds(lineNumber: Int): Pair<Int, Int> {
//        mutex.withLock {
        val s = lineStart(lineNumber)
        val f = lineFinish(lineNumber)
        return Pair(s, f)
//        }
    }

    /**
     * index/position in text where lineNumber starts (after previous line EOL)
     */
    fun lineStart(lineNumber: Int) = when {
        0 == lineNumber -> 0
        lineNumber >= (lineCount) -> lastPosition
        else -> lineEndsAt[lineNumber - 1].range.last + 1
    }

    /**
     * index/position in text where lineNumber finishes (before EOL)
     */
    fun lineFinish(lineNumber: Int) = when {
        lineNumber >= (lineCount - 1) -> lastPosition
        else -> lineEndsAt[lineNumber].range.first
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CodeEditor(
    onTextChange: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    editorState: EditorState = EditorState(
        initialText = "",
        defaultTextStyle = SpanStyle(color = MaterialTheme.colorScheme.onBackground),
        getLineTokens = { _, _, _ -> emptyList() },
        requestAutocompleteSuggestions = { _, _, _ -> },
    )
) {

    val scope = rememberCoroutineScope().also {
        editorState.scope = it
        editorState.currentRecomposeScope = currentRecomposeScope
    }
    val state by remember { mutableStateOf(editorState) }

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
                .background(color = Color.Transparent)
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
                    onValueChange = {
                        // nothing needed as 'input service' does not make the changes
                        // state.viewTextValue = it
                    },
                    onTextLayout = { },
                    onScroll = {
                        if (null != it) {
                            // state.viewLineMetrics.update(it.layoutInput.text.text)
                            state.updateViewCursorRect(it)
                            //editorState.viewTextLayoutResult = it
                        }
                    },
                    modifier = Modifier
                        //.fillMaxSize()
                        .matchParentSize()
                        .drawWithContent {
                            drawContent()
                            // draw the cursors
                            // (can't see how to make the actual cursor visible unless the control has focus)
                            state.viewCursor.let {
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
                        if (it.text != state.inputText) { // has text really changed !
                            onTextChange(it.text)
                        }
                        state.inputTextValue = it
                        //state.inputLineMetrics.update(it.text)
                    },
                    onTextLayout = { },
                    modifier = Modifier
                        .matchParentSize()
                        //.fillMaxSize()
                        //.padding(5.dp,5.dp)
                        .onPreviewKeyEvent { ev -> handlePreviewKeyEvent(ev) },
                    textScrollerPosition = state.inputScrollerPosition,
                    onScroll = { textLayoutResult ->
                        if (textLayoutResult != null) {
                            editorState.inputTextLayoutResult = textLayoutResult
//                            scope.launch {
                            //state.updateView(textLayoutResult)
//                            }
                        }
                    }
                )
            }

            // for autocomplete popup
            AutocompletePopup(
                state = state.autocompleteState
            )
        }
    }


}

@Stable
class EditorState(
    initialText: String = "",
    val defaultTextStyle: SpanStyle = SpanStyle(color = Color.Black, background = Color.White),
    var getLineTokens: LineTokensFunction = { _, _, _ -> emptyList() },
    requestAutocompleteSuggestions: AutocompleteFunction = { _, _, _ -> }
) {

    var scope: CoroutineScope? = null
    var currentRecomposeScope: RecomposeScope? = null

    internal val inputScrollerPosition by mutableStateOf(TextFieldScrollerPosition(Orientation.Vertical))

    var cachedLineHeight = 30f

    var inputTextValue by mutableStateOf(TextFieldValue(initialText))

    var inputTextLayoutResult by mutableStateOf(null as TextLayoutResult?)
    val inputRawText by derivedStateOf {
  inputTextValue.text
//        inputTextLayoutResult?.layoutInput?.text?.text ?: ""
    }

    //FIXME: bug on JS getLineEnd does not work - workaround
    val inputLineMetrics by derivedStateOf {
        LineMetrics(inputRawText)
    }

    val viewFirstLine by derivedStateOf {
        val st = inputScrollerPosition.offset
        inputTextLayoutResult?.MygetLineForVerticalPosition(st) ?: 0
    }
    val viewLastLine by derivedStateOf {
        val st = inputScrollerPosition.offset
        val len = inputScrollerPosition.viewportSize
        inputTextLayoutResult?.MygetLineForVerticalPosition(st + len) ?: 0
    }

    val viewFirstTextIndex by derivedStateOf {
        inputLineMetrics.lineStart(viewFirstLine)
    }
    val viewLastTextIndex by derivedStateOf {
        inputLineMetrics.lineFinish(viewLastLine)
    }
    val viewTextLength by derivedStateOf {
        viewLastTextIndex - viewFirstTextIndex
    }
    val viewRawText by derivedStateOf {
        inputRawText.substring(viewFirstTextIndex, viewLastTextIndex) ?: ""
    }

    val viewLineMetrics by derivedStateOf {
        LineMetrics(viewRawText)
    }
    val viewSelection by derivedStateOf {
        val start = (inputTextValue.selection.start - viewFirstTextIndex).coerceIn(0, viewLastTextIndex)
        val end = (inputTextValue.selection.end - viewFirstTextIndex).coerceIn(0, viewLastTextIndex)
        TextRange(start, end)
    }

    val viewTextValue by derivedStateOf {
        val annotated = buildAnnotatedString {
            addStyle(defaultTextStyle, 0, viewTextLength)  // mark whole text with default style
//                println("Default-style: ${0}-${lp - fp}")
            for (lineNum in viewFirstLine..viewLastLine) {
                //val lineStartPos = textLayoutResult.getLineStart(lineNum)
                //val lineFinishPos = textLayoutResult.getLineEnd(lineNum)
                //FIXME: bug on JS getLineEnd does not work - workaround
                val (lineStartPos, lineFinishPos) = inputLineMetrics.lineEnds(lineNum)
                val lineText = inputRawText.substring(lineStartPos, lineFinishPos)
                val viewLineStart = lineStartPos - viewFirstTextIndex
                val viewLineFinish = lineFinishPos - viewFirstTextIndex
//                    println("Line $lineNum: [$lineStartPos-$lineFinishPos] [$viewLineStart-$viewLineFinish]")
                if (lineNum != viewFirstLine) {
                    append("\n")
                }
                append(lineText)
                val toks = try {
                    getLineTokens(lineNum, lineStartPos, lineText)
                } catch (t: Throwable) {
                    //TODO: log error!
                    println("Error: in getLineTokens ${t.message} ${t.stackTraceToString()}")
                    emptyList()
                }
                for (tk in toks) {
                    val offsetStart = (viewLineStart + tk.start).coerceIn(viewLineStart, viewLineFinish)
                    val offsetFinish = (viewLineStart + tk.finish).coerceIn(viewLineStart, viewLineFinish)
//                        println("tok: [${tk.start}-${tk.finish}] => [$offsetStart-$offsetFinish]")
                    addStyle(tk.style, offsetStart, offsetFinish)
                }
            }
        }
        TextFieldValue(annotatedString = annotated, selection = viewSelection)
    }

    var viewTextLayoutResult by mutableStateOf(null as TextLayoutResult?)
    val viewCursorRect by derivedStateOf {
        //val pos = viewSelection.start //viewCursor.position
        val selStart = inputTextValue.selection.start
        val pos = when {
            selStart < viewFirstTextIndex -> 0
            selStart > viewLastTextIndex -> viewLastTextIndex - viewFirstTextIndex
            else -> selStart - viewFirstTextIndex
        }
        val p = min(pos, viewTextLayoutResult?.layoutInput?.text?.length ?: 0)

        val cr = viewTextLayoutResult?.getCursorRect(p) ?: Rect.Zero

        // Issue [https://github.com/JetBrains/compose-multiplatform/issues/3120]
        // cursor is invisible on JS because cursor rect has 0 height, due to lineMetric issues
        // workaround, figure out position myself
        // left and right of original seem to be corect
        val ln = viewLineMetrics.lineForPosition(pos)
        cachedLineHeight = when {
            0f != cr.height -> cr.height //assume height is correct when text is empty, it seems so
            else -> cachedLineHeight // use last height
        }
        val top = ln * cachedLineHeight
        val bottom = top + cachedLineHeight
        Rect(cr.left, top, cr.right, bottom)
    }

    val viewCursor: CursorDetails by derivedStateOf {
        val selStart = inputTextValue.selection.start
        val inView = when {
            selStart < viewFirstTextIndex -> false
            selStart > viewLastTextIndex -> false
            else -> true
        }
        CursorDetails(SolidColor(Color.Red), viewCursorRect, inView)
    }
    internal val autocompleteState by mutableStateOf(AutocompleteState(this, requestAutocompleteSuggestions))
    val autocompleteOffset by mutableStateOf(IntOffset(-1, -1))

    val inputText get() = inputTextValue.text
    val inputSelection get() = inputTextValue.selection

    val annotationsState by mutableStateOf(AnnotationState())

    fun refresh() = currentRecomposeScope?.invalidate()

    fun updateViewCursorRect(viewTextLayoutResult: TextLayoutResult) {
        // update the drawn cursor position
        val pos = viewSelection.start //viewCursor.position
        val p = min(pos, viewTextLayoutResult.layoutInput.text.length)

        val cr = viewTextLayoutResult.getCursorRect(p)

        // Issue [https://github.com/JetBrains/compose-multiplatform/issues/3120]
        // cursor is invisible on JS because cursor rect has 0 height, due to lineMetric issues
        // workaround, figure out position myself
        // left and right of original seem to be corect
        val ln = viewLineMetrics.lineForPosition(p)
        cachedLineHeight = when {
            0f != cr.height -> cr.height //assume height is correct when text is empty, it seems so
            else -> cachedLineHeight // use last height
        }
        val top = ln * cachedLineHeight
        val bottom = top + cachedLineHeight

        val cr2 = Rect(cr.left, top, cr.right, bottom)
        viewCursor.updateRect(cr2)
    }

    fun insertText(text: String) {
        val pos = viewSelection.start
        val before = this.inputText.substring(0, pos)
        val after = this.inputText.substring(pos)
        val newText = before + text + after
        val sel = inputTextValue.selection
        val newSel = TextRange(sel.start + text.length)
        this.inputTextValue = this.inputTextValue.copy(text = newText, selection = newSel)
        refresh()
    }
}