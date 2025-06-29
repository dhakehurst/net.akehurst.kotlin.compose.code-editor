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
/*
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
import androidx.compose.foundation.text.input.OutputTransformation
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import androidx.compose.ui.zIndex
import kotlinx.coroutines.CoroutineScope
import net.akehurst.kotlin.compose.editor.api.AutocompleteFunction
import net.akehurst.kotlin.compose.editor.api.LineTokensFunction
import kotlin.math.roundToInt





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
    autocompleteModifier: Modifier = Modifier,
    editorState: EditorState2 = EditorState2(
        initialText = "",
        defaultTextStyle = SpanStyle(color = MaterialTheme.colorScheme.onBackground),
        onTextChange = {},
        getLineTokens = { _, _, _ -> emptyList() },
        requestAutocompleteSuggestions = { _, _, _, _ -> },
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
                outputTransformation = state.outputTransformation
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
            AutocompletePopup2(
                state = state.autocompleteState,
                modifier = autocompleteModifier,
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
    requestAutocompleteSuggestions: AutocompleteFunction = { _, _, _, _ -> }
) {
    companion object {
        fun annotateText(rawText: CharSequence, viewFirstLine: Int, viewLastLine: Int, getLineTokens: LineTokensFunction, markers: List<TextMarkerDefault>): AnnotatedString {
            return if (rawText.isEmpty()) {
                AnnotatedString(rawText.toString())
            } else {
                // lines from textLayoutResult are possible different to actual ines in text defined by EOL.
                //  eg if lines are wrapped by the layout, thus have to compute own lineMetrics
                val lineMetrics = LineMetrics(rawText)
                buildAnnotatedString {
                    append(rawText)
                    // annotate from tokens
                    for (lineNum in viewFirstLine..viewLastLine) {
                        val (lineStartPos, lineFinishPos) = lineMetrics.lineEnds(lineNum)
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
                    for (marker in markers) {
                         // println("Marker at: ${marker.position} length ${marker.length} line $lineNum")
                        val offsetStart = (marker.position).coerceIn(0, rawText.length)
                        val offsetFinish = (marker.position + marker.length).coerceIn(0, rawText.length)
                        // println("Style at: ${offsetStart} .. ${offsetFinish}")

                        addStyle(marker.style, offsetStart, offsetFinish)
                    }
                }
            }
        }
    }

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

    //val inputTransformation = InputTransformation({
   //     annotateTextFieldBuffer(this)
   // })
    val outputTransformation = OutputTransformation({
        annotateTextFieldBuffer(this)
    })

    val autocompleteState by mutableStateOf(
        AutocompleteStateCompose(
            getText = { this.inputTextFieldState.text },
            getCursorPosition = { this.inputTextFieldState.selection.start },
            getMenuOffset = { cursorPos() },
            insertText = { txt -> this.inputTextFieldState.edit { this.insert(inputTextFieldState.selection.start, txt) } },
            requestAutocompleteSuggestions = requestAutocompleteSuggestions
        )
    )

    val marginItemsState by mutableStateOf(MarginItemsState())
    val marginItemsVisible by derivedStateOf {
        marginItemsState.items.filter { viewFirstLine <= it.lineNumber && it.lineNumber <= viewLastLine }
    }
    val marginItemHovered by derivedStateOf {
        marginItemsState.items.filter { it.isHovered }
    }

    val textMarkersState by mutableStateOf(TextMarkerState()) //TODO: try using TextFieldDecorator for these
    val textMarkersVisible by derivedStateOf {
        textMarkersState.markers.filter { viewFirstLineStartTextPosition <= it.position && it.position <= viewLastLineFinishTextPosition }
    }

    fun handlePreviewKeyEvent(ev: KeyEvent): Boolean {
        //println("$ev ${ev.key} ${ev.key.keyCode}")
        var handled = true
        when (ev.type) {
            KeyEventType.KeyDown -> when {
                this.autocompleteState.isVisible -> this.autocompleteState.handlePreviewKeyEvent(ev)
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
        autocompleteState.close()
        updateViewDetails(textLayoutResult)
        lastTextLayoutResult = textLayoutResult
    }

    fun refresh() {
        // create an edit causing a recomposition
        // the refresh requires triggering of a text layout
        inputTextFieldState.edit {
            annotateTextFieldBuffer(this)
        }
    }

    fun setNewText(text: String) {
        this.inputTextFieldState.setTextAndPlaceCursorAtEnd(text)
    }

    private fun updateViewDetails(textLayoutResult: TextLayoutResult) {
        val st = inputScrollState.value.toFloat()
        val len = inputScrollState.viewportSize
        viewFirstLine = textLayoutResult.getLineForVerticalPosition(st)
        viewLastLine = textLayoutResult.getLineForVerticalPosition(st + len)
        viewFirstLineStartTextPosition = textLayoutResult.getLineStart(viewFirstLine)
        viewLastLineFinishTextPosition = textLayoutResult.getLineEnd(viewLastLine)
    }

    fun cursorPos(): IntOffset {
        // update the drawn cursor position
        val selStart = inputTextFieldState.selection.start// - viewFirstLinePos
        val tlr = lastTextLayoutResult
        return if (null!=tlr) {
            val currentLine = tlr.getLineForOffset(selStart)
            val lineBot = tlr.getLineBottom(currentLine).roundToInt()
            val cursOffset = tlr.getHorizontalPosition(selStart, true).roundToInt()
            val scrollOffset = inputScrollState.value
            //println("currentLine=${currentLine}, lineBot=${lineBot}, cursOffset=$cursOffset,  scrollOffset=$scrollOffset")
            return IntOffset(cursOffset, lineBot-scrollOffset)
        } else {
            IntOffset(0,0)
        }
    }

    private fun annotateTextFieldBuffer(buffer: TextFieldBuffer) {
        val rawText = buffer.asCharSequence()
        if (rawText.isNotEmpty()) {
            lastAnnotatedText = annotateText(rawText, this.viewFirstLine, this.viewLastLine, this.getLineTokens, this.textMarkersVisible)
            buffer.setComposition(0, rawText.length, lastAnnotatedText.annotations)
            buffer.changeTracker.trackChange(0, rawText.length,rawText.length)
        }
    }

}

 */