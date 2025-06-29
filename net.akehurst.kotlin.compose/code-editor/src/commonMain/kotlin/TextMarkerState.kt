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

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.StringAnnotation
import net.akehurst.kotlin.compose.editor.api.TextDecorationStyle

data class TextMarkerDefault(
    val position:Int,
    val length:Int,
    val style:SpanStyle,
    val decoration:TextDecorationStyle
)

class TextMarkerState {

    val markers = mutableStateListOf<TextMarkerDefault>()

    fun clear() {
        markers.clear()
    }

    fun addMarker(position: Int, length: Int, style: SpanStyle, decoration: TextDecorationStyle) {
        markers.add(TextMarkerDefault(position, length, style, decoration))
    }

}
