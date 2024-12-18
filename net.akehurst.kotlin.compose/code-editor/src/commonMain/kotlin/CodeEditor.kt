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
import androidx.compose.foundation.text.BasicTextField
//import androidx.compose.foundation.text.MyCoreTextField2
import androidx.compose.foundation.text.CoreTextField
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
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.akehurst.kotlin.compose.editor.api.AutocompleteFunction
import net.akehurst.kotlin.compose.editor.api.LineTokensFunction

/*
 * KEY_PRESSED
 * KEY_TYPED
 * onValueChange
 * visualTransformation
 * visualTransformation
 * onTextLayout
 * onScroll
 * onScroll
 * KEY_RELEASED
 */

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CodeEditor(
    modifier: Modifier = Modifier,
    editorState: EditorState = EditorState(
        initialText = "",
        defaultTextStyle = SpanStyle(color = MaterialTheme.colorScheme.onBackground),
        onTextChange = {},
        getLineTokens = { _, _, _ -> emptyList() },
        requestAutocompleteSuggestions = { _, _, _ -> },
    )
) {

    rememberCoroutineScope().also {
        editorState.scope = it
        editorState.autocompleteState.scope = it
        editorState.currentRecomposeScope = currentRecomposeScope
        //editorState.clipboardManager = LocalClipboardManager.current
    }
    val state by remember { mutableStateOf(editorState) }

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
        Box( //if column autocomplete menu does not display!
            modifier = Modifier.fillMaxSize()
        ) {

            CoreTextField(
                value = state.inputTextValue,
                onValueChange = state::onValueChange,
                modifier = Modifier
                    .fillMaxSize()
                    //.padding(5.dp,5.dp)
                    .onPreviewKeyEvent { ev -> editorState.handlePreviewKeyEvent(ev) }
                    .onKeyEvent { ev -> editorState.handleKeyEvent(ev) },
                textScrollerPosition = state.inputScrollerPosition,
                onTextLayout = state::onTextLayout,
                cursorBrush = SolidColor(Color.Red),
            )


            LaunchedEffect(state.inputScrollerPosition) {
                snapshotFlow { state.inputScrollerPosition.offset }.collect { state.onScroll() }
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
    val onTextChange: (String) -> Unit = {},
    var getLineTokens: LineTokensFunction = { _, _, _ -> emptyList() },
    requestAutocompleteSuggestions: AutocompleteFunction = { _, _, _ -> }
) {

    var scope: CoroutineScope? = null
    var currentRecomposeScope: RecomposeScope? = null

    internal val inputScrollerPosition by mutableStateOf(TextFieldScrollerPosition(Orientation.Vertical))
    var inputRawText by mutableStateOf(initialText)
    var inputSelection by mutableStateOf(TextRange.Zero)
    val inputAnnotatedText by derivedStateOf { annotateText(inputRawText) }
    val inputTextValue by derivedStateOf { TextFieldValue(annotatedString = inputAnnotatedText, selection = inputSelection) }
    var lastTextLayoutResult: TextLayoutResult? = null

    var viewFirstLine by mutableStateOf(0)
    var viewLastLine by mutableStateOf(0)
    var viewFirstLineStartIndex by mutableStateOf(0)
    var viewLastLineFinishIndex by mutableStateOf(0)

    internal val autocompleteState by mutableStateOf(
        AutocompleteState(
            { this.inputRawText },
            { this.inputSelection.start },
//            { this.viewCursor.rect.bottomRight.round() },
            { this.lastTextLayoutResult!!.getCursorRect(this.inputSelection.start).bottomRight.round() },
            { this.insertText(it) },
            requestAutocompleteSuggestions
        )
    )
    val autocompleteOffset by mutableStateOf(IntOffset(-1, -1))

    val annotationsState by mutableStateOf(AnnotationState())

    fun updateViewDetails() {
        val textLayoutResult = lastTextLayoutResult
        if (null!=textLayoutResult) {
            val st = inputScrollerPosition.offset
            val len = inputScrollerPosition.viewportSize
            val firstLine = textLayoutResult.getLineForVerticalPosition(st)
            val lastLine = textLayoutResult.getLineForVerticalPosition(st + len)
            val fp = textLayoutResult.getLineStart(firstLine)
            val lp = textLayoutResult.getLineEnd(lastLine)
            viewFirstLine = firstLine
            viewLastLine = lastLine
            viewFirstLineStartIndex = fp
            viewLastLineFinishIndex = lp
        } else {
            // cannot update
        }
    }

    private fun annotateText(rawText: String): AnnotatedString {
        return if (0 == inputScrollerPosition.viewportSize) {
            AnnotatedString(rawText)
        } else {
            buildAnnotatedString {
                append(rawText)
                for (lineNum in viewFirstLine..viewLastLine) {
                    val lineStartPos = lastTextLayoutResult!!.getLineStart(lineNum)
                    val lineFinishPos = lastTextLayoutResult!!.getLineEnd(lineNum)
                    val lineText = rawText.substring(lineStartPos, lineFinishPos)
                    val toks = try {
                        getLineTokens(lineNum, lineStartPos, lineText)
                    } catch (t: Throwable) {
                        //TODO: log error!
                        println("Error: in getLineTokens ${t.message} ${t.stackTraceToString()}")
                        emptyList()
                    }
                    for (tk in toks) {
                        val offsetStart = (lineStartPos + tk.start).coerceIn(lineStartPos, lineFinishPos)
                        val offsetFinish = (lineStartPos + tk.finish).coerceIn(lineStartPos, lineFinishPos)
                        addStyle(tk.style, offsetStart, offsetFinish)
                    }
                }
            }
        }
    }

    fun refresh() = currentRecomposeScope?.invalidate()

    fun setNewText(text: String) {
        this.inputRawText = text
    }

    fun insertText(text: String) {
        val pos = inputSelection.start
        val before = this.inputRawText.substring(0, pos)
        val after = this.inputRawText.substring(pos)
        val newText = before + text + after
        val sel = inputTextValue.selection
        this.inputSelection = TextRange(sel.start + text.length)
        this.inputRawText = newText
        refresh()
    }

    val KeyEvent.isCtrlSpace get() = (key == Key.Spacebar || utf16CodePoint == ' '.code) && isCtrlPressed

    fun handlePreviewKeyEvent(ev: KeyEvent): Boolean {
        //println("$ev ${ev.key} ${ev.key.keyCode}")
        return when (ev.type) {
            KeyEventType.KeyDown -> when {
                this.autocompleteState.isVisible -> when (ev.key) {
                    Key.Escape -> {
                        this.autocompleteState.close()
                        true
                    }

                    Key.Enter -> {
                        this.autocompleteState.chooseSelected()
                        true
                    }

                    Key.DirectionDown -> {
                        this.autocompleteState.selectNext()
                        true
                    }

                    Key.DirectionUp -> {
                        this.autocompleteState.selectPrevious()
                        true
                    }

                    else -> false
                }

                else -> when {
                    ev.isCtrlPressed || ev.isMetaPressed -> when {
                        ev.isCtrlSpace -> {
                            // autocomplete
                            scope?.launch { autocompleteState.open() }
                            true
                        }

                        else -> false
                    }

                    else -> false
                }
            }

            // KeyUp | KeyPressed
            else -> when {
                ev.isCtrlSpace -> true
                else -> false
            }
        }
    }

    fun handleKeyEvent(ev: KeyEvent): Boolean {
        //println("$ev ${ev.key} ${ev.key.keyCode}")
        return when (ev.type) {
            // KeyDown | KeyUp | KeyPressed
            else -> when {
                ev.isCtrlSpace -> true
                else -> false
            }
        }
    }

    fun onValueChange(textFieldValue: TextFieldValue) {
        //println("onValueChange")
        if (textFieldValue.text != inputRawText) { // has text really changed !
            inputRawText = textFieldValue.text
            onTextChange(textFieldValue.text)
        }
        if (inputSelection != textFieldValue.selection) {
            inputSelection = textFieldValue.selection
        }
        // inputTextValue = textFieldValue
    }

    fun onScroll() {
        // println("onScroll")
        updateViewDetails()
    }

    fun onTextLayout(textLayoutResult: TextLayoutResult) {
        // println("onTextLayout")
        lastTextLayoutResult = textLayoutResult
        updateViewDetails()
    }

}