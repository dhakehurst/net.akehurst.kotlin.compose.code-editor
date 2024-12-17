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

package test

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.window.singleWindowApplication
import net.akehurst.kotlin.compose.editor.CodeEditor
import net.akehurst.kotlin.compose.editor.CodeEditor2
import net.akehurst.kotlin.compose.editor.EditorState
import net.akehurst.kotlin.compose.editor.api.AutocompleteItem
import net.akehurst.kotlin.compose.editor.api.AutocompleteSuggestion
import net.akehurst.kotlin.compose.editor.api.EditorLineToken
import kotlin.test.Test

data class AcItem(
    override val text: String
) : AutocompleteItem {
    override val name: String? get() = text
    override fun equalTo(other: AutocompleteItem): Boolean =when {
        other !is AcItem -> false
        this.text != other.text -> false
        else -> true
    }
}

class test_CodeEditor {

    @Test
    fun main() {
        singleWindowApplication(
            title = "Code Editor Test",
        ) {
            content()
        }
    }

    @Composable
    fun content() {
        val editorState = remember {
            EditorState(
                initialText = """
                    \red{Hello} \blue{World}
                """.trimIndent(),
                //onTextChange = { onTextChange.invoke(it) },
                getLineTokens = { lineNumber, lineStartPosition, lineText -> getLineTokens(lineNumber, lineStartPosition, lineText) },
                requestAutocompleteSuggestions = { position, text, result -> requestAutocompleteSuggestions(position, text, result) }
            )
        }

        Surface {
            CodeEditor(
                modifier = Modifier
                    .fillMaxSize(),
                editorState = editorState
            )
        }
    }

    private fun requestAutocompleteSuggestions(position: Int, text: CharSequence, result: AutocompleteSuggestion) {
        result.provide(listOf(
            AcItem("if"),
            AcItem("else"),
        ))
    }

    fun getLineTokens(lineNumber: Int, lineStartPosition: Int, lineText: String): List<EditorLineToken> {
       val t1 = Regex("[\\\\]red[{](.*)[}]\"").findAll(lineText).map {
            it.range.first
            object : EditorLineToken {
                override val start: Int get() = it.range.first
                override val finish: Int get() = it.range.last+1
                override val style: SpanStyle get() = SpanStyle(color = Color.Red)
            }
        }
        val t2 = Regex("[\\\\]blue[{](.*)[}]").findAll(lineText).map {
            it.range.first
            object : EditorLineToken {
                override val start: Int get() = it.range.first
                override val finish: Int get() = it.range.last+1
                override val style: SpanStyle get() = SpanStyle(color = Color.Blue)
            }
        }
        val t3 = Regex("else|if|[{]|[}]}").findAll(lineText).map {
            it.range.first
            object : EditorLineToken {
                override val start: Int get() = it.range.first
                override val finish: Int get() = it.range.last+1
                override val style: SpanStyle get() = SpanStyle(color = Color.Magenta)
            }
        }
        return (t1 + t2 + t3).toList()
    }

}
