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

import androidx.compose.foundation.text.input.internal.selection.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text.input.internal.TextLayoutState
import androidx.compose.foundation.text.input.internal.TransformedTextFieldState
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.OnGloballyPositionedModifier
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.SemanticsModifierNode
import androidx.compose.ui.semantics.SemanticsPropertyReceiver

/**
 * Initializes either an actual TextFieldMagnifierNode implementation or No-op node according to
 * whether magnifier is supported.
 */
// TODO https://youtrack.jetbrains.com/issue/COMPOSE-737/TextField2.-Implement-textFieldMagnifierNode
internal  fun textFieldMagnifierNode3(
    textFieldState: TransformedTextFieldState3,
    textFieldSelectionState: TextFieldSelectionState3,
    textLayoutState: TextLayoutState3,
    visible: Boolean
): TextFieldMagnifierNode3 {
    return object : TextFieldMagnifierNode3() {
        override fun update(
            textFieldState: TransformedTextFieldState3,
            textFieldSelectionState: TextFieldSelectionState3,
            textLayoutState: TextLayoutState3,
            visible: Boolean
        ) {}
    }
}

internal abstract class TextFieldMagnifierNode3 :
    DelegatingNode(), OnGloballyPositionedModifier, DrawModifierNode, SemanticsModifierNode {

    abstract fun update(
        textFieldState: TransformedTextFieldState3,
        textFieldSelectionState: TextFieldSelectionState3,
        textLayoutState: TextLayoutState3,
        visible: Boolean
    )

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {}

    override fun ContentDrawScope.draw() {}

    override fun SemanticsPropertyReceiver.applySemantics() {}
}