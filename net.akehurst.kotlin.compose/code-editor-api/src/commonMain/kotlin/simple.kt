package net.akehurst.kotlin.compose.editor.api.simple

import androidx.compose.ui.text.SpanStyle
import net.akehurst.kotlin.compose.editor.api.AutocompleteItem
import net.akehurst.kotlin.compose.editor.api.EditorLineToken


data class EditorLineTokenSimple(
    override val start: Int,
    override val finish: Int,
    override val style: SpanStyle
) : EditorLineToken

data class AutocompleteItemSimple(
    override val text: String,
    override val label: String?
) : AutocompleteItem {
    override fun equalTo(other: AutocompleteItem): Boolean = this == other
}