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

package net.akehurst.kotlin.compose.editor

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.CanvasBasedWindow
import org.jetbrains.skiko.wasm.onWasmReady
import org.w3c.dom.Element
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.HTMLScriptElement

@OptIn(ExperimentalComposeUiApi::class)
class ComposeCodeEditorJs(
    editorElement: Element,
    initialText: String = "",
    var onTextChange: (String) -> Unit = {},
    var getLineTokens: LineTokensFunction = { _, _, _ -> emptyList() },
    var requestAutocompleteSuggestions: AutocompleteFunction = { _, _, _ -> },
) {
    val script: HTMLScriptElement
    val canvas: HTMLCanvasElement
    private val editorState = EditorState(
        initialText = initialText,
        getLineTokens = getLineTokens,
        requestAutocompleteSuggestions = requestAutocompleteSuggestions
    )

    init {
        script = editorElement.ownerDocument!!.createElement("script") as HTMLScriptElement
        script.setAttribute("src", "skiko.js")
        editorElement.ownerDocument!!.body!!.appendChild(script)
        val elementId = editorElement.id
        canvas = editorElement.ownerDocument!!.createElement("canvas") as HTMLCanvasElement
        canvas.id = "canvas_$elementId"
        editorElement.appendChild(canvas)
        onWasmReady {
            CanvasBasedWindow(
                canvasElementId = canvas.id
            ) {
                CodeEditor(
                    modifier = Modifier.fillMaxSize(),
                    onTextChange = onTextChange,
                    editorState = editorState
                )
            }
        }
    }

    fun destroy() {
        canvas.remove()
        script.remove()
    }
}