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
import net.akehurst.kotlin.compose.editor.ComposableCodeEditor3
import kotlin.test.Test

class test_CodeEditor {

    @OptIn(ExperimentalTextApi::class)
    @Test
    fun main3() {
        var composeEditor = ComposableCodeEditor3(
            initialText = """
                    \red{Hello} \blue{World}
                    info
                    error
                """.trimIndent(),
            requestAutocompleteSuggestions = { position, text, result -> requestAutocompleteSuggestions(position, text, result) }

        )
        val info = Regex("info")
        val err = Regex("error")
        val wavyStyle = PlatformSpanStyle(textDecorationLineStyle = TextDecorationLineStyle.Wavy)
        composeEditor.onTextChange =  {
            val lines = it.split("\n")
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

            composeEditor.lineStyles =  lines.mapIndexed {  idx, ln -> Pair(idx,getLineTokens(ln))  }.toMap()
        }

        singleWindowApplication(
            title = "Code Editor 3 Test",
        ) {
            Surface {
                composeEditor.content(autocompleteModifier = Modifier.width(500.dp))
            }
        }
    }

}