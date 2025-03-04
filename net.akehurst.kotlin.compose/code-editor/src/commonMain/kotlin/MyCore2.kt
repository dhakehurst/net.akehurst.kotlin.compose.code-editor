/*
 * Copyright 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Modified by Dr. D.H. Akehurst adding the onScoll argument
 * as also done by Alex/n34t0 in [https://github.com/n34t0/compose-code-editor]
 *
 */

@file:Suppress(
    "DEPRECATION",
    "UNUSED",
    "INVISIBLE_MEMBER",
    "INVISIBLE_REFERENCE",
    "EQUALS_MISSING",
    "CANNOT_OVERRIDE_INVISIBLE_MEMBER"
)

package androidx.compose.foundation.text

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.handwriting.stylusHandwriting
import androidx.compose.foundation.text.input.internal.legacyTextInputServiceAdapterAndService
import androidx.compose.foundation.text.input.internal.legacyTextInputAdapter
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.OffsetProvider
import androidx.compose.foundation.text.selection.SelectionHandleAnchor
import androidx.compose.foundation.text.selection.SelectionHandleInfo
import androidx.compose.foundation.text.selection.SelectionHandleInfoKey
import androidx.compose.foundation.text.selection.SimpleLayout
import androidx.compose.foundation.text.selection.TextFieldSelectionHandle
import androidx.compose.foundation.text.selection.TextFieldSelectionManager
import androidx.compose.foundation.text.selection.isSelectionHandleInVisibleBound
import androidx.compose.foundation.text.selection.selectionGestureInput
import androidx.compose.foundation.text.selection.textFieldMagnifier
import androidx.compose.foundation.text.selection.updateSelectionTouchMode
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.DontMemoize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.RecomposeScope
import androidx.compose.runtime.currentRecomposeScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.LastBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.semantics.copyText
import androidx.compose.ui.semantics.cutText
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.editableText
import androidx.compose.ui.semantics.getTextLayoutResult
import androidx.compose.ui.semantics.insertTextAtCursor
import androidx.compose.ui.semantics.isEditable
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onImeAction
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.password
import androidx.compose.ui.semantics.pasteText
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setSelection
import androidx.compose.ui.semantics.setText
import androidx.compose.ui.semantics.textSelectionRange
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.CommitTextCommand
import androidx.compose.ui.text.input.DeleteAllCommand
import androidx.compose.ui.text.input.EditProcessor
import androidx.compose.ui.text.input.FinishComposingTextCommand
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.ImeOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TextInputService
import androidx.compose.ui.text.input.TextInputSession
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastRoundToInt
import kotlin.math.max
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
/*
/**
 * Base composable that enables users to edit text via hardware or software keyboard.
 *
 * This composable provides basic text editing functionality, however does not include any
 * decorations such as borders, hints/placeholder.
 *
 * If the editable text is larger than the size of the container, the vertical scrolling
 * behaviour will be automatically applied. To enable a single line behaviour with horizontal
 * scrolling instead, set the [maxLines] parameter to 1, [softWrap] to false, and
 * [ImeOptions.singleLine] to true.
 *
 * Whenever the user edits the text, [onValueChange] is called with the most up to date state
 * represented by [TextFieldValue]. [TextFieldValue] contains the text entered by user, as well
 * as selection, cursor and text composition information. Please check [TextFieldValue] for the
 * description of its contents.
 *
 * It is crucial that the value provided in the [onValueChange] is fed back into [CoreTextField] in
 * order to have the final state of the text being displayed. Example usage:
 *
 * Please keep in mind that [onValueChange] is useful to be informed about the latest state of the
 * text input by users, however it is generally not recommended to modify the values in the
 * [TextFieldValue] that you get via [onValueChange] callback. Any change to the values in
 * [TextFieldValue] may result in a context reset and end up with input session restart. Such
 * a scenario would cause glitches in the UI or text input experience for users.
 *
 * @param value The [androidx.compose.ui.text.input.TextFieldValue] to be shown in the [CoreTextField].
 * @param onValueChange Called when the input service updates the values in [TextFieldValue].
 * @param modifier optional [Modifier] for this text field.
 * @param textStyle Style configuration that applies at character level such as color, font etc.
 * @param visualTransformation The visual transformation filter for changing the visual
 * representation of the input. By default no visual transformation is applied.
 * @param onTextLayout Callback that is executed when a new text layout is calculated. A
 * [TextLayoutResult] object that callback provides contains paragraph information, size of the
 * text, baselines and other details. The callback can be used to add additional decoration or
 * functionality to the text. For example, to draw a cursor or selection around the text.
 * @param interactionSource the [MutableInteractionSource] representing the stream of
 * [Interaction]s for this CoreTextField. You can create and pass in your own remembered
 * [MutableInteractionSource] if you want to observe [Interaction]s and customize the
 * appearance / behavior of this CoreTextField in different [Interaction]s.
 * @param cursorBrush [Brush] to paint cursor with. If [SolidColor] with [Color.Unspecified]
 * provided, there will be no cursor drawn
 * @param softWrap Whether the text should break at soft line breaks. If false, the glyphs in the
 * text will be positioned as if there was unlimited horizontal space.
 * @param maxLines The maximum height in terms of maximum number of visible lines. It is required
 * that 1 <= [minLines] <= [maxLines].
 * @param minLines The minimum height in terms of minimum number of visible lines. It is required
 * that 1 <= [minLines] <= [maxLines].
 * @param imeOptions Contains different IME configuration options.
 * @param keyboardActions when the input service emits an IME action, the corresponding callback
 * is called. Note that this IME action may be different from what you specified in
 * [KeyboardOptions.imeAction].
 * @param enabled controls the enabled state of the text field. When `false`, the text
 * field will be neither editable nor focusable, the input of the text field will not be selectable
 * @param readOnly controls the editable state of the [CoreTextField]. When `true`, the text
 * field can not be modified, however, a user can focus it and copy text from it. Read-only text
 * fields are usually used to display pre-filled forms that user can not edit
 * @param decorationBox Composable lambda that allows to add decorations around text field, such
 * as icon, placeholder, helper messages or similar, and automatically increase the hit target area
 * of the text field. To allow you to control the placement of the inner text field relative to your
 * decorations, the text field implementation will pass in a framework-controlled composable
 * parameter "innerTextField" to the decorationBox lambda you provide. You must call
 * innerTextField exactly once.
 */
