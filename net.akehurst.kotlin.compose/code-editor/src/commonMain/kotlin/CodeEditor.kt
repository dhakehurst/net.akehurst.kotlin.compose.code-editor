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

import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.TextFieldState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.*
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import me.saket.extendedspans.ExtendedSpans
import me.saket.extendedspans.SquigglyUnderlineSpanPainter
import me.saket.extendedspans.drawBehind
import net.akehurst.kotlin.compose.components.flowHolder.mutableStateFlowHolderOf
import net.akehurst.kotlin.compose.editor.api.*
import kotlin.comparisons.minOf
import kotlin.math.roundToInt
import kotlin.ranges.coerceIn

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

data class CodeEditorState(
    val inputTextFieldState: State<TextFieldState>,
    val extendedSpans: ExtendedSpans,
    val inputScrollState: ScrollState,
//    val annotatedText: AnnotatedString,
    val giveFocus: Boolean,
    val lastTextLayoutResult: TextLayoutResult?,
    val viewFirstLine: Int,
    val viewLastLine: Int,
    val lineScrollOffset:Float,
    val lineTokens: Map<Int, List<EditorSegmentStyle>>,
//    val viewFirstLineStartTextPosition: Int,
//    val viewLastLineFinishTextPosition: Int,
//    val cursorDetails: CursorDetails,
//    val marginItemsVisible: List<MarginItem>,
//    val marginItemHovered: List<MarginItem>,
    val textMarkersVisible: List<TextMarkerDefault>,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CodeEditorView(
    editorState: CodeEditorStateHolder = CodeEditorStateHolder(
        initialText = "",
        defaultTextStyle = SpanStyle(color = MaterialTheme.colorScheme.onBackground)
    ),
    textStyle: TextStyle = TextStyle.Default,
    modifier: Modifier = Modifier,
    autocompleteModifier: Modifier = Modifier,
    marginItemHoverModifier: Modifier = Modifier,
) {

    //val state by remember { mutableStateOf(editorState) }
    val state: CodeEditorState = editorState.collectAsState()
    val marginItemsState = editorState.collectVisibleMarginItemsAsState( //TODO: maybe pass the CodeEditorState !
        state.viewFirstLine,
        state.viewLastLine,
        state.lineScrollOffset,
        state.lastTextLayoutResult
    )

    val scope = rememberCoroutineScope().also {
        editorState._autocompleteState.scope = it
    }

    Row(
        modifier = modifier//.weight(1f)
    ) {
        annotationMargin(marginItemsState, marginItemHoverModifier)
        textEditor(
            state = state,
            textStyle = textStyle,
            outputTransformation = {
                CodeEditorStateHolder.annotateTextFieldBuffer(
                    this,
                    state.viewFirstLine,
                    state.viewLastLine,
                    state.lineTokens,
                    state.textMarkersVisible,
                    { editorState.lastAnnotatedText = it}
                )
            },
            onTextLayout = { r ->
                r.invoke()?.let { editorState.onInputTextLayout(it) }
            },
            handlePreviewKeyEvent = { ev -> editorState.handlePreviewKeyEvent(ev) },
            handleKeyEvent = { ev -> editorState.handleKeyEvent(ev) },
            onTextChange = { editorState.onTextChange.invoke(it) },
            onInputScroll = { editorState.onInputScroll(it) },
            onFocusChanged = { editorState._isFocused = it; editorState._giveFocus = false }
        )

        AutocompletePopup2(
            state = editorState._autocompleteState,
            modifier = autocompleteModifier,
        )
    }
}

@Composable
fun annotationMargin(state: MarginItemsState, marginItemHoverModifier: Modifier = Modifier) {
    Box(
        modifier = Modifier
            .width(state.marginWidth)
            .fillMaxHeight()
//                .background(color = MaterialTheme.colorScheme.surfaceVariant)
            .wrapContentWidth(unbounded = true, align = Alignment.Start)
            .zIndex(1f),
    ) {
        state.visibleItems.forEach { item ->
            val interactionSource = remember { MutableInteractionSource() }
            Icon(
                imageVector = item.item.icon,
                contentDescription = item.item.text,
                tint = item.item.color,
                modifier = Modifier.width(state.marginWidth)
                    .offset(y = with(LocalDensity.current) {item.offsetFromTopOfViewport.toDp()})
                    .hoverable(interactionSource)
            )
            val isHovered = interactionSource.collectIsHoveredAsState().value
            if (isHovered) {
                Text(
                    text = item.item.text,
                    modifier = marginItemHoverModifier
                        .offset(
                            y = with(LocalDensity.current) {item.detailOffsetFromTopOfViewport.toDp()},
                            x = state.marginWidth / 2
                        )
                        .background(color = MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.dp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        .hoverable(interactionSource)
                )
            }
        }
    }
}

@Composable
fun textEditor(
    state: CodeEditorState,
    outputTransformation: OutputTransformation,
    textStyle: TextStyle,
    onTextLayout: (Density.(getResult: () -> TextLayoutResult?) -> Unit),
    handlePreviewKeyEvent: (KeyEvent) -> Boolean,
    handleKeyEvent: (KeyEvent) -> Boolean,
    onTextChange: (CharSequence) -> Unit,
    onInputScroll: (ScrollState) -> Unit,
    onFocusChanged: (Boolean) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val interactionSource = remember { MutableInteractionSource() }
    val focusRequester by remember { mutableStateOf(FocusRequester()) }
    Box(
        modifier = Modifier
            .fillMaxSize()
//                .background(color = MaterialTheme.colorScheme.surface),
    ) {

        BasicTextField(
            cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
            textStyle = textStyle,
            state = state.inputTextFieldState.value,
            modifier = Modifier
                .fillMaxSize()
                .onPreviewKeyEvent { ev -> handlePreviewKeyEvent(ev) }
                .onKeyEvent { ev -> handleKeyEvent(ev) }
                .focusRequester(focusRequester)
                .drawBehind(state.extendedSpans, state.inputScrollState.value.toFloat()),
            onTextLayout = onTextLayout,
            scrollState = state.inputScrollState,
            interactionSource = interactionSource,
            outputTransformation = outputTransformation,
        )

        // to invoke the 'onTextChange' callback when text changes
        LaunchedEffect(state.inputTextFieldState.value.text) {
            snapshotFlow { state.inputTextFieldState.value.text }.collect { scope.launch { onTextChange(it) } }
        }

        LaunchedEffect(state.inputScrollState.value) {
            snapshotFlow { state.inputScrollState }.collect { onInputScroll(it) }
        }

        val isFocused by interactionSource.collectIsFocusedAsState()
        LaunchedEffect(isFocused) { onFocusChanged.invoke(isFocused) }

        SideEffect {
            if (state.giveFocus) {
                focusRequester.requestFocus()
            }
        }

    }
}

class CodeEditorStateHolder(
    initialText: String = "",
    val defaultTextStyle: SpanStyle = SpanStyle(color = Color.Black, background = Color.White),
) : ComposeCodeEditor {
    companion object Companion {
        val STRAIGHT = SquigglyUnderlineSpanPainter(
            "STRAIGHT",
            width = 3.sp,
            wavelength = 20.sp,
            amplitude = 0.sp,
            bottomOffset = 1.sp,
            //animator = underlineAnimator
        )
        val SQUIGGLY = SquigglyUnderlineSpanPainter(
            "SQUIGGLY",
            width = 3.sp,
            wavelength = 15.sp,
            amplitude = 2.sp,
            bottomOffset = 1.sp,
            //animator = underlineAnimator
        )

        // currently used for margin item positions and limits the position to in range of first/last lines
        private fun offsetFromTopOfViewport(lineNumber: Int, viewFirstLine: Int, viewLastLine: Int, lineScrollOffset:Float, textLayoutResult: TextLayoutResult): Float {
            //val lineBot = textLayoutResult.getLineBottom(lineNumber)
//println("lineNumber: $lineNumber, viewFirstLine:$viewFirstLine, viewLastLine: $viewLastLine, lineScrollOffset: $lineScrollOffset")
            return when {
                lineNumber < (viewFirstLine+1) -> 0f // if line is partially visible, just use top of viewport
                lineNumber >= (viewLastLine-1) -> textLayoutResult.getLineTop(viewLastLine)- lineScrollOffset // likewise don't go below last line
                else -> {
                    val lineIndex = lineNumber - viewFirstLine
                    textLayoutResult.getLineTop(lineIndex) - lineScrollOffset
                }
            }
        }

        private fun lineHeight(textLayoutResult: TextLayoutResult, lineNumber: Int): Float {
            return if (lineNumber >= 0 && lineNumber < textLayoutResult.lineCount) {
                textLayoutResult.getLineBottom(lineNumber) - textLayoutResult.getLineTop(lineNumber)
            } else {
                0f
            }
        }

        internal fun annotateTextFieldBuffer(
            buffer: TextFieldBuffer,
            viewFirstLine: Int,
            viewLastLine: Int,
            lineStyles: Map<Int, List<EditorSegmentStyle>>,
            textMarkers: List<TextMarkerDefault>,
            annotatedTextChange: (AnnotatedString) -> Unit
        ) {
            val rawText = buffer.asCharSequence()
            if (rawText.isNotEmpty()) {
                val annotatedText = annotateText(rawText, viewFirstLine, viewLastLine, lineStyles, textMarkers)
                buffer.setComposition(0, rawText.length, annotatedText.annotations)
                buffer.changeTracker.trackChange(0, rawText.length, rawText.length)
                annotatedTextChange.invoke(annotatedText)
            }
        }

        fun annotateText(rawText: CharSequence, viewFirstLine: Int, viewLastLine: Int, lineTokens: Map<Int, List<EditorSegmentStyle>>, markers: List<TextMarkerDefault>): AnnotatedString {
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
                        val toks = lineTokens.getOrElse(lineNum) { emptyList() }
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
                        //println("Style at: ${offsetStart} .. ${offsetFinish}")
                        val ss = when (marker.decoration) {
                            TextDecorationStyle.NONE -> null
                            TextDecorationStyle.STRAIGHT -> STRAIGHT.decorate(marker.style, offsetStart, offsetFinish, builder = this)
                            TextDecorationStyle.SQUIGGLY -> SQUIGGLY.decorate(marker.style, offsetStart, offsetFinish, builder = this)
                        }
                        ss?.let { addStyle(it, offsetStart, offsetFinish) }
                    }
                }
            }
        }
    }

    private val MARGIN_WIDTH = 20.dp
    private val _interactionSource = MutableInteractionSource()
    private var _giveFocus = false
    private var _isFocused = false
    private val _focusRequester by mutableStateOf(FocusRequester())

