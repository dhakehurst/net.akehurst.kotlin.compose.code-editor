@file:Suppress("UNUSED", "INVISIBLE_MEMBER", "INVISIBLE_REFERENCE", "EQUALS_MISSING", "CANNOT_OVERRIDE_INVISIBLE_MEMBER")

package net.akehurst.kotlin.compose.editor.text

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.style.ResolvedTextDirection

/** Defines how to render a selection or cursor handle on a TextField. */
internal data class TextFieldHandleState(
    val visible: Boolean,
    val position: Offset,
    val lineHeight: Float,
    val direction: ResolvedTextDirection,
    val handlesCrossed: Boolean
) {
    companion object {
        val Hidden =
            TextFieldHandleState(
                visible = false,
                position = Offset.Unspecified,
                lineHeight = 0f,
                direction = ResolvedTextDirection.Ltr,
                handlesCrossed = false
            )
    }
}