@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun MyCoreTextField2(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = TextStyle.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    onTextLayout: (TextLayoutResult) -> Unit = {},
    interactionSource: MutableInteractionSource? = null,
    cursorBrush: Brush = SolidColor(Color.Unspecified),
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = DefaultMinLines,
    imeOptions: ImeOptions = ImeOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    decorationBox: @Composable (innerTextField: @Composable () -> Unit) -> Unit =
        @Composable { innerTextField -> innerTextField() },
    textScrollerPosition: TextFieldScrollerPosition? = null,
    onScroll: (TextLayoutResult?) -> Unit = {}
) {
    val focusRequester = remember { FocusRequester() }
    val (legacyTextInputServiceAdapter, textInputService) =
        legacyTextInputServiceAdapterAndService()

    // CompositionLocals
    val density = LocalDensity.current
    val fontFamilyResolver = LocalFontFamilyResolver.current
    val selectionBackgroundColor = LocalTextSelectionColors.current.backgroundColor
    val focusManager = LocalFocusManager.current
    val windowInfo = LocalWindowInfo.current
    val keyboardController = LocalSoftwareKeyboardController.current

    // Scroll state
    val singleLine = maxLines == 1 && !softWrap && imeOptions.singleLine
    val orientation = if (singleLine) Orientation.Horizontal else Orientation.Vertical
    val scrollerPosition = textScrollerPosition ?: rememberSaveable(
        orientation,
        saver = TextFieldScrollerPosition.Saver
    ) { TextFieldScrollerPosition(orientation) }
    if (scrollerPosition.orientation != orientation) {
        throw IllegalArgumentException(
            "Mismatching scroller orientation; " + (
                    if (orientation == Orientation.Vertical)
                        "only single-line, non-wrap text fields can scroll horizontally"
                    else
                        "single-line, non-wrap text fields can only scroll horizontally"
                    )
        )
    }

    // State
    val transformedText = remember(value, visualTransformation) {
        val transformed = visualTransformation.filterWithValidation(value.annotatedString)

        value.composition?.let {
            TextFieldDelegate.applyCompositionDecoration(it, transformed)
        } ?: transformed
    }

    val visualText = transformedText.text
    val offsetMapping = transformedText.offsetMapping

    // If developer doesn't pass new value to TextField, recompose won't happen but internal state
    // and IME may think it is updated. To fix this inconsistent state, enforce recompose.
    val scope = currentRecomposeScope
    val state = remember(keyboardController) {
        LegacyTextFieldState(
            TextDelegate(
                text = visualText,
                style = textStyle,
                softWrap = softWrap,
                density = density,
                fontFamilyResolver = fontFamilyResolver
            ),
            recomposeScope = scope,
            keyboardController = keyboardController
        )
    }
    state.update(
        value.annotatedString,
        visualText,
        textStyle,
        softWrap,
        density,
        fontFamilyResolver,
        onValueChange,
        keyboardActions,
        focusManager,
        selectionBackgroundColor
    )

    // notify the EditProcessor of value every recomposition
    state.processor.reset(value, state.inputSession)

    val undoManager = remember { UndoManager() }
    undoManager.snapshotIfNeeded(value)

    val manager = remember { TextFieldSelectionManager(undoManager) }
    manager.offsetMapping = offsetMapping
    manager.visualTransformation = visualTransformation
    manager.onValueChange = state.onValueChange
    manager.state = state
    manager.value = value
    manager.clipboardManager = LocalClipboardManager.current
    manager.textToolbar = LocalTextToolbar.current
    manager.hapticFeedBack = LocalHapticFeedback.current
    manager.focusRequester = focusRequester
    manager.editable = !readOnly
    manager.enabled = enabled

    val coroutineScope = rememberCoroutineScope()
    val bringIntoViewRequester = remember { BringIntoViewRequester() }

    rememberClipboardEventsHandler(
        isEnabled = state.hasFocus,
        onCopy = { manager.onCopyWithResult() },
        onCut = { manager.onCutWithResult() },
        onPaste = { manager.paste(AnnotatedString(it)) }
    )

    // Focus
    val focusModifier = Modifier.textFieldFocusModifier(
        enabled = enabled,
        focusRequester = focusRequester,
        interactionSource = interactionSource
    ) {
        if (state.hasFocus == it.isFocused) {
            return@textFieldFocusModifier
        }
        state.hasFocus = it.isFocused

        if (state.hasFocus && enabled && !readOnly) {
            startInputSession(
                textInputService,
                state,
                value,
                imeOptions,
                offsetMapping
            )
        } else {
            endInputSession(state)
        }

        // The focusable modifier itself will request the entire focusable be brought into view
        // when it gains focus – in this case, that's the decoration box. However, since text
        // fields may have their own internal scrolling, and the decoration box can do anything,
        // we also need to specifically request that the cursor itself be brought into view.
        // TODO(b/216790855) If this request happens after the focusable's request, the field
        //  will only be scrolled far enough to show the cursor, _not_ the entire decoration
        //  box.
        if (it.isFocused) {
            state.layoutResult?.let { layoutResult ->
                coroutineScope.launch {
                    bringIntoViewRequester.bringSelectionEndIntoView(
                        value,
                        state.textDelegate,
                        layoutResult.value,
                        offsetMapping
                    )
                }
            }
        }
        if (!it.isFocused) manager.deselect()
    }

    // Hide the keyboard if made disabled or read-only while focused (b/237308379).
    val writeable by rememberUpdatedState(enabled && !readOnly)
    LaunchedEffect(Unit) {
        try {
            snapshotFlow { writeable }
                .collect { writeable ->
                    // When hasFocus changes, the session will be stopped/started in the focus
                    // handler so we don't need to handle its changes here.
                    if (writeable && state.hasFocus) {
                        startInputSession(
                            textInputService,
                            state,
                            manager.value,
                            imeOptions,
                            manager.offsetMapping
                        )
                    } else {
                        endInputSession(state)
                    }
                }
        } finally {
            // TODO(b/230536793) This is a workaround since we don't get an explicit focus blur
            //  event when the text field is removed from the composition entirely.
            endInputSession(state)
        }
    }

    val pointerModifier = Modifier.textFieldPointer(
        manager,
        enabled,
        interactionSource,
        state,
        focusRequester,
        readOnly,
        offsetMapping,
    )

    val drawModifier = Modifier.drawBehind {
        state.layoutResult?.let { layoutResult ->
            drawIntoCanvas { canvas ->
                TextFieldDelegate.draw(
                    canvas,
                    value,
                    state.selectionPreviewHighlightRange,
                    state.deletionPreviewHighlightRange,
                    offsetMapping,
                    layoutResult.value,
                    state.highlightPaint,
                    state.selectionBackgroundColor
                )
            }
        }
    }

    val onPositionedModifier = Modifier.onGloballyPositioned {
        state.layoutCoordinates = it
        state.layoutResult?.innerTextFieldCoordinates = it
        if (enabled) {
            if (state.handleState == HandleState.Selection) {
                if (state.showFloatingToolbar && windowInfo.isWindowFocused) {
                    manager.showSelectionToolbar()
                } else {
                    manager.hideSelectionToolbar()
                }
                state.showSelectionHandleStart =
                    manager.isSelectionHandleInVisibleBound(isStartHandle = true)
                state.showSelectionHandleEnd =
                    manager.isSelectionHandleInVisibleBound(isStartHandle = false)
                state.showCursorHandle = value.selection.collapsed
            } else if (state.handleState == HandleState.Cursor) {
                state.showCursorHandle =
                    manager.isSelectionHandleInVisibleBound(isStartHandle = true)
            }
            notifyFocusedRect(state, value, offsetMapping)
            state.layoutResult?.let { layoutResult ->
                state.inputSession?.let { inputSession ->
                    if (state.hasFocus) {
                        TextFieldDelegate.updateTextLayoutResult(
                            inputSession,
                            value,
                            offsetMapping,
                            layoutResult
                        )
                    }
                }
            }
        }
    }

    val isPassword = visualTransformation is PasswordVisualTransformation
    val semanticsModifier =
        Modifier.semantics(true) {
            // focused semantics are handled by Modifier.focusable()
            this.editableText = transformedText.text
            this.textSelectionRange = value.selection
            if (!enabled) this.disabled()
            if (isPassword) this.password()
            val editable = enabled && !readOnly
            isEditable = editable
            getTextLayoutResult {
                if (state.layoutResult != null) {
                    it.add(state.layoutResult!!.value)
                    true
                } else {
                    false
                }
            }
            if (editable) {
                setText { text ->
                    // If the action is performed while in an active text editing session, treat
                    // this like an IME command and update the text by going through the buffer.
                    // This keeps the buffer state consistent if other IME commands are performed
                    // before the next recomposition, and is used for the testing code path.
                    state.inputSession?.let { session ->
                        TextFieldDelegate.onEditCommand(
                            ops = listOf(DeleteAllCommand(), CommitTextCommand(text, 1)),
                            editProcessor = state.processor,
                            state.onValueChange,
                            session
                        )
                    }
                        ?: run {
                            state.onValueChange(
                                TextFieldValue(text.text, TextRange(text.text.length))
                            )
                        }
                    true
                }

                insertTextAtCursor { text ->
                    if (readOnly || !enabled) return@insertTextAtCursor false

                    // If the action is performed while in an active text editing session, treat
                    // this like an IME command and update the text by going through the buffer.
                    // This keeps the buffer state consistent if other IME commands are performed
                    // before the next recomposition, and is used for the testing code path.
                    state.inputSession?.let { session ->
                        TextFieldDelegate.onEditCommand(
                            // Finish composing text first because when the field is focused the IME
                            // might
                            // set composition.
                            ops = listOf(FinishComposingTextCommand(), CommitTextCommand(text, 1)),
                            editProcessor = state.processor,
                            state.onValueChange,
                            session
                        )
                    }
                        ?: run {
                            val newText =
                                value.text.replaceRange(
                                    value.selection.start,
                                    value.selection.end,
                                    text
                                )
                            val newCursor = TextRange(value.selection.start + text.length)
                            state.onValueChange(TextFieldValue(newText, newCursor))
                        }
                    true
                }
            }

            setSelection { selectionStart, selectionEnd, relativeToOriginalText ->
                // in traversal mode we get selection from the `textSelectionRange` semantics which
                // is
                // selection in original text. In non-traversal mode selection comes from the
                // Talkback
                // and indices are relative to the transformed text
                val start =
                    if (relativeToOriginalText) {
                        selectionStart
                    } else {
                        offsetMapping.transformedToOriginal(selectionStart)
                    }
                val end =
                    if (relativeToOriginalText) {
                        selectionEnd
                    } else {
                        offsetMapping.transformedToOriginal(selectionEnd)
                    }

                if (!enabled) {
                    false
                } else if (start == value.selection.start && end == value.selection.end) {
                    false
                } else if (
                    minOf(start, end) >= 0 && maxOf(start, end) <= value.annotatedString.length
                ) {
                    // Do not show toolbar if it's a traversal mode (with the volume keys), or
                    // if the cursor just moved to beginning or end.
                    if (relativeToOriginalText || start == end) {
                        manager.exitSelectionMode()
                    } else {
                        manager.enterSelectionMode()
                    }
                    state.onValueChange(
                        TextFieldValue(value.annotatedString, TextRange(start, end))
                    )
                    true
                } else {
                    manager.exitSelectionMode()
                    false
                }
            }
            onImeAction(imeOptions.imeAction) {
                // This will perform the appropriate default action if no handler has been
                // specified, so
                // as far as the platform is concerned, we always handle the action and never want
                // to
                // defer to the default _platform_ implementation.
                state.onImeActionPerformed(imeOptions.imeAction)
                true
            }
            onClick {
                // according to the documentation, we still need to provide proper semantics actions
                // even if the state is 'disabled'
                requestFocusAndShowKeyboardIfNeeded(state, focusRequester, !readOnly)
                true
            }
            onLongClick {
                manager.enterSelectionMode()
                true
            }
            if (!value.selection.collapsed && !isPassword) {
                copyText {
                    manager.copy()
                    true
                }
                if (enabled && !readOnly) {
                    cutText {
                        manager.cut()
                        true
                    }
                }
            }
            if (enabled && !readOnly) {
                pasteText {
                    manager.paste()
                    true
                }
            }
        }

    val showCursor = enabled && !readOnly && windowInfo.isWindowFocused && !state.hasHighlight()
    val cursorModifier = Modifier.cursor(state, value, offsetMapping, cursorBrush, showCursor)

    DisposableEffect(manager) {
        onDispose { manager.hideSelectionToolbar() }
    }

    DisposableEffect(imeOptions) {
        if (state.hasFocus) {
            state.inputSession = TextFieldDelegate.restartInput(
                textInputService = textInputService,
                value = value,
                editProcessor = state.processor,
                imeOptions = imeOptions,
                onValueChange = state.onValueChange,
                onImeActionPerformed = state.onImeActionPerformed
            )
        }
        onDispose { /* do nothing */ }
    }

    val textKeyInputModifier =
        Modifier.textFieldKeyInput(
            state = state,
            manager = manager,
            value = value,
            onValueChange = state.onValueChange,
            editable = !readOnly,
            singleLine = maxLines == 1,
            offsetMapping = offsetMapping,
            undoManager = undoManager,
            imeAction = imeOptions.imeAction,
        )

    val stylusHandwritingModifier = Modifier.stylusHandwriting(writeable) {
        if (!state.hasFocus) {
            focusRequester.requestFocus()
        }
        // If this is a password field, we can't trigger handwriting.
        // The expected behavior is 1) request focus 2) show software keyboard.
        // Note: TextField will show software keyboard automatically when it
        // gain focus. 3) show a toast message telling that handwriting is not
        // supported for password fields. TODO(b/335294152)
        if (imeOptions.keyboardType != KeyboardType.Password &&
            imeOptions.keyboardType != KeyboardType.NumberPassword
        ) {
            // TextInputService is calling LegacyTextInputServiceAdapter under the
            // hood.  And because it's a public API, startStylusHandwriting is added
            // to legacyTextInputServiceAdapter instead.
            // startStylusHandwriting may be called before the actual input
            // session starts when the editor is not focused, this is handled
            // internally by the LegacyTextInputServiceAdapter.
            legacyTextInputServiceAdapter.startStylusHandwriting()
        }
        true
    }

    val overscrollEffect = rememberTextFieldOverscrollEffect()

    // Modifiers that should be applied to the outer text field container. Usually those include
    // gesture and semantics modifiers.
    val decorationBoxModifier = modifier
        .legacyTextInputAdapter(legacyTextInputServiceAdapter, state, manager)
        .then(stylusHandwritingModifier)
        .then(focusModifier)
        .interceptDPadAndMoveFocus(state, focusManager)
        .previewKeyEventToDeselectOnBack(state, manager)
        .then(textKeyInputModifier)
        .textFieldScrollable(scrollerPosition, interactionSource, enabled, overscrollEffect)
        .then(pointerModifier)
        .then(semanticsModifier)
        .onGloballyPositioned @DontMemoize {
            state.layoutResult?.decorationBoxCoordinates = it
        }

    val showHandleAndMagnifier =
        enabled && state.hasFocus && state.isInTouchMode && windowInfo.isWindowFocused
    val magnifierModifier = if (showHandleAndMagnifier) {
        Modifier.textFieldMagnifier(manager)
    } else {
        Modifier
    }

    CoreTextFieldRootBox(decorationBoxModifier, manager) {
        decorationBox {
            fun Modifier.overscroll(): Modifier =
                overscrollEffect?.let {
                    this then it.effectModifier
                } ?: this

            // Modifiers applied directly to the internal input field implementation. In general,
            // these will most likely include draw, layout and IME related modifiers.
            val coreTextFieldModifier = Modifier
                // min height is set for maxLines == 1 in order to prevent text cuts for single line
                // TextFields
                .heightIn(min = state.minHeightForSingleLineField)
                .heightInLines(
                    textStyle = textStyle,
                    minLines = minLines,
                    maxLines = maxLines
                )
                .overscroll()
                .textFieldScroll(
                    scrollerPosition = scrollerPosition,
                    textFieldValue = value,
                    visualTransformation = visualTransformation,
                    textLayoutResultProvider = {
                        onScroll(state.layoutResult?.value)
                        state.layoutResult
                    },
                )
                .then(cursorModifier)
                .then(drawModifier)
                .textFieldMinSize(textStyle)
                .then(onPositionedModifier)
                .then(magnifierModifier)
                .bringIntoViewRequester(bringIntoViewRequester)

            SimpleLayout(coreTextFieldModifier) {
                Layout(
                    content = { },
                    measurePolicy = object : MeasurePolicy {
                        override fun MeasureScope.measure(
                            measurables: List<Measurable>,
                            constraints: Constraints
                        ): MeasureResult {
                            val prevProxy = Snapshot.withoutReadObservation { state.layoutResult }
                            val prevResult = prevProxy?.value
                            val (width, height, result) = TextFieldDelegate.layout(
                                state.textDelegate,
                                constraints,
                                layoutDirection,
                                prevResult
                            )
                            if (prevResult != result) {
                                state.layoutResult = TextLayoutResultProxy(
                                    value = result,
                                    decorationBoxCoordinates = prevProxy?.decorationBoxCoordinates,
                                )
                                onTextLayout(result)
                                notifyFocusedRect(state, value, offsetMapping)
                            }

                            // calculate the min height for single line text to prevent text cuts.
                            // for single line text maxLines puts in max height constraint based on
                            // constant characters therefore if the user enters a character that is
                            // longer (i.e. emoji or a tall script) the text is cut
                            state.minHeightForSingleLineField = with(density) {
                                when (maxLines) {
                                    1 -> result.getLineBottom(0).ceilToIntPx()
                                    else -> 0
                                }.toDp()
                            }

                            return layout(
                                width = width,
                                height = height,
                                alignmentLines = mapOf(
                                    FirstBaseline to result.firstBaseline.fastRoundToInt(),
                                    LastBaseline to result.lastBaseline.fastRoundToInt()
                                )
                            ) {}
                        }

                        override fun IntrinsicMeasureScope.maxIntrinsicWidth(
                            measurables: List<IntrinsicMeasurable>,
                            height: Int
                        ): Int {
                            state.textDelegate.layoutIntrinsics(layoutDirection)
                            return state.textDelegate.maxIntrinsicWidth
                        }
                    }
                )

                SelectionToolbarAndHandles(
                    manager = manager,
                    show = state.handleState != HandleState.None &&
                            state.layoutCoordinates != null &&
                            state.layoutCoordinates!!.isAttached &&
                            showHandleAndMagnifier
                )

                if (
                    state.handleState == HandleState.Cursor &&
                    !readOnly &&
                    showHandleAndMagnifier
                ) {
                    TextFieldCursorHandle(manager = manager)
                }
            }
        }
    }
}