//    private val inputTextFieldState by mutableStateOf(TextFieldState(initialText))
    private val _inputTextFieldState = mutableStateFlowHolderOf(TextFieldState(initialText))
    private val _inputRawText by derivedStateOf { snapshotFlow { _inputTextFieldState.value.text } }
    private val _lineStyles = mutableStateMapOf<Int, List<EditorSegmentStyle>>()

    internal var lastAnnotatedText: AnnotatedString? = null
    private val _inputScrollState by mutableStateOf(ScrollState(0))
    // val inputScrollerViewportSize get() = inputScrollState.maxValue// viewportSize
    // val inputScrollerOffset get() = inputScrollState.value

    // so we can get the annotatedstring
    private var _lastTextLayoutResult: TextLayoutResult? = null

    private val _viewCursor by mutableStateOf(CursorDetails(SolidColor(Color.Red)))
    private var _viewFirstLine by mutableStateOf(0)
    private var _viewLastLine by mutableStateOf(0)
    private var _lineScrollOffset by mutableStateOf(0f)
    private var _viewFirstLineStartTextPosition by mutableStateOf(0)
    private var _viewLastLineFinishTextPosition by mutableStateOf(0)

    //val inputTransformation = InputTransformation({
    //     annotateTextFieldBuffer(this)
    // })
