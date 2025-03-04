/*
 * Copyright 2024 The Android Open Source Project
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
 */

@file:Suppress("UNUSED", "INVISIBLE_MEMBER", "INVISIBLE_REFERENCE", "EQUALS_MISSING", "CANNOT_OVERRIDE_INVISIBLE_MEMBER")
@file:OptIn(ExperimentalFoundationApi::class)
package net.akehurst.kotlin.compose.editor.text

import androidx.compose.foundation.text.input.internal.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.content.MediaType
import androidx.compose.foundation.content.TransferableContent
import androidx.compose.foundation.content.internal.ReceiveContentConfiguration
import androidx.compose.foundation.content.internal.dragAndDropRequestPermission
import androidx.compose.foundation.content.internal.getReceiveContentConfiguration
import androidx.compose.foundation.content.readPlainText
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.Handle
import androidx.compose.foundation.text.KeyCommand
import androidx.compose.foundation.text.KeyboardActionScope
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.LocalAutofillHighlightColor
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.internal.selection.TextFieldSelectionState
import androidx.compose.foundation.text.input.internal.selection.TextToolbarState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.autofill.ContentDataType
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusEventModifierNode
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequesterModifierNode
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.focus.requestFocus
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyInputModifierNode
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.SuspendingPointerInputModifierNode
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.modifier.ModifierLocalModifierNode
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.LayoutAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.ObserverModifierNode
import androidx.compose.ui.node.PointerInputModifierNode
import androidx.compose.ui.node.SemanticsModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.invalidateSemantics
import androidx.compose.ui.node.observeReads
import androidx.compose.ui.node.requestAutofill
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.PlatformTextInputModifierNode
import androidx.compose.ui.platform.PlatformTextInputSession
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.platform.establishTextInputSession
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.contentDataType
import androidx.compose.ui.semantics.copyText
import androidx.compose.ui.semantics.cutText
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.editableText
import androidx.compose.ui.semantics.getTextLayoutResult
import androidx.compose.ui.semantics.insertTextAtCursor
import androidx.compose.ui.semantics.isEditable
import androidx.compose.ui.semantics.onAutofillText
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onImeAction
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.password
import androidx.compose.ui.semantics.pasteText
import androidx.compose.ui.semantics.setSelection
import androidx.compose.ui.semantics.setText
import androidx.compose.ui.semantics.textSelectionRange
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.ImeOptions
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlin.compareTo

@OptIn(ExperimentalFoundationApi::class) private val MediaTypesText = setOf(MediaType.Text)

@OptIn(ExperimentalFoundationApi::class) private val MediaTypesAll = setOf(MediaType.All)

/**
 * Modifier element for most of the functionality of [BasicTextField] that is attached to the
 * decoration box. This is only half the actual modifiers for the field, the other half are only
 * attached to the internal text field.
 *
 * This modifier handles input events (both key and pointer), semantics, and focus.
 */
internal data class TextFieldDecoratorModifier3(
    private val textFieldState: TransformedTextFieldState3,
    private val textLayoutState: TextLayoutState3,
    private val textFieldSelectionState: TextFieldSelectionState3,
    private val filter: InputTransformation?,
    private val enabled: Boolean,
    private val readOnly: Boolean,
    private val keyboardOptions: KeyboardOptions,
    private val keyboardActionHandler: KeyboardActionHandler?,
    private val singleLine: Boolean,
    private val interactionSource: MutableInteractionSource,
    private val isPassword: Boolean,
    private val stylusHandwritingTrigger: MutableSharedFlow<Unit>?
) : ModifierNodeElement<TextFieldDecoratorModifierNode3>() {
    override fun create(): TextFieldDecoratorModifierNode3 =
        TextFieldDecoratorModifierNode3(
            textFieldState = textFieldState,
            textLayoutState = textLayoutState,
            textFieldSelectionState = textFieldSelectionState,
            filter = filter,
            enabled = enabled,
            readOnly = readOnly,
            keyboardOptions = keyboardOptions,
            keyboardActionHandler = keyboardActionHandler,
            singleLine = singleLine,
            interactionSource = interactionSource,
            isPassword = isPassword,
            stylusHandwritingTrigger = stylusHandwritingTrigger
        )

    override fun update(node: TextFieldDecoratorModifierNode3) {
        node.updateNode(
            textFieldState = textFieldState,
            textLayoutState = textLayoutState,
            textFieldSelectionState = textFieldSelectionState,
            filter = filter,
            enabled = enabled,
            readOnly = readOnly,
            keyboardOptions = keyboardOptions,
            keyboardActionHandler = keyboardActionHandler,
            singleLine = singleLine,
            interactionSource = interactionSource,
            isPassword = isPassword,
            stylusHandwritingTrigger = stylusHandwritingTrigger
        )
    }

    override fun InspectorInfo.inspectableProperties() {
        // Show nothing in the inspector.
    }
}

