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

import androidx.compose.runtime.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.akehurst.kotlin.compose.editor.api.MarginItem

data class MarginItemDefault(
    override val lineNumber: Int,
    override val kind: String,
    override val text: String,
    override val icon: ImageVector,
    override val color: Color
) : MarginItem {
    val interactionSource = MutableInteractionSource()
    var isHovered by mutableStateOf(false)

    fun offsetFromTopOfViewport(viewTopLine:Int,textLayoutResult: TextLayoutResult): Dp {
        val fl = textLayoutResult.getLineTop(lineNumber - viewTopLine)
        return fl.dp / 2
    }

}

@Stable
class MarginItemsState {

    var items = mutableStateListOf<MarginItemDefault>()

    fun clear() {
        items.clear()
    }

    fun addAnnotation(lineNumber: Int, kind:String, text: String, icon: ImageVector, color: Color) {
        items.add(MarginItemDefault(lineNumber, kind, text, icon, color))
    }
}