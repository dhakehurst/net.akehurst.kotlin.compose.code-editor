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
 */

@file:Suppress("UNUSED", "INVISIBLE_MEMBER", "INVISIBLE_REFERENCE", "EQUALS_MISSING", "CANNOT_OVERRIDE_INVISIBLE_MEMBER")

package net.akehurst.kotlin.compose.editor.text

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.*
import androidx.compose.foundation.text.handwriting.stylusHandwriting
import androidx.compose.foundation.text.input.*
import androidx.compose.foundation.text.input.TextFieldLineLimits.MultiLine
import androidx.compose.foundation.text.input.TextFieldLineLimits.SingleLine
import androidx.compose.foundation.text.input.internal.*
import androidx.compose.foundation.text.input.internal.selection.TextFieldSelectionState
import androidx.compose.foundation.text.input.internal.selection.TextToolbarState
import androidx.compose.foundation.text.selection.SelectionHandle
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

// This takes a composable lambda, but it is not primarily a container.
@OptIn(ExperimentalFoundationApi::class)
@Suppress("ComposableLambdaParameterPosition")
@Composable
internal fun BasicTextField3(
    state: TextFieldState3,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    inputTransformation: InputTransformation? = null,
    textStyle: TextStyle = TextStyle.Default,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    onKeyboardAction: KeyboardActionHandler? = null,
    lineLimits: TextFieldLineLimits = TextFieldLineLimits.Default,
    onTextLayout: (Density.(getResult: () -> TextLayoutResult?) -> Unit)? = null,
    interactionSource: MutableInteractionSource? = null,
    cursorBrush: Brush = BasicTextFieldDefaults.CursorBrush,
    codepointTransformation: CodepointTransformation? = null,
    outputTransformation: OutputTransformation? = null,
    decorator: TextFieldDecorator? = null,
    scrollState: ScrollState = rememberScrollState(),
    isPassword: Boolean = false,
    // Last parameter must not be a function unless it's intended to be commonly used as a trailing
    // lambda.
) {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val windowInfo = LocalWindowInfo.current
    val singleLine = lineLimits == SingleLine
    // We're using this to communicate focus state to cursor for now.
    @Suppress("NAME_SHADOWING")
    val interactionSource = interactionSource ?: remember { MutableInteractionSource() }
    val orientation = if (singleLine) Orientation.Horizontal else Orientation.Vertical
    val isFocused = interactionSource.collectIsFocusedAsState().value
    val isDragHovered = interactionSource.collectIsHoveredAsState().value
    val isWindowFocused = windowInfo.isWindowFocused
    val stylusHandwritingTrigger = remember {
        MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_LATEST)
    }

    val transformedState =
        remember(state, codepointTransformation, outputTransformation) {
            // First prefer provided codepointTransformation if not null, e.g. BasicSecureTextField
            // would send PasswordTransformation. Second, apply a SingleLineCodepointTransformation
            // if
            // text field is configured to be single line. Else, don't apply any visual
            // transformation.
            val appliedCodepointTransformation =
                codepointTransformation ?: SingleLineCodepointTransformation.takeIf { singleLine }
            TransformedTextFieldState3(
                textFieldState = state,
                inputTransformation = inputTransformation,
                codepointTransformation = appliedCodepointTransformation,
                outputTransformation = outputTransformation
            )
        }

    // Invalidate textLayoutState if TextFieldState itself has changed, since TextLayoutState3
    // would be carrying an invalid TextFieldState in its nonMeasureInputs.
    val textLayoutState = remember(transformedState) { TextLayoutState3() }

    // InputTransformation.keyboardOptions might be backed by Snapshot state.
    // Read in a restartable composable scope to make sure the resolved value is always up-to-date.
    val resolvedKeyboardOptions =
        keyboardOptions.fillUnspecifiedValuesWith(inputTransformation?.keyboardOptions)

    val textFieldSelectionState =
        remember(transformedState) {
            TextFieldSelectionState3(
                textFieldState = transformedState,
                textLayoutState = textLayoutState,
                density = density,
                enabled = enabled,
                readOnly = readOnly,
                isFocused = isFocused && isWindowFocused,
                isPassword = isPassword,
            )
        }
    val coroutineScope = rememberCoroutineScope()
    val currentHapticFeedback = LocalHapticFeedback.current
    val currentClipboard = LocalClipboard.current
    val currentTextToolbar = LocalTextToolbar.current

    val textToolbarHandler =
        remember(coroutineScope, currentTextToolbar) {
            object : TextToolbarHandler {
                override suspend fun showTextToolbar(
                    selectionState: TextFieldSelectionState3,
                    rect: Rect
                ) =
                    with(selectionState) {
                        currentTextToolbar.showMenu(
                            rect = rect,
                            onCopyRequested =
                                menuItem(canCopy(), TextToolbarState.None) {
                                    coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
                                        copy()
                                    }
                                },
                            onPasteRequested =
                                menuItem(canPaste(), TextToolbarState.None) {
                                    coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
                                        paste()
                                    }
                                },
                            onCutRequested =
                                menuItem(canCut(), TextToolbarState.None) {
                                    coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
                                        cut()
                                    }
                                },
                            onSelectAllRequested =
                                menuItem(canSelectAll(), TextToolbarState.Selection) {
                                    selectAll()
                                },
                            onAutofillRequested =
                                menuItem(canAutofill(), TextToolbarState.None) { autofill() }
                        )
                    }

                override fun hideTextToolbar() {
                    if (currentTextToolbar.status == TextToolbarStatus.Shown) {
                        currentTextToolbar.hide()
                    }
                }
            }
        }

    // TODO: upstreaming https://youtrack.jetbrains.com/issue/CMP-7517/Upstream-rememberClipboardEventsHandler
    rememberClipboardEventsHandler(
        isEnabled = isFocused,
        onPaste = { textFieldSelectionState.pasteAsPlainText(it) },
        onCopy = { textFieldSelectionState.copyWithResult() },
        onCut = { textFieldSelectionState.cutWithResult() }
    )

    SideEffect {
        // These properties are not backed by snapshot state, so they can't be updated directly in
        // composition.
        transformedState.update(inputTransformation)

        textFieldSelectionState.update(
            hapticFeedBack = currentHapticFeedback,
            clipboard = currentClipboard,
            density = density,
            enabled = enabled,
            readOnly = readOnly,
            isPassword = isPassword,
            showTextToolbar = textToolbarHandler
        )
    }

    DisposableEffect(textFieldSelectionState) { onDispose { textFieldSelectionState.dispose() } }

    val overscrollEffect = rememberTextFieldOverscrollEffect()

    val handwritingEnabled =
        !isPassword &&
                keyboardOptions.keyboardType != KeyboardType.Password &&
                keyboardOptions.keyboardType != KeyboardType.NumberPassword
    val decorationModifiers =
        modifier
            .then(
                // semantics + some focus + input session + touch to focus
                TextFieldDecoratorModifier3(
                    textFieldState = transformedState,
                    textLayoutState = textLayoutState,
                    textFieldSelectionState = textFieldSelectionState,
                    filter = inputTransformation,
                    enabled = enabled,
                    readOnly = readOnly,
                    keyboardOptions = resolvedKeyboardOptions,
                    keyboardActionHandler = onKeyboardAction,
                    singleLine = singleLine,
                    interactionSource = interactionSource,
                    isPassword = isPassword,
                    stylusHandwritingTrigger = stylusHandwritingTrigger
                )
            )
            .stylusHandwriting(enabled, handwritingEnabled) {
                // If this is a password field, we can't trigger handwriting.
                // The expected behavior is 1) request focus 2) show software keyboard.
                // Note: TextField will show software keyboard automatically when it
                // gain focus. 3) show a toast message telling that handwriting is not
                // supported for password fields. TODO(b/335294152)
                if (handwritingEnabled) {
                    // Send the handwriting start signal to platform.
                    // The editor should send the signal when it is focused or is about
                    // to gain focus, Here are more details:
                    //   1) if the editor already has an active input session, the
                    //   platform handwriting service should already listen to this flow
                    //   and it'll start handwriting right away.
                    //
                    //   2) if the editor is not focused, but it'll be focused and
                    //   create a new input session, one handwriting signal will be
                    //   replayed when the platform collect this flow. And the platform
                    //   should trigger handwriting accordingly.
                    stylusHandwritingTrigger.tryEmit(Unit)
                }
            }
            .focusable(interactionSource = interactionSource, enabled = enabled)
            .scrollable(
                state = scrollState,
                orientation = orientation,
                // Disable scrolling when textField is disabled or another dragging gesture is
                // taking
                // place
                enabled =
                    enabled && textFieldSelectionState.directDragGestureInitiator == TextFieldSelectionState3.InputType.None,
                reverseDirection =
                    ScrollableDefaults.reverseDirection(
                        layoutDirection = layoutDirection,
                        orientation = orientation,
                        reverseScrolling = false
                    ),
                interactionSource = interactionSource,
                overscrollEffect = overscrollEffect
            )
            .pointerHoverIcon(textPointerIcon)

    Box(decorationModifiers, propagateMinConstraints = true) {
        ContextMenuArea3(textFieldSelectionState, enabled) {
            val nonNullDecorator = decorator ?: DefaultTextFieldDecorator
            nonNullDecorator.Decoration {
                val minLines: Int
                val maxLines: Int
                if (lineLimits is MultiLine) {
                    minLines = lineLimits.minHeightInLines
                    maxLines = lineLimits.maxHeightInLines
                } else {
                    minLines = 1
                    maxLines = 1
                }

                Box(
                    propagateMinConstraints = true,
                    modifier =
                        Modifier.heightIn(min = textLayoutState.minHeightForSingleLineField)
                            .heightInLines(
                                textStyle = textStyle,
                                minLines = minLines,
                                maxLines = maxLines
                            )
                            .textFieldMinSize(textStyle)
                            .clipToBounds()
                            .overscroll(overscrollEffect)
                            .then(
                                TextFieldCoreModifier3(
                                    isFocused = isFocused && isWindowFocused,
                                    isDragHovered = isDragHovered,
                                    textLayoutState = textLayoutState,
                                    textFieldState = transformedState,
                                    textFieldSelectionState = textFieldSelectionState,
                                    cursorBrush = cursorBrush,
                                    writeable = enabled && !readOnly,
                                    scrollState = scrollState,
                                    orientation = orientation
                                )
                            )
                ) {
                    Box(
                        modifier =
                            Modifier.bringIntoViewRequester(textLayoutState.bringIntoViewRequester)
                                .then(
                                    TextFieldTextLayoutModifier3(
                                        textLayoutState = textLayoutState,
                                        textFieldState = transformedState,
                                        textStyle = textStyle,
                                        singleLine = singleLine,
                                        onTextLayout = onTextLayout,
                                        keyboardOptions = resolvedKeyboardOptions,
                                    )
                                )
                    )

                    if (
                        enabled &&
                        isFocused &&
                        isWindowFocused &&
                        textFieldSelectionState.isInTouchMode
                    ) {
                        TextFieldSelectionHandles3(selectionState = textFieldSelectionState)
                        if (!readOnly) {
                            TextFieldCursorHandle3(selectionState = textFieldSelectionState)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ContextMenuArea3(
    selectionState: TextFieldSelectionState3,
    enabled: Boolean,
    content: @Composable () -> Unit
) {
    content()
}

@Composable
internal fun TextFieldCursorHandle3(selectionState: TextFieldSelectionState3) {
    // Does not recompose if only position of the handle changes.
    val cursorHandleState:TextFieldHandleState by remember(selectionState) {
        derivedStateOf { selectionState.getCursorHandleState(includePosition = false) }
    }
    if (cursorHandleState.visible) {
        CursorHandle(
            offsetProvider = {
                selectionState.getCursorHandleState(includePosition = true).position
            },
            modifier =
                Modifier.pointerInput(selectionState) {
                    with(selectionState) { cursorHandleGestures() }
                },
            minTouchTargetSize = MinTouchTargetSizeForHandles,
        )
    }
}

@Composable
internal fun TextFieldSelectionHandles3(selectionState: TextFieldSelectionState3) {
    // Does not recompose if only position of the handle changes.
    val startHandleState:TextFieldHandleState by remember {
        derivedStateOf {
            selectionState.getSelectionHandleState(isStartHandle = true, includePosition = false)
        }
    }
    if (startHandleState.visible) {
        SelectionHandle(
            offsetProvider = {
                selectionState
                    .getSelectionHandleState(isStartHandle = true, includePosition = true)
                    .position
            },
            isStartHandle = true,
            direction = startHandleState.direction,
            handlesCrossed = startHandleState.handlesCrossed,
            modifier =
                Modifier.pointerInput(selectionState) {
                    with(selectionState) { selectionHandleGestures(true) }
                },
            lineHeight = startHandleState.lineHeight,
            minTouchTargetSize = MinTouchTargetSizeForHandles,
        )
    }

    // Does not recompose if only position of the handle changes.
    val endHandleState:TextFieldHandleState by remember {
        derivedStateOf {
            selectionState.getSelectionHandleState(isStartHandle = false, includePosition = false)
        }
    }
    if (endHandleState.visible) {
        SelectionHandle(
            offsetProvider = {
                selectionState
                    .getSelectionHandleState(isStartHandle = false, includePosition = true)
                    .position
            },
            isStartHandle = false,
            direction = endHandleState.direction,
            handlesCrossed = endHandleState.handlesCrossed,
            modifier =
                Modifier.pointerInput(selectionState) {
                    with(selectionState) { selectionHandleGestures(false) }
                },
            lineHeight = endHandleState.lineHeight,
            minTouchTargetSize = MinTouchTargetSizeForHandles,
        )
    }
}

private val DefaultTextFieldDecorator = TextFieldDecorator { it() }

/**
 * Defines a minimum touch target area size for Selection and Cursor handles.
 *
 * Although BasicTextField is not part of Material spec, this accessibility feature is important
 * enough to be included at foundation layer, and also TextField cannot change selection handles
 * provided by BasicTextField to somehow achieve this accessibility requirement.
 *
 * This value is adopted from Android platform's TextView implementation.
 */
private val MinTouchTargetSizeForHandles = DpSize(40.dp, 40.dp)
