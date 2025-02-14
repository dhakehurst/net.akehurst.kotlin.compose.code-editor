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
import net.akehurst.kotlin.compose.editor.api.AutocompleteFunction
import net.akehurst.kotlin.compose.editor.api.AutocompleteState
import net.akehurst.kotlin.compose.editor.api.ComposeCodeEditor
import net.akehurst.kotlin.compose.editor.api.LineTokensFunction


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

    override var text: String
        get() = editorState.inputRawText
        set(value) {
            editorState.setNewText(value)
        }

    override val autocomplete: AutocompleteState get() = editorState.autocompleteState

    override fun refreshTokens() = editorState.refresh()

    override fun destroy() {
        // not sure what is needed here!
    }

    @Composable
    fun content() {
        CodeEditor(
            modifier = Modifier
                .fillMaxSize(),
            editorState = editorState
        )
    }

}