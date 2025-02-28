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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.input.insert
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.akehurst.kotlin.compose.editor.api.AutocompleteFunction
import net.akehurst.kotlin.compose.editor.api.LineTokensFunction
import net.akehurst.kotlin.compose.editor.text.BasicTextField3
import net.akehurst.kotlin.compose.editor.text.TextFieldState3
import net.akehurst.kotlin.compose.editor.text.setTextAndPlaceCursorAtEnd

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
fun CodeEditor3(
    modifier: Modifier = Modifier,
    editorState: EditorState3 = EditorState3(
        initialText = "",
        defaultTextStyle = SpanStyle(color = MaterialTheme.colorScheme.onBackground),
        onTextChange = {},
        getLineTokens = { _, _, _ -> emptyList() },
        requestAutocompleteSuggestions = { _, _, _ -> },
    )
) {
    val state by remember { mutableStateOf(editorState) }

    val scope = rememberCoroutineScope().also {
        editorState.scope = it
        // editorState.currentRecomposeScope = currentRecomposeScope
        //editorState.clipboardManager = LocalClipboardManager.current
    }

    Row(
        modifier = modifier//.weight(1f)
    ) {
        // Margin Annotations
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

            BasicTextField3(
                cursorBrush = SolidColor(Color.Red),
                //                    textStyle = TextStyle(color = Color.Transparent),
                state = editorState.inputTextFieldState,
                modifier = Modifier
                   // .matchParentSize()
                    .fillMaxSize()
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
                    .onKeyEvent { ev -> editorState.handleKeyEvent(ev) }
                    .focusRequester(state.focusRequester)
                ,
                onTextLayout = { r ->
                    r.invoke()?.let { editorState.onInputTextLayout(it) }
                },
                scrollState = editorState.inputScrollState,
            )

            LaunchedEffect(editorState.inputTextFieldState) {
                snapshotFlow { editorState.inputTextFieldState.value }.collect { editorState.onInputChange() }
            }

            LaunchedEffect(editorState.inputScrollState) {
                snapshotFlow { editorState.inputScrollState.value }.collect { editorState.onInputScroll() }
            }

            // for autocomplete popup
            AutocompletePopup(
                state = state.autocompleteState
            )
        }
    }
}


class EditorState3(
    initialText: String = "",
    val defaultTextStyle: SpanStyle = SpanStyle(color = Color.Black, background = Color.White),
    val onTextChange: (CharSequence) -> Unit = {},
    var getLineTokens: LineTokensFunction = { _, _, _ -> emptyList() },
    requestAutocompleteSuggestions: AutocompleteFunction = { _, _, _ -> }
) {
    var scope: CoroutineScope? = null
    val annotationsState by mutableStateOf(AnnotationState())
    val focusRequester = FocusRequester()

    val inputTextFieldState by mutableStateOf(TextFieldState3(initialText))
    val inputRawText get() = inputTextFieldState.text
    val inputScrollState by mutableStateOf(ScrollState(0))
    val inputScrollerViewportSize get() = inputScrollState.maxValue// viewportSize
    val inputScrollerOffset get() = inputScrollState.value

    var lastTextLayoutResult: TextLayoutResult? = null

    val viewCursor by mutableStateOf(CursorDetails(SolidColor(Color.Red)))
    var viewFirstLine by mutableStateOf(0)
    var viewLastLine by mutableStateOf(0)
    var viewFirstLineStartIndex by mutableStateOf(0)
    var viewLastLineFinishIndex by mutableStateOf(0)

    internal val autocompleteState by mutableStateOf(
        AutocompleteStateCompose(
            { this.inputTextFieldState.text },
            { this.inputTextFieldState.selection.start },
            { this.viewCursor.rect.bottomRight.round() },
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

    fun onInputScroll() {
        if (null!=lastTextLayoutResult) {
            updateViewDetails(lastTextLayoutResult!!)
        }
    }

    fun onInputTextLayout(textLayoutResult: TextLayoutResult) {
        lastTextLayoutResult = textLayoutResult
        val annText = annotateText(textLayoutResult)
        inputTextFieldState.editAsUser(null) {
            this.replace(0, originalText.length, annText)
           //this.setComposition(0,originalText.length, annText.annotations)
        }
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
        val rawText = inputTextFieldState.text.toString()
        return when {
           // (Int.MAX_VALUE == inputScrollerViewportSize || 0 == inputScrollerViewportSize) -> {
           //     AnnotatedString(rawText)
           // }

            else -> {
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

    fun refresh() {
       // updateViewDetails()
    }

    fun setNewText(text: String) {
        this.inputTextFieldState.setTextAndPlaceCursorAtEnd(text)
    }
}