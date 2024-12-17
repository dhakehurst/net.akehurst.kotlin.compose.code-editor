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
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.RowScopeInstance.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.insert
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
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
fun CodeEditor2(
    modifier: Modifier = Modifier,
    editorState: EditorState2 = EditorState2(
        initialText = "",
        defaultTextStyle = SpanStyle(color = MaterialTheme.colorScheme.onBackground),
        onTextChange = {},
        getLineTokens = { _, _, _ -> emptyList() },
        requestAutocompleteSuggestions = { _, _, _ -> },
    )
) {

    val scope = rememberCoroutineScope().also {
        editorState.scope = it
        // editorState.currentRecomposeScope = currentRecomposeScope
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
        //Box(
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
/*
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

                BasicTextField(
                    //state = editorState.viewTextFieldState,
                    value = editorState.viewTextValue,
                    onValueChange = {},
                    readOnly = false,
                    enabled = false,
                    onTextLayout = { },
                    modifier = Modifier
                        .background(color = Color.LightGray)
                        //.fillMaxSize()
//                        .matchParentSize()
                        .height(50.dp)
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
*/
            // The input CoreTextField
            // sits on top receives user interactions
            // and contains the whole text - with no styling
            // Transparent
            CompositionLocalProvider(
                // make selections transparent in the in
                LocalTextSelectionColors provides TextSelectionColors(
                    handleColor = LocalTextSelectionColors.current.handleColor,
//                    backgroundColor = Color.Transparent
                    backgroundColor = Color.Blue
                )
            ) {

                BasicTextField(
                    //cursorBrush = SolidColor(Color.Red),
                    //                    textStyle = TextStyle(color = Color.Transparent),
                    state = editorState.inputTextFieldState,
                    modifier = Modifier
//                        .matchParentSize()
                        //.fillMaxSize()
                        //.padding(5.dp,5.dp)
/*                        .drawWithContent {
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
                        }
*/
                        .onPreviewKeyEvent { ev -> editorState.handlePreviewKeyEvent(ev) }
                        .onKeyEvent { ev -> editorState.handleKeyEvent(ev) },
                    onTextLayout = { r -> r.invoke()?.let { editorState.onInputTextLayout(it) } },
                    scrollState = editorState.inputScrollState,
                )
            }

            LaunchedEffect(editorState.inputTextFieldState) {
                snapshotFlow { editorState.inputTextFieldState.value }.collect { editorState.onInputChange() }
            }

            // for autocomplete popup
            AutocompletePopup(
                state = state.autocompleteState
            )
        }
    }
}


class EditorState2(
    initialText: String = "",
    val defaultTextStyle: SpanStyle = SpanStyle(color = Color.Black, background = Color.White),
    val onTextChange: (CharSequence) -> Unit = {},
    var getLineTokens: LineTokensFunction = { _, _, _ -> emptyList() },
    requestAutocompleteSuggestions: AutocompleteFunction = { _, _, _ -> }
) {
    var scope: CoroutineScope? = null
    val annotationsState by mutableStateOf(AnnotationState())

    val inputTextFieldState by mutableStateOf(TextFieldState(initialText))
    val inputRawText get() = inputTextFieldState.text
    val inputScrollState = ScrollState(0)
    val inputScrollerViewportSize get() = inputScrollState.viewportSize
    val inputScrollerOffset get() = inputScrollState.value

    val viewCursor by mutableStateOf(CursorDetails(SolidColor(Color.Red)))
    var viewFirstLine by mutableStateOf(0)
    var viewLastLine by mutableStateOf(0)
    var viewFirstLineStartIndex by mutableStateOf(0)
    var viewLastLineFinishIndex by mutableStateOf(0)
//    var viewTextValue by mutableStateOf(TextFieldValue(""))
//    val viewCursor by mutableStateOf(CursorDetails(SolidColor(Color.Red)))

    internal val autocompleteState by mutableStateOf(
        AutocompleteState(
            { this.inputTextFieldState.text },
            { this.inputTextFieldState.selection.start },
            { this.viewCursor },
            { txt -> this.inputTextFieldState.edit { this.insert(inputTextFieldState.selection.start, txt) } },
            requestAutocompleteSuggestions
        )
    )

    val KeyEvent.isCtrlSpace
        get() = (key == Key.Spacebar || utf16CodePoint == ' '.code) && isCtrlPressed

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

    fun onInputChange() {
        onTextChange(inputRawText)

//        if (inputSelection != textFieldValue.selection) {
//            inputSelection = textFieldValue.selection
//        }
    }

    fun onInputTextLayout(textLayoutResult: TextLayoutResult) {
        // println("onTextLayout")
        updateViewDetails(textLayoutResult)
    }

    fun updateViewDetails(textLayoutResult: TextLayoutResult) {
        val newText = textLayoutResult.layoutInput.text.text
        val st = inputScrollerOffset.toFloat()
        val len = inputScrollerViewportSize
        val firstLine = textLayoutResult.getLineForVerticalPosition(st)
        val lastLine = textLayoutResult.getLineForVerticalPosition(st + len)
        val fp = textLayoutResult.getLineStart(firstLine)
        val lp = textLayoutResult.getLineEnd(lastLine)
        viewFirstLine = firstLine
        viewLastLine = lastLine
        viewFirstLineStartIndex = fp
        viewLastLineFinishIndex = lp
 //       updateViewCursorPos()
 //       updateViewCursorRect(textLayoutResult)
    }

    fun annotateText(textLayoutResult: TextLayoutResult): AnnotatedString {
        val rawText = ""
        return if (0 == inputScrollerViewportSize) {
            AnnotatedString("")
        } else {
            buildAnnotatedString {
//                addStyle(defaultTextStyle, 0, lp - fp)  // mark whole text with default style
                append(rawText)
                for (lineNum in viewFirstLine..viewLastLine) {
                    val lineStartPos = textLayoutResult.getLineStart(lineNum)
                    val lineFinishPos = textLayoutResult.getLineEnd(lineNum)
                    //FIXME: bug on JS getLineEnd does not work - workaround
                    //val (lineStartPos, lineFinishPos) = inputLineMetrics.lineEnds(lineNum)
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
}