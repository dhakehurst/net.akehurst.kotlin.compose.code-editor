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
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.insert
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import androidx.compose.ui.zIndex
import kotlinx.coroutines.CoroutineScope
import net.akehurst.kotlin.compose.editor.api.AutocompleteFunction
import net.akehurst.kotlin.compose.editor.api.LineTokensFunction

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

val KeyEvent.isCtrlEnter get() = (key == Key.Enter || utf16CodePoint == '\n'.code) && isCtrlPressed
val KeyEvent.isCtrlSpace get() = (key == Key.Spacebar || utf16CodePoint == ' '.code) && isCtrlPressed


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
    val MARGIN_WIDTH = 20.dp
    val state by remember { mutableStateOf(editorState) }

    rememberCoroutineScope().also {
        editorState.scope = it
        editorState.autocompleteState.scope = it
    }

    Row(
        modifier = modifier//.weight(1f)
    ) {
        // Margin Annotations
        Box(
            modifier = Modifier
                .width(MARGIN_WIDTH)
                .fillMaxHeight()
                .background(color = MaterialTheme.colorScheme.surfaceVariant)
                .wrapContentWidth(unbounded = true, align = Alignment.Start)
                //.graphicsLayer { clip=false }
                .zIndex(1f)
            ,
        ) {
            state.lastTextLayoutResult?.let { textLayoutResult ->
                for (ann in state.marginItemsVisible) {
                    Icon(
                        ann.icon, contentDescription = ann.text, tint = ann.color,
                        modifier = Modifier
                            .width(MARGIN_WIDTH)
                            .offset(y = ann.offsetFromTopOfViewport(state.viewFirstLine, textLayoutResult))
                            .hoverable(ann.interactionSource)
                    )
                    ann.isHovered = ann.interactionSource.collectIsHoveredAsState().value
                }
                state.marginItemHovered.forEach { ann ->
                    Text(
                        ann.text, modifier = Modifier
                            .offset(y = ann.offsetFromTopOfViewport(state.viewFirstLine, textLayoutResult)+(MARGIN_WIDTH/2), x = MARGIN_WIDTH/2)
                            .background(color = MaterialTheme.colorScheme.surfaceVariant)
                            .border(1.dp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            .hoverable(ann.interactionSource)
                    )
                }
            }
        }
        //Text editing
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(color = MaterialTheme.colorScheme.surface),
        ) {

            BasicTextField(
//            TextField(
                cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
                textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface),
                state = state.inputTextFieldState,
                modifier = Modifier
                    .fillMaxSize()
                    .onPreviewKeyEvent { ev -> state.handlePreviewKeyEvent(ev) }
                    .onKeyEvent { ev -> state.handleKeyEvent(ev) }
                    .focusRequester(state.focusRequester),
                onTextLayout = { r ->
                    r.invoke()?.let { state.onInputTextLayout(it) }
                },
                scrollState = state.inputScrollState,
                interactionSource = state.interactionSource,
                inputTransformation = state.inputTransformation,
            )

            // to invoke the 'onTextChange' callback when text changes
            LaunchedEffect(state.inputRawText) {
                snapshotFlow { state.inputRawText }.collect { state.onInputChange(it) }
            }

            LaunchedEffect(state.inputScrollState.value) {
                snapshotFlow { state.inputScrollState }.collect { state.onInputScroll(it) }
            }

            SideEffect {
                if (state.giveFocus) {
                    state.focusRequester.requestFocus()
                    state.giveFocus = false
                }
            }

            AutocompletePopup(
                state = state.autocompleteState
            )
        }
    }

    state.isFocused = state.interactionSource.collectIsFocusedAsState().value
}