/** Modifier node for [TextFieldDecoratorModifier3]. */
@OptIn(ExperimentalFoundationApi::class)
internal class TextFieldDecoratorModifierNode3(
    var textFieldState: TransformedTextFieldState3,
    var textLayoutState: TextLayoutState3,
    var textFieldSelectionState: TextFieldSelectionState3,
    var filter: InputTransformation?,
    var enabled: Boolean,
    var readOnly: Boolean,
    var keyboardOptions: KeyboardOptions,
    var keyboardActionHandler: KeyboardActionHandler?,
    var singleLine: Boolean,
    var interactionSource: MutableInteractionSource,
    var isPassword: Boolean,
    var stylusHandwritingTrigger: MutableSharedFlow<Unit>?
) :
    DelegatingNode(),
    DrawModifierNode,
    PlatformTextInputModifierNode,
    SemanticsModifierNode,
    FocusRequesterModifierNode,
    FocusEventModifierNode,
    GlobalPositionAwareModifierNode,
    PointerInputModifierNode,
    KeyInputModifierNode,
    CompositionLocalConsumerModifierNode,
    ModifierLocalModifierNode,
    ObserverModifierNode,
    LayoutAwareModifierNode {

    init {
        textFieldSelectionState.requestAutofillAction = { requestAutofill() }
    }

    private val pointerInputNode =
        delegate(
            SuspendingPointerInputModifierNode {
                coroutineScope {
                    with(textFieldSelectionState) {
                        val requestFocus = { if (!isFocused) requestFocus() }

                        launch(start = CoroutineStart.UNDISPATCHED) { detectTouchMode() }
                      /*  launch(start = CoroutineStart.UNDISPATCHED) {
                            detectTextFieldTapGestures(
                                requestFocus = requestFocus,
                                showKeyboard = {
                                    if (inputSessionJob != null) {
                                        // just reshow the keyboard in existing session
                                        requireKeyboardController().show()
                                    } else {
                                        startInputSession(fromTap = true)
                                    }
                                },
                                interactionSource = interactionSource
                            )
                        }*/
                        launch(start = CoroutineStart.UNDISPATCHED) {
                            textFieldSelectionGestures(requestFocus)
                        }
                    }
                }
            }
        )

    /**
     * The last enter event that was submitted to [interactionSource] from [dragAndDropNode]. We
     * need to keep a reference to this event to send a follow-up exit event.
     *
     * We are using interaction source hover state as a hacky capsule to carry dragging events to
     * core modifier node which draws the cursor and shows the magnifier. TextFields are not really
     * focused when a dragging text hovers over them. Focused TextFields should have active input
     * connections that is not required in a drag and drop scenario.
     *
     * When proper hover events are implemented for [interactionSource], the below code in
     * [dragAndDropNode] should be revised.
     */
    private var dragEnterEvent: HoverInteraction.Enter? = null

    /** Special Drag and Drop node for BasicTextField that is also aware of `receiveContent` API. */
    private val dragAndDropNode =
        delegate(
            textFieldDragAndDropNode(
                hintMediaTypes = {
                    val receiveContentConfiguration = getReceiveContentConfiguration()
                    // if ReceiveContentConfiguration is set, all drag events should be accepted.
                    // ContentReceiver handler should evaluate the incoming content.
                    if (receiveContentConfiguration != null) {
                        MediaTypesAll
                    } else {
                        MediaTypesText
                    }
                },
                dragAndDropRequestPermission = {
                    if (getReceiveContentConfiguration() != null) {
                        dragAndDropRequestPermission(it)
                    }
                },
                onEntered = {
                    dragEnterEvent = HoverInteraction.Enter().also { interactionSource.tryEmit(it) }
                    // Although BasicTextField itself is not a `receiveContent` node, it should
                    // behave like one. Delegate the enter event to the ancestor nodes just like
                    // `receiveContent` itself would.
                    getReceiveContentConfiguration()?.receiveContentListener?.onDragEnter()
                },
                onMoved = { position ->
                    val positionOnTextField = textLayoutState.fromWindowToDecoration(position)
                    val cursorPosition = textLayoutState.getOffsetForPosition(positionOnTextField)
                    textFieldState.selectCharsIn(TextRange(cursorPosition))
                    textFieldSelectionState.updateHandleDragging(Handle.Cursor, positionOnTextField)
                },
                onDrop = { clipEntry, clipMetadata ->
                    emitDragExitEvent()
                    textFieldSelectionState.clearHandleDragging()
                    var plainText = clipEntry.readPlainText()

                    val receiveContentConfiguration = getReceiveContentConfiguration()
                    // if receiveContent configuration is set, all drag operations should be
                    // accepted. ReceiveContent handler should evaluate the incoming content.
                    if (receiveContentConfiguration != null) {
                        val transferableContent =
                            TransferableContent(
                                clipEntry,
                                clipMetadata,
                                TransferableContent.Source.DragAndDrop
                            )

                        val remaining =
                            receiveContentConfiguration.receiveContentListener.onReceive(
                                transferableContent
                            )
                        plainText = remaining?.clipEntry?.readPlainText()
                    }
                    plainText?.let(textFieldState::replaceSelectedText)
                    true
                },
                onExited = {
                    emitDragExitEvent()
                    textFieldSelectionState.clearHandleDragging()
                    // Although BasicTextField itself is not a `receiveContent` node, it should
                    // behave like one. Delegate the exit event to the ancestor nodes just like
                    // `receiveContent` itself would.
                    getReceiveContentConfiguration()?.receiveContentListener?.onDragExit()
                },
                onEnded = { emitDragExitEvent() }
            )
        )

    /**
     * Needs to be kept separate from a window focus so we can restart an input session when the
     * window receives the focus back. Element can stay focused even if the window loses its focus.
     */
    private var isElementFocused: Boolean = false

    /** Keeps focus state of the window */
    private var windowInfo: WindowInfo? = null

    private val isFocused: Boolean
        get() {
            // make sure that we read both window focus and element focus for snapshot aware
            // callers to successfully update when either one changes
            val isWindowFocused = windowInfo?.isWindowFocused == true
            return isElementFocused && isWindowFocused
        }

    /**
     * We observe text changes to show/hide text toolbar and cursor handles. This job is only run
     * when [isFocused] is true, and cancels when focus is lost.
     */
    private var observeChangesJob: Job? = null

    /**
     * Manages key events. These events often are sourced by a hardware keyboard but it's also
     * possible that IME or some other platform system simulates a KeyEvent.
     */
    private val textFieldKeyEventHandler = createTextFieldKeyEventHandler()

    private val keyboardActionScope =
        object : KeyboardActionScope {
            private val focusManager: FocusManager
                get() = currentValueOf(LocalFocusManager)

            override fun defaultKeyboardAction(imeAction: ImeAction) {
                when (imeAction) {
                    ImeAction.Next -> {
                        focusManager.moveFocus(FocusDirection.Next)
                    }
                    ImeAction.Previous -> {
                        focusManager.moveFocus(FocusDirection.Previous)
                    }
                    ImeAction.Done -> {
                        requireKeyboardController().hide()
                    }
                    else -> Unit
                }
            }
        }

    /**
     * A handler dedicated for Clipboard related key commands. We extracted it from
     * [textFieldKeyEventHandler] because Clipboard actions require a [coroutineScope] which is
     * available here.
     */
    private val clipboardKeyCommandsHandler = ClipboardKeyCommandsHandler { keyCommand ->
        coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
            when (keyCommand) {
                KeyCommand.COPY -> textFieldSelectionState.copy(false)
                KeyCommand.CUT -> textFieldSelectionState.cut()
                KeyCommand.PASTE -> textFieldSelectionState.paste()
                else -> Unit
            }
        }
    }

    /**
     * A coroutine job that observes text and layout changes in selection state to react to those
     * changes.
     */
    private var inputSessionJob: Job? = null

    private val receiveContentConfigurationProvider: () -> ReceiveContentConfiguration? = {
        getReceiveContentConfiguration()
    }

    // Mutable state to hold the autofillHighlightOn value
    private var autofillHighlightOn by mutableStateOf(false)

    override fun ContentDrawScope.draw() {
        drawContent()

        // Autofill highlight is drawn on top of the content — this way the coloring appears over
        // any Material background applied.
        if (autofillHighlightOn) {
            drawRect(color = currentValueOf(LocalAutofillHighlightColor))
        }
    }

    private suspend fun observeUntransformedTextChanges() {
        snapshotFlow { textFieldState.untransformedText.toString() }
            .drop(1)
            .take(1)
            .collect { autofillHighlightOn = false }
    }

    /** Updates all the related properties and invalidates internal state based on the changes. */
    fun updateNode(
        textFieldState: TransformedTextFieldState3,
        textLayoutState: TextLayoutState3,
        textFieldSelectionState: TextFieldSelectionState3,
        filter: InputTransformation?,
        enabled: Boolean,
        readOnly: Boolean,
        keyboardOptions: KeyboardOptions,
        keyboardActionHandler: KeyboardActionHandler?,
        singleLine: Boolean,
        interactionSource: MutableInteractionSource,
        isPassword: Boolean,
        stylusHandwritingTrigger: MutableSharedFlow<Unit>?
    ) {
        // Find the diff: current previous and new values before updating current.
        val previousEditable = this.enabled && !this.readOnly
        val previousEnabled = this.enabled
        val previousTextFieldState = this.textFieldState
        val previousKeyboardOptions = this.keyboardOptions
        val previousTextFieldSelectionState = this.textFieldSelectionState
        val previousInteractionSource = this.interactionSource
        val previousIsPassword = this.isPassword
        val previousStylusHandwritingTrigger = this.stylusHandwritingTrigger
        val editable = enabled && !readOnly

        // Apply the diff.
        this.textFieldState = textFieldState
        this.textLayoutState = textLayoutState
        this.textFieldSelectionState = textFieldSelectionState
        this.filter = filter
        this.enabled = enabled
        this.readOnly = readOnly
        this.keyboardOptions = keyboardOptions
        this.keyboardActionHandler = keyboardActionHandler
        this.singleLine = singleLine
        this.interactionSource = interactionSource
        this.isPassword = isPassword
        this.stylusHandwritingTrigger = stylusHandwritingTrigger

        // React to diff.
        // Something about the session changed, restart the session.
        if (
            editable != previousEditable ||
            textFieldState != previousTextFieldState ||
            keyboardOptions != previousKeyboardOptions ||
            stylusHandwritingTrigger != previousStylusHandwritingTrigger
        ) {
            if (editable && isFocused) {
                // The old session will be implicitly disposed.
                startInputSession(fromTap = false)
            } else if (!editable) {
                // We were made read-only or disabled, hide the keyboard.
                disposeInputSession()
            }
        }

        if (
            enabled != previousEnabled ||
            editable != previousEditable ||
            keyboardOptions.imeActionOrDefault != previousKeyboardOptions.imeActionOrDefault ||
            isPassword != previousIsPassword
        ) {
            invalidateSemantics()
        }

        if (textFieldSelectionState != previousTextFieldSelectionState) {
            pointerInputNode.resetPointerInputHandler()
            if (isAttached) {
                textFieldSelectionState.receiveContentConfiguration =
                    receiveContentConfigurationProvider
            }
            textFieldSelectionState.requestAutofillAction = { requestAutofill() }
        }

        if (interactionSource != previousInteractionSource) {
            pointerInputNode.resetPointerInputHandler()
        }
    }

    override val shouldMergeDescendantSemantics: Boolean
        get() = true

    // This function is called inside a snapshot observer.
    override fun SemanticsPropertyReceiver.applySemantics() {
        val text = textFieldState.outputText
        val selection = text.selection
        editableText = AnnotatedString(text.toString())
        textSelectionRange = selection

        if (!enabled) disabled()
        if (isPassword) password()

        val editable = enabled && !readOnly
        isEditable = editable

        // The developer will set `contentType`. TF populates the other autofill-related
        // semantics. And since we're in a TextField, set the `contentDataType` to be "Text".
        this.contentDataType = ContentDataType.Text

        onAutofillText { newText ->
            if (!editable) return@onAutofillText false
            textFieldState.replaceAll(newText)
            autofillHighlightOn = true
            coroutineScope.launch { observeUntransformedTextChanges() }
            true
        }

        getTextLayoutResult {
            textLayoutState.layoutResult?.let { result -> it.add(result) } ?: false
        }
        if (editable) {
            setText { newText ->
                if (!editable) return@setText false

                textFieldState.replaceAll(newText)
                true
            }
            insertTextAtCursor { newText ->
                if (!editable) return@insertTextAtCursor false

                // Finish composing text first because when the field is focused the IME
                // might set composition.
                textFieldState.replaceSelectedText(newText, clearComposition = true)
                true
            }
        }
        @Suppress("NAME_SHADOWING")
        setSelection { start, end, relativeToOriginal ->
            // in traversal mode (relativeToOriginal=true) we get selection from the
            // `textSelectionRange` semantics which is selection in original text. In non-traversal
            // mode selection comes from the Talkback and indices are relative to the transformed
            // text
            val text =
                if (relativeToOriginal) {
                    textFieldState.untransformedText
                } else {
                    textFieldState.visualText
                }
            val selection = text.selection

            if (!enabled || minOf(start, end) < 0 || maxOf(start, end) > text.length) {
                return@setSelection false
            }

            // Selection is already selected, don't need to do any work.
            if (start == selection.start && end == selection.end) {
                return@setSelection true
            }

            val selectionRange = TextRange(start, end)
            // Do not show toolbar if it's a traversal mode (with the volume keys), or if the
            // selection is collapsed.
            if (relativeToOriginal || start == end) {
                textFieldSelectionState.updateTextToolbarState(TextToolbarState.None)
            } else {
                textFieldSelectionState.updateTextToolbarState(TextToolbarState.Selection)
            }
            if (relativeToOriginal) {
                textFieldState.selectUntransformedCharsIn(selectionRange)
            } else {
                textFieldState.selectCharsIn(selectionRange)
            }
            return@setSelection true
        }

        val effectiveImeAction = keyboardOptions.imeActionOrDefault
        onImeAction(effectiveImeAction) {
            onImeActionPerformed(effectiveImeAction)
            true
        }
        onClick {
            // according to the documentation, we still need to provide proper semantics actions
            // even if the state is 'disabled'
            if (!isFocused) {
                requestFocus()
            } else if (!readOnly) {
                requireKeyboardController().show()
            }
            true
        }
        onLongClick {
            if (!isFocused) {
                requestFocus()
            }
            textFieldSelectionState.updateTextToolbarState(TextToolbarState.Selection)
            true
        }
        if (!selection.collapsed && !isPassword) {
            copyText {
                coroutineScope.launch { textFieldSelectionState.copy() }
                true
            }
            if (enabled && !readOnly) {
                cutText {
                    coroutineScope.launch { textFieldSelectionState.cut() }
                    true
                }
            }
        }
        if (editable) {
            pasteText {
                coroutineScope.launch { textFieldSelectionState.paste() }
                true
            }
        }

        filter?.let { with(it) { applySemantics() } }
    }

    override fun onFocusEvent(focusState: FocusState) {
        if (isElementFocused == focusState.isFocused) {
            return
        }
        isElementFocused = focusState.isFocused
        onFocusChange()

        val editable = enabled && !readOnly
        if (focusState.isFocused) {
            // Deselect when losing focus even if readonly.
            if (editable) {
                startInputSession(fromTap = false)
            }
        } else {
            disposeInputSession()
            // only clear the composing region when element loses focus. Window focus lost should
            // not clear the composing region.
            textFieldState.editUntransformedTextAsUser { commitComposition() }
            textFieldState.collapseSelectionToMax()
        }
    }

    /**
     * Should be called when either [isElementFocused] or [WindowInfo.isWindowFocused] change since
     * they are used in evaluation of [isFocused].
     */
    private fun onFocusChange() {
        textFieldSelectionState.isFocused = this.isFocused
        if (isFocused && observeChangesJob == null) {
            // only start a new job is there's not an ongoing one.
            observeChangesJob = coroutineScope.launch { textFieldSelectionState.observeChanges() }
        } else if (!isFocused) {
            observeChangesJob?.cancel()
            observeChangesJob = null
        }
    }

    override fun onAttach() {
        onObservedReadsChanged()
        textFieldSelectionState.receiveContentConfiguration = receiveContentConfigurationProvider
    }

    override fun onDetach() {
        disposeInputSession()
        textFieldSelectionState.receiveContentConfiguration = null
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        textLayoutState.decoratorNodeCoordinates = coordinates
    }

    override fun onPointerEvent(
        pointerEvent: PointerEvent,
        pass: PointerEventPass,
        bounds: IntSize
    ) {
        pointerInputNode.onPointerEvent(pointerEvent, pass, bounds)
    }

    override fun onCancelPointerInput() {
        pointerInputNode.onCancelPointerInput()
    }

    override fun onPreKeyEvent(event: KeyEvent): Boolean {
        return textFieldKeyEventHandler.onPreKeyEvent(
            event = event,
            textFieldState = textFieldState,
            textFieldSelectionState = textFieldSelectionState,
            focusManager = currentValueOf(LocalFocusManager),
            keyboardController = requireKeyboardController()
        )
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        return textFieldKeyEventHandler.onKeyEvent(
            event = event,
            textFieldState = textFieldState,
            textLayoutState = textLayoutState,
            textFieldSelectionState = textFieldSelectionState,
            clipboardKeyCommandsHandler = clipboardKeyCommandsHandler,
            editable = enabled && !readOnly,
            singleLine = singleLine,
            onSubmit = { onImeActionPerformed(keyboardOptions.imeActionOrDefault) },
        )
    }

    override fun onObservedReadsChanged() {
        observeReads {
            windowInfo = currentValueOf(LocalWindowInfo)
            onFocusChange()
        }
    }

    override fun onPlaced(coordinates: LayoutCoordinates) {
        // If the node implements the same interface, it must manually forward calls to
        //  all its delegatable nodes.
        dragAndDropNode.onPlaced(coordinates)
    }

    override fun onRemeasured(size: IntSize) {
        // If the node implements the same interface, it must manually forward calls to
        //  all its delegatable nodes.
        dragAndDropNode.onRemeasured(size)
    }

    private fun startInputSession(fromTap: Boolean) {
        if (!fromTap && !keyboardOptions.showKeyboardOnFocusOrDefault) return

        val receiveContentConfiguration = getReceiveContentConfiguration()

        inputSessionJob =
            coroutineScope.launch {
                // This will automatically cancel the previous session, if any, so we don't need to
                // cancel the inputSessionJob ourselves.
                establishTextInputSession {
                    platformSpecificTextInputSession(
                        state = textFieldState,
                        layoutState = textLayoutState,
                        imeOptions = keyboardOptions.toImeOptions(singleLine),
                        receiveContentConfiguration = receiveContentConfiguration,
                        onImeAction = ::onImeActionPerformed,
                        updateSelectionState = {
                            textFieldSelectionState.updateTextToolbarState(
                                TextToolbarState.Selection
                            )
                        },
                        stylusHandwritingTrigger = stylusHandwritingTrigger,
                        viewConfiguration = currentValueOf(LocalViewConfiguration)
                    )
                }
            }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun disposeInputSession() {
        inputSessionJob?.cancel()
        inputSessionJob = null
        stylusHandwritingTrigger?.resetReplayCache()
    }

    private fun requireKeyboardController(): SoftwareKeyboardController =
        currentValueOf(LocalSoftwareKeyboardController) ?: error("No software keyboard controller")

    private fun emitDragExitEvent() {
        dragEnterEvent?.let {
            interactionSource.tryEmit(HoverInteraction.Exit(it))
            dragEnterEvent = null
        }
    }

    private fun onImeActionPerformed(imeAction: ImeAction) {
        if (
            imeAction == ImeAction.None ||
            imeAction == ImeAction.Default ||
            keyboardActionHandler == null
        ) {
            // this should never happen but better be safe
            keyboardActionScope.defaultKeyboardAction(imeAction)
            return
        }

        keyboardActionHandler?.onKeyboardAction(
            performDefaultAction = { keyboardActionScope.defaultKeyboardAction(imeAction) }
        )
    }
}

internal expect suspend fun PlatformTextInputSession.platformSpecificTextInputSession(
    state: TransformedTextFieldState3,
    layoutState: TextLayoutState3,
    imeOptions: ImeOptions,
    receiveContentConfiguration: ReceiveContentConfiguration?,
    onImeAction: ((ImeAction) -> Unit)?,
    updateSelectionState: (() -> Unit)? = null,
    stylusHandwritingTrigger: MutableSharedFlow<Unit>? = null,
    viewConfiguration: ViewConfiguration? = null
): Nothing