package net.akehurst.kotlin.compose.editor.api.simple

import androidx.compose.ui.text.SpanStyle
import net.akehurst.kotlin.compose.editor.api.AutocompleteItem
import net.akehurst.kotlin.compose.editor.api.AutocompleteItemContent
import net.akehurst.kotlin.compose.editor.api.EditorSegmentStyle


data class EditorSegmentStyleSimple(
    override val start: Int,
    override val finish: Int,
    override val style: SpanStyle
) : EditorSegmentStyle

data class AutocompleteItemSimple(
    override val text: String,
    override val offset: Int,
    override val label: String?
) : AutocompleteItemContent {
    override fun equalTo(other: AutocompleteItem): Boolean = this == other
}