class EditorState2(
    initialText: String = "",
    val defaultTextStyle: SpanStyle = SpanStyle(color = Color.Black, background = Color.White),
    val onTextChange: (CharSequence) -> Unit = {},
    var getLineTokens: LineTokensFunction = { _, _, _ -> emptyList() },
    requestAutocompleteSuggestions: AutocompleteFunction = { _, _, _ -> }
) {
    var scope: CoroutineScope? = null

    val interactionSource = MutableInteractionSource()
    var giveFocus = false
    var isFocused = false
    val focusRequester by mutableStateOf(FocusRequester())

    val inputTextFieldState by mutableStateOf(TextFieldState(initialText))
    val inputRawText by derivedStateOf { inputTextFieldState.text }
    var lastAnnotatedText: AnnotatedString? = null
    val inputScrollState by mutableStateOf(ScrollState(0))
    // val inputScrollerViewportSize get() = inputScrollState.maxValue// viewportSize
    // val inputScrollerOffset get() = inputScrollState.value

    // so we can get the annotatedstring
    var lastTextLayoutResult: TextLayoutResult? = null

    val viewCursor by mutableStateOf(CursorDetails(SolidColor(Color.Red)))
    var viewFirstLine by mutableStateOf(0)
    var viewLastLine by mutableStateOf(0)
    var viewFirstLineStartTextPosition by mutableStateOf(0)
    var viewLastLineFinishTextPosition by mutableStateOf(0)

    val inputTransformation = InputTransformation({
        annotateTextFieldBuffer(this)
    })

    internal val autocompleteState by mutableStateOf(
        AutocompleteStateCompose(
            { this.inputTextFieldState.text },
            { this.inputTextFieldState.selection.start },
            { this.viewCursor.rect.bottomRight.round() },
            { txt -> this.inputTextFieldState.edit { this.insert(inputTextFieldState.selection.start, txt) } },
            requestAutocompleteSuggestions
        )
    )

    val marginItemsState by mutableStateOf(MarginItemsState())
    val marginItemsVisible by derivedStateOf {
        marginItemsState.items.filter { viewFirstLine <= it.lineNumber && it.lineNumber <= viewLastLine }
    }
    val marginItemHovered by derivedStateOf {
        marginItemsState.items.filter { it.isHovered }
    }

    val textMarkersState by mutableStateOf(TextMarkerState())
    val textMarkersVisible by derivedStateOf {
        textMarkersState.markers.filter { viewFirstLineStartTextPosition <= it.position && it.position <= viewLastLineFinishTextPosition }
    }

    fun handlePreviewKeyEvent(ev: KeyEvent): Boolean {
        //println("$ev ${ev.key} ${ev.key.keyCode}")
        var handled = true
        when (ev.type) {
            KeyEventType.KeyDown -> when {
                this.autocompleteState.isVisible -> this.autocompleteState.handleKey(ev)
                else -> when {
                    ev.isCtrlPressed || ev.isMetaPressed -> when {
                        ev.isCtrlSpace -> autocompleteState.open()
                        else -> handled = false
                    }

                    else -> handled = false
                }
            }

            // KeyUp | KeyPressed
            else -> when {
                ev.isCtrlSpace -> handled = true
                else -> handled = false
            }
        }
        return handled
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

    fun onInputChange(newText: CharSequence) {
        onTextChange(newText)
    }

    fun onInputScroll(i: ScrollState) {
        if (null != lastTextLayoutResult) {
            updateViewDetails(lastTextLayoutResult!!)
        }
    }

    fun onInputTextLayout(textLayoutResult: TextLayoutResult) {
        updateViewDetails(textLayoutResult)
        lastTextLayoutResult = textLayoutResult
        /*
                val rawText = textLayoutResult.layoutInput.text.text
                if (rawText.length > 0) {
                    val annText = annotateText(textLayoutResult)
                    val sel = inputTextFieldState.selection
                    val tr = InputTransformation({
                        this.setComposition(0, rawText.length, annText.annotations)
                        this.selection = sel
                    })
                    inputTextFieldState.editAsUser(tr) {
                        this.setComposition(0, rawText.length, annText.annotations)
                    }
                }
        */
    }

    // The line metrics from TextLayoutResult are related to the 'layout' of the text
    // i.e. if the line wraps, the metrics indicate an extra line.
    // We want line metrics where the lines are indicated by user input of EOL characters!
    fun annotateText(textLayoutResult: TextLayoutResult): AnnotatedString {
        val rawText = textLayoutResult.layoutInput.text.text
        return when {
            // (Int.MAX_VALUE == inputScrollerViewportSize || 0 == inputScrollerViewportSize) -> {
            //     AnnotatedString(rawText)
            // }

            else -> {
                var viewFirstLine = -1
                var viewLastLine = -1
                if (0 == this.inputScrollState.viewportSize) {
                    viewFirstLine = 0
                    viewLastLine = textLayoutResult.lineCount - 1
                } else {
                    //val st = inputScrollerOffset.toFloat()
                    //val len = inputScrollerViewportSize
                    val firstLine = viewFirstLine//textLayoutResult.getLineForVerticalPosition(st)
                    val lastLine = viewLastLine//textLayoutResult.getLineForVerticalPosition(st + len)
                    viewFirstLine = firstLine
                    viewLastLine = lastLine
                }

                buildAnnotatedString {
//                addStyle(defaultTextStyle, 0, lp - fp)  // mark whole text with default style
                    append(rawText)
                    for (lineNum in viewFirstLine..viewLastLine) {
                        val lineStartPos = textLayoutResult.getLineStart(lineNum)
                        val lineFinishPos = textLayoutResult.getLineEnd(lineNum)
                        //FIXME: bug on JS getLineEnd does not work - workaround
                        //val (lineStartPos, lineFinishPos) = inputLineMetrics.lineEnds(lineNum)
//                        val lineText = rawText.substring(lineStartPos, lineFinishPos)
                        val lineText = rawText.substring(lineStartPos, minOf(lineFinishPos + 1, rawText.length)) //+1 to get the eol
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

    fun annotateText2(rawText: CharSequence): AnnotatedString {
        //val textLayoutResult = lastTextLayoutResult
        // val rawText = inputRawText
        return if (rawText.isEmpty()) {
            AnnotatedString(rawText.toString())
        } else {
            val lineMetrics = LineMetrics(rawText)
            var viewFirstLine = -1
            var viewLastLine = -1
            if (0 == this.inputScrollState.viewportSize) {
                viewFirstLine = 0
                viewLastLine = lineMetrics.lineCount - 1
            } else {
                //val st = inputScrollerOffset.toFloat()
                //val len = inputScrollerViewportSize
                val firstLine = viewFirstLine//textLayoutResult.getLineForVerticalPosition(st)
                val lastLine = viewLastLine//textLayoutResult.getLineForVerticalPosition(st + len)
                viewFirstLine = firstLine
                viewLastLine = lastLine
            }

            buildAnnotatedString {
//                addStyle(defaultTextStyle, 0, lp - fp)  // mark whole text with default style
                append(rawText)

                // annotate from tokens
                for (lineNum in viewFirstLine..viewLastLine) {
                    //val lineStartPos = textLayoutResult.getLineStart(lineNum)
                    // val lineFinishPos = textLayoutResult.getLineEnd(lineNum)
                    //FIXME: bug on JS getLineEnd does not work - workaround
                    val (lineStartPos, lineFinishPos) = lineMetrics.lineEnds(lineNum)
//                        val lineText = rawText.substring(lineStartPos, lineFinishPos)
                    val lineText = rawText.substring(lineStartPos, minOf(lineFinishPos + 1, rawText.length)) //+1 to get the eol
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

                // annotate from markers
                for (marker in textMarkersVisible) {
                    val lineNum = lineMetrics.lineForPosition(marker.position)
                   // println("Marker at: ${marker.position} length ${marker.length} line $lineNum")
                   // val (lineStartPos, lineFinishPos) = lineMetrics.lineEnds(lineNum)
                    val offsetStart = (marker.position).coerceIn(0, rawText.length)
                    val offsetFinish = (marker.position + marker.length).coerceIn(0, rawText.length)
                   // println("Style at: ${offsetStart} .. ${offsetFinish}")
                    addStyle(marker.style, offsetStart, offsetFinish)
                }
            }
        }
    }

    fun refresh() {
        // lastTextLayoutResult?.let { onInputTextLayout(it) }
        inputTextFieldState.editAsUser(InputTransformation({})) {
            annotateTextFieldBuffer(this)
        }
    }

    fun setNewText(text: String) {
        this.inputTextFieldState.setTextAndPlaceCursorAtEnd(text)
    }

    private fun annotateTextFieldBuffer(buffer: TextFieldBuffer) {
        val rawText = buffer.asCharSequence()
        if (rawText.isNotEmpty()) {
            //val sel = buffer.selection
            lastAnnotatedText = annotateText2(rawText)
            buffer.setComposition(0, rawText.length, lastAnnotatedText.annotations)
            // buffer.selection = sel
        }
    }

    private fun updateViewDetails(textLayoutResult: TextLayoutResult) {
        val st = inputScrollState.value.toFloat()
        val len = 500 //inputScrollState.viewportSize
        viewFirstLine = textLayoutResult.getLineForVerticalPosition(st)
        viewLastLine = textLayoutResult.getLineForVerticalPosition(st + len)
        viewFirstLineStartTextPosition = textLayoutResult.getLineStart(viewFirstLine)
        viewLastLineFinishTextPosition = textLayoutResult.getLineEnd(viewLastLine)
        //println("View: $viewFirstLine $viewLastLine")
    }
}