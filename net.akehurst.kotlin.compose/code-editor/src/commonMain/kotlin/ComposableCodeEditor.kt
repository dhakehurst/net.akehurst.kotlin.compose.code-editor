/**
 * Copyright (C) 2025 Dr. David H. Akehurst (http://dr.david.h.akehurst.net)
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

package net.akehurst.kotlin.compose.editor

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import net.akehurst.kotlin.compose.editor.api.AutocompleteFunction
import net.akehurst.kotlin.compose.editor.api.AutocompleteState
import net.akehurst.kotlin.compose.editor.api.ComposeCodeEditor
import net.akehurst.kotlin.compose.editor.api.EditorSegmentStyle
import net.akehurst.kotlin.compose.editor.api.LineTokensFunction
import net.akehurst.kotlin.compose.editor.api.MarginItem
import net.akehurst.kotlin.compose.editor.api.TextDecorationStyle

class ComposableCodeEditor(
    initialText: String = "",
    override var onTextChange: (String) -> Unit = {},
    override var requestAutocompleteSuggestions: AutocompleteFunction = { _, _ -> },
) : ComposeCodeEditor {

    override var getLineTokens: LineTokensFunction
        get() = error("Unsupported")
        set(value) {
            error("Unsupported")
        }

    val editorState = EditorState(
        initialText = initialText,
        onTextChange = { onTextChange.invoke(it.toString()) },
        requestAutocompleteSuggestions = { request, result -> requestAutocompleteSuggestions.invoke(request, result) }
    )

    override var rawText: String
        get() = editorState.inputRawText.toString()
        set(value) {
            editorState.setNewText(value)
        }

    override var annotatedText
        get() = editorState.lastAnnotatedText ?: buildAnnotatedString { }
        set(value) {
            editorState.lastAnnotatedText = value
            editorState.setNewText(value.text)
        }

    override var lineStyles: Map<Int, List<EditorSegmentStyle>>
        get() = editorState.lineStyles
        set(value) {
            editorState.setAllLineStyles(value)
        }

    override val autocomplete: AutocompleteState get() = editorState.autocompleteState

    override val marginItems: List<MarginItem> get() = editorState.marginItemsState.items

    override fun focus() {
        editorState.giveFocus = true
    }

    override fun refreshTokens() = editorState.refresh()

    override fun destroy() {
        // not sure what is needed here!
    }

    override fun clearMarginItems() {
        editorState.marginItemsState.clear()
    }

    override fun addMarginItem(lineNumber: Int, kind: String, text: String, icon: ImageVector, color: Color) {
        editorState.marginItemsState.addAnnotation(lineNumber, kind, text, icon, color)
    }

    override fun clearTextMarkers() {
        editorState.textMarkersState.clear()
    }

    override fun addTextMarker(position: Int, length: Int, style: SpanStyle, decoration: TextDecorationStyle) {
        editorState.textMarkersState.addMarker(position, length, style, decoration)
    }

    @Composable
    fun content(
        textStyle: TextStyle = TextStyle.Default,
        modifier: Modifier = Modifier.fillMaxSize(),
        autocompleteModifier: Modifier = Modifier,
        marginItemHoverModifier: Modifier = Modifier
    ) {
        CodeEditor(
            textStyle = textStyle,
            modifier = modifier,
            autocompleteModifier = autocompleteModifier,
            marginItemHoverModifier = marginItemHoverModifier,
            editorState = editorState
        )
    }

}