@Composable
private fun CoreTextFieldRootBox(
    modifier: Modifier,
    manager: TextFieldSelectionManager,
    content: @Composable () -> Unit
) {
    Box(modifier, propagateMinConstraints = true) {
        ContextMenuArea(manager, content)
    }
}

// HandleState

// Handle

/**
 * Modifier to intercept back key presses, when supported by the platform, and deselect selected
 * text and clear selection popups.
 */
private fun Modifier.previewKeyEventToDeselectOnBack(
    state: LegacyTextFieldState,
    manager: TextFieldSelectionManager
) = onPreviewKeyEvent { keyEvent ->
    if (state.handleState == HandleState.Selection && keyEvent.cancelsTextSelection()) {
        manager.deselect()
        true
    } else {
        false
    }
}

internal class LegacyTextFieldState(
    var textDelegate: TextDelegate,
    val recomposeScope: RecomposeScope,
    val keyboardController: SoftwareKeyboardController?,
) {
    val processor = EditProcessor()
    var inputSession: TextInputSession? = null

    /**
     * This should be a state as every time we update the value we need to redraw it.
     * state observation during onDraw callback will make it work.
     */
    var hasFocus by mutableStateOf(false)

    /**
     * Set to a non-zero value for single line TextFields in order to prevent text cuts.
     */
    var minHeightForSingleLineField by mutableStateOf(0.dp)

    /**
     * The last layout coordinates for the inner text field LayoutNode, used by selection and
     * notifyFocusedRect. Since this layoutCoordinates only used for relative position calculation,
     * we are guarding ourselves from using it when it's not attached.
     */
    private var _layoutCoordinates: LayoutCoordinates? = null
    var layoutCoordinates: LayoutCoordinates?
        get() = _layoutCoordinates?.takeIf { it.isAttached }
        set(value) {
            _layoutCoordinates = value
        }

    /**
     * You should be using proxy type [TextLayoutResultProxy] if you need to translate touch
     * offset into text's coordinate system. For example, if you add a gesture on top of the
     * decoration box and want to know the character in text for the given touch offset on
     * decoration box.
     * When you don't need to shift the touch offset, you should be using `layoutResult.value`
     * which omits the proxy and calls the layout result directly. This is needed when you work
     * with the text directly, and not the decoration box. For example, cursor modifier gets
     * position using the [TextFieldValue.selection] value which corresponds to the text directly,
     * and therefore does not require the translation.
     */
    private val layoutResultState: MutableState<TextLayoutResultProxy?> = mutableStateOf(null)
    var layoutResult: TextLayoutResultProxy?
        get() = layoutResultState.value
        set(value) {
            layoutResultState.value = value
            isLayoutResultStale = false
        }

    /**
     * [textDelegate] keeps a reference to the visually transformed text that is visible to the
     * user. TextFieldState needs to have access to the underlying value that is not transformed
     * while making comparisons that test whether the user input actually changed.
     *
     * This field contains the real value that is passed by the user before it was visually
     * transformed.
     */
    var untransformedText: AnnotatedString? = null

    /**
     * The gesture detector state, to indicate whether current state is selection, cursor
     * or editing.
     *
     * In the none state, no selection or cursor handle is shown, only the cursor is shown.
     * TextField is initially in this state. To enter this state, input anything from the
     * keyboard and modify the text.
     *
     * In the selection state, there is no cursor shown, only selection is shown. To enter
     * the selection mode, just long press on the screen. In this mode, finger movement on the
     * screen changes selection instead of moving the cursor.
     *
     * In the cursor state, no selection is shown, and the cursor and the cursor handle are shown.
     * To enter the cursor state, tap anywhere within the TextField.(The TextField will stay in the
     * edit state if the current text is empty.) In this mode, finger movement on the screen
     * moves the cursor.
     */
    var handleState by mutableStateOf(HandleState.None)

    /**
     * A flag to check if the floating toolbar should show.
     *
     * This state is meant to represent the floating toolbar status regardless of if all touch
     * behaviors are disabled (like if the user is using a mouse). This is so that when touch
     * behaviors are re-enabled, the toolbar status will still reflect whether it should be shown
     * at that point.
     */
    var showFloatingToolbar by mutableStateOf(false)

    /**
     * True if the position of the selection start handle is within a visible part of the window
     * (i.e. not scrolled out of view) and the handle should be drawn.
     */
    var showSelectionHandleStart by mutableStateOf(false)

    /**
     * True if the position of the selection end handle is within a visible part of the window
     * (i.e. not scrolled out of view) and the handle should be drawn.
     */
    var showSelectionHandleEnd by mutableStateOf(false)

    /**
     * True if the position of the cursor is within a visible part of the window (i.e. not scrolled
     * out of view) and the handle should be drawn.
     */
    var showCursorHandle by mutableStateOf(false)

    /**
     * TextFieldState holds both TextDelegate and layout result. However, these two values are not
     * updated at the same time. TextDelegate is updated during composition according to new
     * arguments while layoutResult is updated during layout phase. Therefore, [layoutResult] might
     * not indicate the result of [textDelegate] at a given time during composition. This variable
     * indicates whether layout result is lacking behind the latest TextDelegate.
     */
    var isLayoutResultStale: Boolean = true
        private set

    var isInTouchMode: Boolean by mutableStateOf(true)

    private val keyboardActionRunner: KeyboardActionRunner =
        KeyboardActionRunner(keyboardController)

    /**
     * DO NOT USE, use [onValueChange] instead. This is original callback provided to the TextField.
     * In order the CoreTextField to work, the recompose.invalidate() has to be called when we call
     * the callback and [onValueChange] is a wrapper that mainly does that.
     */
    private var onValueChangeOriginal: (TextFieldValue) -> Unit = {}

    val onValueChange: (TextFieldValue) -> Unit = {
        if (it.text != untransformedText?.text) {
            // Text has been changed, enter the HandleState.None and hide the cursor handle.
            handleState = HandleState.None
        }
        selectionPreviewHighlightRange = TextRange.Zero
        deletionPreviewHighlightRange = TextRange.Zero
        onValueChangeOriginal(it)
        recomposeScope.invalidate()
    }

    val onImeActionPerformed: (ImeAction) -> Unit = { imeAction ->
        keyboardActionRunner.runAction(imeAction)
    }

    /** The paint used to draw highlight backgrounds. */
    val highlightPaint: Paint = Paint()
    var selectionBackgroundColor = Color.Unspecified

    /** Range of text to be highlighted to display handwriting gesture previews from the IME. */
    var selectionPreviewHighlightRange: TextRange by mutableStateOf(TextRange.Zero)
    var deletionPreviewHighlightRange: TextRange by mutableStateOf(TextRange.Zero)

    fun hasHighlight() =
        !selectionPreviewHighlightRange.collapsed || !deletionPreviewHighlightRange.collapsed

    fun update(
        untransformedText: AnnotatedString,
        visualText: AnnotatedString,
        textStyle: TextStyle,
        softWrap: Boolean,
        density: Density,
        fontFamilyResolver: FontFamily.Resolver,
        onValueChange: (TextFieldValue) -> Unit,
        keyboardActions: KeyboardActions,
        focusManager: FocusManager,
        selectionBackgroundColor: Color
    ) {
        this.onValueChangeOriginal = onValueChange
        this.selectionBackgroundColor = selectionBackgroundColor
        this.keyboardActionRunner.apply {
            this.keyboardActions = keyboardActions
            this.focusManager = focusManager
        }
        this.untransformedText = untransformedText

        val newTextDelegate = updateTextDelegate(
            current = textDelegate,
            text = visualText,
            style = textStyle,
            softWrap = softWrap,
            density = density,
            fontFamilyResolver = fontFamilyResolver,
            placeholders = emptyList(),
        )

        if (textDelegate !== newTextDelegate) isLayoutResultStale = true
        textDelegate = newTextDelegate
    }
}

