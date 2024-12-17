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

package net.akehurst.kotlin.compose.editor.api

import androidx.compose.ui.text.SpanStyle

typealias LineTokensFunction = ((lineNumber: Int, lineStartPosition: Int, lineText: String) -> List<EditorLineToken>)
typealias AutocompleteFunction = suspend (position: Int, text: CharSequence, result: AutocompleteSuggestion) -> Unit

interface ComposeCodeEditor {
    var text: String
    var onTextChange: (String) -> Unit
    var getLineTokens: LineTokensFunction
    var requestAutocompleteSuggestions: AutocompleteFunction

    fun destroy()
}

interface EditorLineToken {
    /**
     * style for this segment of text
     */
    val style: SpanStyle

    /**
     * starting offset in the line for this segment. Lines start at offset 0
     */
    val start: Int

    /**
     * finish offset (exclusive) in the line for this segment. Lines start at offset 0
     */
    val finish: Int
}

interface AutocompleteItem {

    /**
     * text to insert if selected
     */
    val text: String

    /**
     * when not null will add ($name) after the text
     */
    val name: String?

    fun equalTo(other: AutocompleteItem): Boolean
}

interface AutocompleteSuggestion {
    fun provide(items: List<AutocompleteItem>)
}