//    private val outputTransformation = OutputTransformation({
//        annotateTextFieldBuffer(this)
//    })

    private val _autocompleteState by mutableStateOf(
        AutocompleteStateCompose( //TODO: don't get 'value' of inputTextFieldState flow
            getText = { this._inputTextFieldState.value.text },
            getCursorPosition = { this._inputTextFieldState.value.selection.min },
            getMenuOffset = { cursorPos() },
            insertText = { offset, txt ->
                val start = _inputTextFieldState.value.selection.min
                val end = _inputTextFieldState.value.selection.max
//                val s = minOf(start,end)
//                val e = maxOf(start,end)
                this._inputTextFieldState.value.edit {
                    this.replace(start - offset, end, txt)
                }
            }
        )
    )

    private val _marginItemsStateHolder = MarginItemsStateHolder()

    private val _textMarkersState by mutableStateOf(TextMarkerState()) //TODO: try using TextFieldDecorator for these
    private val _textMarkersVisible by derivedStateOf {
        _textMarkersState.markers.filter { _viewFirstLineStartTextPosition <= it.position && it.position <= _viewLastLineFinishTextPosition }
    }

    // val underlineAnimator = rememberSquigglyUnderlineAnimator()
    private val _extendedSpans = ExtendedSpans(STRAIGHT, SQUIGGLY)

    @Composable
    fun collectAsState(): CodeEditorState {
        return CodeEditorState(
            inputTextFieldState = this._inputTextFieldState.collectAsState(),
            extendedSpans = this._extendedSpans,
            inputScrollState = this._inputScrollState,
            giveFocus = this._giveFocus,
            lastTextLayoutResult = this._lastTextLayoutResult,
            viewFirstLine = this._viewFirstLine,
            viewLastLine = this._viewLastLine,
            lineScrollOffset = _lineScrollOffset,
            lineTokens = lineStyles,
            textMarkersVisible = _textMarkersVisible
        )
    }

    @Composable
    fun collectVisibleMarginItemsAsState(viewFirstLine: Int, viewLastLine:Int, lineScrollOffset:Float, textLayoutResult: TextLayoutResult?): MarginItemsState {
        val visibleItems = _marginItemsStateHolder.stateFlow.collectAsState()
        return MarginItemsState(
            marginWidth = MARGIN_WIDTH,
            visibleItems = visibleItems.value.map { item ->
                val offsetFromTopOfViewport = textLayoutResult?.let { tlr -> offsetFromTopOfViewport(item.lineNumber, viewFirstLine, viewLastLine,lineScrollOffset,tlr) } ?: 0f
                val detailOffset = textLayoutResult?.let { tlr -> offsetFromTopOfViewport + lineHeight(tlr, item.lineNumber) / 2 } ?: 0f
                MarginItemState(item, offsetFromTopOfViewport, detailOffset)
            }
        )
    }

    fun handlePreviewKeyEvent(ev: KeyEvent): Boolean {
        //println("$ev ${ev.key} ${ev.key.keyCode}")
        var handled = true
        when (ev.type) {
            KeyEventType.KeyDown -> when {
                this._autocompleteState.isVisible -> this._autocompleteState.handlePreviewKeyEvent(ev)
                else -> when {
                    ev.isCtrlPressed || ev.isMetaPressed -> when {
                        ev.isCtrlSpace -> _autocompleteState.open()
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

    @OptIn(ExperimentalFoundationApi::class)
    fun handleKeyEvent(ev: KeyEvent): Boolean {
        //println("$ev ${ev.key} ${ev.key.keyCode}")
        return when (ev.type) {
            // KeyDown | KeyUp | KeyPressed
            else -> when {
                ev.isCtrlSpace -> true
//                ev.isUndo -> {
//                    inputTextFieldState.undoState.undo()
//                    true
//                }
//
//                ev.isRedo -> {
//                    inputTextFieldState.undoState.redo()
//                    true
//                }

                else -> false
            }
        }
    }

    // useful helpers
    fun setNewText(text: String) {
        this._inputTextFieldState.update {
            TextFieldState(text, it.selection)
        }
        //this.inputTextFieldState.setTextAndPlaceCursorAtEnd(text)
    }

    fun onInputScroll(i: ScrollState) {
        if (null != _lastTextLayoutResult) {
            updateViewDetails(_lastTextLayoutResult!!)
        }
    }

    fun onInputTextLayout(textLayoutResult: TextLayoutResult) {
        _autocompleteState.close()
        updateViewDetails(textLayoutResult)
        _lastTextLayoutResult = textLayoutResult
        _extendedSpans.onTextLayout(textLayoutResult)
    }

    private fun updateViewDetails(textLayoutResult: TextLayoutResult) {
        val st = _inputScrollState.value.toFloat()
        val len = _inputScrollState.viewportSize
        _viewFirstLine = textLayoutResult.getLineForVerticalPosition(st)
        _viewLastLine = textLayoutResult.getLineForVerticalPosition(st + len-1)
        _viewFirstLineStartTextPosition = textLayoutResult.getLineStart(_viewFirstLine)
        _viewLastLineFinishTextPosition = textLayoutResult.getLineEnd(_viewLastLine)
        val topOfFirstLine = textLayoutResult.getLineTop(_viewFirstLine)
        _lineScrollOffset = st - topOfFirstLine
    }

    fun cursorPos(): IntOffset {
        // update the drawn cursor position
        val sel = _inputTextFieldState.value.selection
        val tlr = _lastTextLayoutResult
        return if (null != tlr) {
            val txtLen = tlr.layoutInput.text.length
            val selStart = sel.start.coerceIn(0, txtLen)
            val currentLine = tlr.getLineForOffset(selStart)
            val lineBot = tlr.getLineBottom(currentLine).roundToInt()
            val lineEnd = tlr.getLineEnd(currentLine, true)
            val cursOffset = tlr.getHorizontalPosition(minOf(selStart, lineEnd), true).roundToInt()
            val scrollOffset = _inputScrollState.value
            //println("currentLine=${currentLine}, lineBot=${lineBot}, cursOffset=$cursOffset,  scrollOffset=$scrollOffset")
            return IntOffset(cursOffset, lineBot - scrollOffset)
        } else {
            IntOffset(0, 0)
        }
    }

    fun setAllLineStyles(value: Map<Int, List<EditorSegmentStyle>>) {
        _lineStyles.clear()
        _lineStyles.putAll(value)
    }

    fun setLineStyles(lineNumber: Int, styles: List<EditorSegmentStyle>) {
        _lineStyles[lineNumber] = styles
    }

//    private fun annotateTextFieldBuffer(buffer: TextFieldBuffer) {
//        val rawText = buffer.asCharSequence()
//        if (rawText.isNotEmpty()) {
//            lastAnnotatedText = annotateText(rawText, this.viewFirstLine, this.viewLastLine, lineStyles, this.textMarkersVisible)
//            buffer.setComposition(0, rawText.length, lastAnnotatedText.annotations)
//            buffer.changeTracker.trackChange(0, rawText.length, rawText.length)
//        }
//    }

    // --- ComposeCodeEditor ---

    override var rawText: String
        get() = this._inputTextFieldState.value.text.toString()
        set(value) {
            this.setNewText(value)
        }

    override var lineStyles: Map<Int, List<EditorSegmentStyle>>
        get() = _lineStyles
        set(value) {
            setAllLineStyles(value)
        }

    override var annotatedText: AnnotatedString
        get() = this.lastAnnotatedText ?: AnnotatedString("")
        set(value) {
            this.lastAnnotatedText = value
            this.setNewText(value.text)
        }

    override var onTextChange: (CharSequence) -> Unit = {}

    override val marginItems: List<MarginItem> get() = _marginItemsStateHolder.value

    override val autocomplete: AutocompleteState get() = this._autocompleteState
    override var requestAutocompleteSuggestions: AutocompleteFunction
        get() = _autocompleteState.requestAutocompleteSuggestions
        set(value) {
            _autocompleteState.requestAutocompleteSuggestions = value
        }

    override fun focus() {
        this._giveFocus = true
    }

//    override fun refreshTokens() {
//        //FIXME: calling this seem to stop the undo/redo from working...not sure it is even needed
//        // create an edit causing a recomposition
//        // the refresh requires triggering of a text layout
//        inputTextFieldState.edit {
//            annotateTextFieldBuffer(this)
//        }
//    }

    override fun clearTextMarkers() {
        this._textMarkersState.clear()
    }

    override fun addTextMarker(position: Int, length: Int, style: SpanStyle, decoration: TextDecorationStyle) {
        this._textMarkersState.addMarker(position, length, style, decoration)
    }

    override fun clearMarginItems() {
        this._marginItemsStateHolder.clear()
    }

    override fun addMarginItem(lineNumber: Int, kind: String, text: String, icon: ImageVector, color: Color) {
        this._marginItemsStateHolder.addAnnotation(lineNumber, kind, text, icon, color)
    }


//    private fun addTextMarkers(textLayoutResult: TextLayoutResult) {
//        val annText = textLayoutResult.layoutInput.text
//        val rawText = textLayoutResult.layoutInput.text.toString()
//        val painters = mutableListOf<SquigglyUnderlineSpanPainter>()
//        // annotate from markers
//        for (marker in this.textMarkersVisible) {
//            // println("Marker at: ${marker.position} length ${marker.length} line $lineNum")
//            val offsetStart = (marker.position).coerceIn(0, rawText.length)
//            val offsetFinish = (marker.position + marker.length).coerceIn(0, rawText.length)
//            // println("Style at: ${offsetStart} .. ${offsetFinish}")
//            val p = SquigglyUnderlineSpanPainter(
//                width = 4.sp,
//                wavelength = 20.sp,
//                amplitude = 2.sp,
//                bottomOffset = 2.sp,
//                //animator = underlineAnimator
//            )
//            painters.add(p)
//        }
//
//        extendedSpans.extend(annText)
//        extendedSpans.onTextLayout(textLayoutResult)
//    }

}