// requestFocusAndShowKeyboardIfNeeded

private fun startInputSession(
    textInputService: TextInputService,
    state: LegacyTextFieldState,
    value: TextFieldValue,
    imeOptions: ImeOptions,
    offsetMapping: OffsetMapping
) {
    state.inputSession = TextFieldDelegate.onFocus(
        textInputService,
        value,
        state.processor,
        imeOptions,
        state.onValueChange,
        state.onImeActionPerformed
    )
    notifyFocusedRect(state, value, offsetMapping)
}

private fun endInputSession(state: LegacyTextFieldState) {
    state.inputSession?.let { session ->
        TextFieldDelegate.onBlur(session, state.processor, state.onValueChange)
    }
    state.inputSession = null
}

// BringIntoViewRequester.bringSelectionEndIntoView

@Composable
private fun SelectionToolbarAndHandles(manager: TextFieldSelectionManager, show: Boolean) {
    with(manager) {
        if (show) {
            // Check whether text layout result became stale. A stale text layout might be
            // completely unrelated to current TextFieldValue, causing offset errors.
            state?.layoutResult?.value?.takeIf { !(state?.isLayoutResultStale ?: true) }?.let {
                if (!value.selection.collapsed) {
                    val startOffset = offsetMapping.originalToTransformed(value.selection.start)
                    val endOffset = offsetMapping.originalToTransformed(value.selection.end)
                    val startDirection = it.getBidiRunDirection(startOffset)
                    val endDirection = it.getBidiRunDirection(max(endOffset - 1, 0))
                    if (manager.state?.showSelectionHandleStart == true) {
                        TextFieldSelectionHandle(
                            isStartHandle = true,
                            direction = startDirection,
                            manager = manager
                        )
                    }
                    if (manager.state?.showSelectionHandleEnd == true) {
                        TextFieldSelectionHandle(
                            isStartHandle = false,
                            direction = endDirection,
                            manager = manager
                        )
                    }
                }

                state?.let { textFieldState ->
                    // If in selection mode (when the floating toolbar is shown) a new symbol
                    // from the keyboard is entered, text field should enter the editing mode
                    // instead.
                    if (isTextChanged()) textFieldState.showFloatingToolbar = false
                    if (textFieldState.hasFocus) {
                        if (textFieldState.showFloatingToolbar) showSelectionToolbar()
                        else hideSelectionToolbar()
                    }
                }
            }
        } else hideSelectionToolbar()
    }
}

// TextFieldCursorHandle

// CursorHandle

// TODO(b/262648050) Try to find a better API.
private fun notifyFocusedRect(
    state: LegacyTextFieldState,
    value: TextFieldValue,
    offsetMapping: OffsetMapping
) {
    // If this reports state reads it causes an invalidation cycle.
    // This function doesn't need to be invalidated anyway because it's already explicitly called
    // after updating text layout or position.
    Snapshot.withoutReadObservation {
        val layoutResult = state.layoutResult ?: return
        val inputSession = state.inputSession ?: return
        val layoutCoordinates = state.layoutCoordinates ?: return
        TextFieldDelegate.notifyFocusedRect(
            value,
            state.textDelegate,
            layoutResult.value,
            layoutCoordinates,
            inputSession,
            state.hasFocus,
            offsetMapping
        )
    }
}
*/