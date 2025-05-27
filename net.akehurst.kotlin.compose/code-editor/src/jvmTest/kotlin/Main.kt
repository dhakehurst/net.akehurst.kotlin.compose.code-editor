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

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.PlatformSpanStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextDecorationLineStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.singleWindowApplication
import net.akehurst.kotlin.compose.editor.ComposableCodeEditor3
import net.akehurst.kotlin.compose.editor.api.*
import kotlin.test.Test

data class AcItem(
    override val text: String
) : AutocompleteItemContent {
    override val label: String? get() = text
    override fun equalTo(other: AutocompleteItem): Boolean =when {
        other !is AcItem -> false
        this.text != other.text -> false
        else -> true
    }
}

class test_CodeEditor {
/*
    @Test
    fun main1() {

        var composeEditor = ComposableCodeEditor(
            initialText = """
                    \red{Hello} \blue{World}
                """.trimIndent(),
            getLineTokens = { lineNumber, lineStartPosition, lineText -> getLineTokens( lineText) },
            requestAutocompleteSuggestions = { position, text, result -> requestAutocompleteSuggestions(position, text, result) }

        )

        singleWindowApplication(
            title = "Code Editor Test",
        ) {
            Surface {
                composeEditor.content()
            }
        }
    }

    @OptIn(ExperimentalTextApi::class)
    @Test
    fun main2() {

        var composeEditor = ComposableCodeEditor2(
            initialText = """
                    \red{Hello} \blue{World}
                    info
                    error
                """.trimIndent(),
            getLineTokens = { lineNumber, lineStartPosition, lineText -> getLineTokens(lineText) },
            requestAutocompleteSuggestions = { request, result -> requestAutocompleteSuggestions(request, result) }

        )
        val info = Regex("info")
        val err = Regex("error")
        val wavyStyle = PlatformSpanStyle(textDecorationLineStyle = TextDecorationLineStyle.Wavy)
        composeEditor.onTextChange =  {
            composeEditor.clearMarginItems()
            composeEditor.clearTextMarkers()
            it.split("\n").forEachIndexed {  idx, ln ->
                when {
                    ln.contains("info") ->  composeEditor.addMarginItem(idx, "Info", "Some info", Icons.Outlined.Info, Color.Blue)
                    ln.contains("error") ->  composeEditor.addMarginItem(idx, "Error", "Some error", Icons.Outlined.Warning, Color.Red)
                }
            }

            info.findAll(it).forEach {
                composeEditor.addTextMarker(it.range.start,it.range.endInclusive-it.range.start+1, SpanStyle(color = Color.Blue, textDecoration = TextDecoration.Underline, platformStyle = wavyStyle))
            }
            err.findAll(it).forEach {
                composeEditor.addTextMarker(it.range.start,it.range.endInclusive-it.range.start+1, SpanStyle(color = Color.Red, textDecoration = TextDecoration.Underline,platformStyle = wavyStyle))
            }
        }

        singleWindowApplication(
            title = "Code Editor 2 Test",
        ) {
            Surface {
                composeEditor.content(autocompleteModifier = Modifier.width(500.dp))
            }
        }
    }
*/

    @OptIn(ExperimentalTextApi::class)
    @Test
    fun main3() {
        var composeEditor = ComposableCodeEditor3(
            initialText = """
                    \red{Hello} \blue{World}
                    info
                    error
                """.trimIndent(),
            requestAutocompleteSuggestions = { request, result -> requestAutocompleteSuggestions(request, result) }

        )
        val info = Regex("info")
        val err = Regex("error")
        val wavyStyle = PlatformSpanStyle(textDecorationLineStyle = TextDecorationLineStyle.Wavy)
        composeEditor.onTextChange =  {
            val lines = it.split("\n")
            composeEditor.lineStyles =  lines.mapIndexed {  idx, ln -> Pair(idx,getLineTokens(ln))  }.toMap()

            composeEditor.clearMarginItems()
            composeEditor.clearTextMarkers()
            lines.forEachIndexed {  idx, ln ->
                when {
                    ln.contains("info") ->  composeEditor.addMarginItem(idx, "Info", "Some info", Icons.Outlined.Info, Color.Blue)
                    ln.contains("error") ->  composeEditor.addMarginItem(idx, "Error", "Some error", Icons.Outlined.Warning, Color.Red)
                }
            }

            info.findAll(it).forEach {
                composeEditor.addTextMarker(it.range.start,it.range.endInclusive-it.range.start+1, SpanStyle(color = Color.Blue, textDecoration = TextDecoration.Underline, platformStyle = wavyStyle))
            }
            err.findAll(it).forEach {
                composeEditor.addTextMarker(it.range.start,it.range.endInclusive-it.range.start+1, SpanStyle(color = Color.Red, textDecoration = TextDecoration.Underline,platformStyle = wavyStyle))
            }

        }

        singleWindowApplication(
            title = "Code Editor 3 Test",
        ) {
            Surface {
                composeEditor.content(autocompleteModifier = Modifier.width(500.dp).heightIn(30.dp, 300.dp))
            }
        }
    }

/*
    @Test
    fun main3() {

        var composeEditor = ComposableCodeEditor3(
            initialText = """
                    \red{Hello} \blue{World}
                """.trimIndent(),
            getLineTokens = { lineNumber, lineStartPosition, lineText -> getLineTokens(lineNumber, lineStartPosition, lineText) },
            requestAutocompleteSuggestions = { position, text, result -> requestAutocompleteSuggestions(position, text, result) }

        )

        singleWindowApplication(
            title = "Code Editor Test",
        ) {
            Surface {
                composeEditor.content()
            }
        }
    }
*/
    private fun requestAutocompleteSuggestions(request: AutocompleteRequestData, result: AutocompleteSuggestion) {
        when {
            request.depthDelta == 0 -> result.provide(
                listOf(
                    AcItem("if"),
                    AcItem("else")
                )
            )
            else -> result.provide(
                listOf(
                    AcItem("if"),
                    AcItem("else"),
                    AcItem("while"),
                    AcItem("when"),
                    AcItem("fun"),
                    AcItem("val"),
                    AcItem("var"),
                    AutocompleteItemDivider,
                    AcItem("override"),
                    AcItem("data"),
                    AcItem("value"),
                    AcItem("enum"),
                    AcItem("class"),
                )
            )
        }
    }

    fun getLineTokens(lineText: String): List<EditorSegmentStyle> {
       val t1 = Regex("[\\\\]red[{](.*)[}]").findAll(lineText).map {
            it.range.first
            object : EditorSegmentStyle {
                override val start: Int get() = it.range.first
                override val finish: Int get() = it.range.last+1
                override val style: SpanStyle get() = SpanStyle(color = Color.Red)
            }
        }
        val t2 = Regex("[\\\\]blue[{](.*)[}]").findAll(lineText).map {
            it.range.first
            object : EditorSegmentStyle {
                override val start: Int get() = it.range.first
                override val finish: Int get() = it.range.last+1
                override val style: SpanStyle get() = SpanStyle(color = Color.Blue, textDecoration = TextDecoration.None)
            }
        }
        val t3 = Regex("else|if|[{]|[}]").findAll(lineText).map {
            it.range.first
            object : EditorSegmentStyle {
                override val start: Int get() = it.range.first
                override val finish: Int get() = it.range.last+1
                override val style: SpanStyle get() = SpanStyle(color = Color.Magenta)
            }
        }
        return (t1 + t2 + t3).toList()
    }

}
