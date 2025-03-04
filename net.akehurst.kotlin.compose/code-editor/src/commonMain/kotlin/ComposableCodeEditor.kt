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
import androidx.compose.ui.text.buildAnnotatedString
import net.akehurst.kotlin.compose.editor.api.AutocompleteFunction
import net.akehurst.kotlin.compose.editor.api.AutocompleteState
import net.akehurst.kotlin.compose.editor.api.ComposeCodeEditor
import net.akehurst.kotlin.compose.editor.api.LineTokensFunction
import net.akehurst.kotlin.compose.editor.api.MarginItem

class ComposableCodeEditor(
    initialText: String = "",
    override var onTextChange: (String) -> Unit = {},
    override var getLineTokens: LineTokensFunction = { _, _, _ -> emptyList() },
    override var requestAutocompleteSuggestions: AutocompleteFunction = { _, _, _ -> },
) : ComposeCodeEditor {

    val editorState = EditorState(
        initialText = initialText,
        onTextChange = { onTextChange.invoke(it) },
        getLineTokens = { lineNumber, lineStartPosition, lineText -> getLineTokens.invoke(lineNumber, lineStartPosition, lineText) },
        requestAutocompleteSuggestions = { position, text, result -> requestAutocompleteSuggestions.invoke(position, text, result) }
    )

    override var rawText: String
        get() = editorState.inputRawText
        set(value) {
            editorState.setNewText(value)
        }

    override var annotatedText
        get() = editorState.lastTextLayoutResult?.layoutInput?.text ?: buildAnnotatedString { }
        set(value) {

        }

    override val autocomplete: AutocompleteState get() = editorState.autocompleteState

    override val marginItems: List<MarginItem> get() = editorState.marginItemsState.items

    override fun focus() {
        editorState.focusRequester.requestFocus()
    }

    override fun refreshTokens() = editorState.refresh()

    override fun destroy() {
        // not sure what is needed here!
    }

    override fun clearMarginItems() {
        editorState.marginItemsState.clear()
    }

    override fun addMarginItem(lineNumber: Int, kind:String, text: String, icon: ImageVector, color: Color) {
        editorState.marginItemsState.addAnnotation(lineNumber, kind, text, icon,color)
    }

    override fun clearTextMarkers() {
        TODO("not implemented")
    }

    override fun addTextMarker(position: Int, length: Int, style: SpanStyle) {
        TODO("not implemented")
    }

    @Composable
    fun content(modifier: Modifier = Modifier.fillMaxSize()) {
        CodeEditor(
            modifier = modifier,
            editorState = editorState
        )
    }

}

class ComposableCodeEditor2(
    initialText: String = "",
    override var onTextChange: (String) -> Unit = {},
    override var getLineTokens: LineTokensFunction = { _, _, _ -> emptyList() },
    override var requestAutocompleteSuggestions: AutocompleteFunction = { _, _, _ -> },
) : ComposeCodeEditor {

    val editorState = EditorState2(
        initialText = initialText,
        onTextChange = { onTextChange.invoke(it.toString()) },
        getLineTokens = { lineNumber, lineStartPosition, lineText -> getLineTokens.invoke(lineNumber, lineStartPosition, lineText) },
        requestAutocompleteSuggestions = { position, text, result -> requestAutocompleteSuggestions.invoke(position, text, result) }
    )

    override var rawText: String
        get() = editorState.inputRawText.toString()
        set(value) {
            editorState.setNewText(value)
        }

    override var annotatedText
        get() = editorState.lastAnnotatedText ?: buildAnnotatedString { }
        set(value) {

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

    override fun addMarginItem(lineNumber: Int,kind:String, text: String, icon: ImageVector, color: Color) {
        editorState.marginItemsState.addAnnotation(lineNumber,kind, text, icon,color)
    }

    override fun clearTextMarkers() {
        editorState.textMarkersState.clear()
    }

    override fun addTextMarker(position: Int, length: Int, style: SpanStyle) {
        editorState.textMarkersState.addMarker(position, length, style)
    }

    @Composable
    fun content(modifier: Modifier = Modifier.fillMaxSize()) {
        CodeEditor2(
            modifier = modifier,
            editorState = editorState
        )
    }

}
/*
class ComposableCodeEditor3(
    initialText: String = "",
    override var onTextChange: (String) -> Unit = {},
    override var getLineTokens: LineTokensFunction = { _, _, _ -> emptyList() },
    override var requestAutocompleteSuggestions: AutocompleteFunction = { _, _, _ -> },
) : ComposeCodeEditor {

    val editorState = EditorState3(
        initialText = initialText,
        onTextChange = { onTextChange.invoke(it.toString()) },
        getLineTokens = { lineNumber, lineStartPosition, lineText -> getLineTokens.invoke(lineNumber, lineStartPosition, lineText) },
        requestAutocompleteSuggestions = { position, text, result -> requestAutocompleteSuggestions.invoke(position, text, result) }
    )

    override var text: String
        get() = editorState.inputRawText.toString()
        set(value) {
            editorState.setNewText(value)
        }

    override val autocomplete: AutocompleteState get() = editorState.autocompleteState

    override fun focus() {
        editorState.focusRequester.requestFocus()
    }

    override fun refreshTokens() = editorState.refresh()

    override fun destroy() {
        // not sure what is needed here!
    }

    @Composable
    fun content(modifier: Modifier = Modifier.fillMaxSize()) {
        CodeEditor3(
            modifier = modifier,
            editorState = editorState
        )
    }

}
*/