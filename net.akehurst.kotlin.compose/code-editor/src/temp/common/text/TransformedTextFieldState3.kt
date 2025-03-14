/*
 * Copyright 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

@file:Suppress("UNUSED", "INVISIBLE_MEMBER", "INVISIBLE_REFERENCE", "EQUALS_MISSING", "CANNOT_OVERRIDE_INVISIBLE_MEMBER")

package net.akehurst.kotlin.compose.editor.text

import androidx.compose.foundation.text.input.internal.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.TextFieldCharSequence
import androidx.compose.foundation.text.input.TextHighlightType
import androidx.compose.foundation.text.input.delete
import androidx.compose.foundation.text.input.internal.IndexTransformationType.Deletion
import androidx.compose.foundation.text.input.internal.IndexTransformationType.Insertion
import androidx.compose.foundation.text.input.internal.IndexTransformationType.Replacement
import androidx.compose.foundation.text.input.internal.IndexTransformationType.Untransformed
import androidx.compose.foundation.text.input.internal.undo.TextFieldEditUndoBehavior
import androidx.compose.foundation.text.input.setSelectionCoerced
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * A mutable view of a [TextFieldState3] where the text and selection values are transformed by an
 * [OutputTransformation] and a [CodepointTransformation].
 *
 * [outputText] and [visualText] return the transformed text (see the explanation of phases below),
 * with selection and composition mapped to the corresponding offsets from the untransformed text.
 * The transformed text is cached in [derived states][derivedStateOf] and only recalculated when the
 * [TextFieldState3] changes or some state read by the transformation functions changes.
 *
 * This class defines methods for various operations that can be performed on the underlying
 * [TextFieldState3]. When possible, these methods should be used instead of editing the state
 * directly, since this class ensures the correct offset mappings are used. If an operation is too
 * complex to warrant a method here, use [editUntransformedTextAsUser] but be careful to make sure
 * any offsets are mapped correctly.
 *
 * To map offsets from transformed to untransformed text or back, use the [mapFromTransformed] and
 * [mapToTransformed] methods.
 *
 * All operations call [TextFieldState3.editAsUser] internally and pass [inputTransformation].
 *
 * ## Text transformation phases
 *
 * Text is transformed in two phases:
 * 1. The first phase applies [outputTransformation], and the resulting text, [outputText], is
 *    published to the semantics system (consumed by a11y services and tests).
 * 2. The second phase applies [codepointTransformation] and the resulting text, [visualText], is
 *    laid out and drawn to the screen.
 *
 * Any transformations that change the semantics (in the generic sense, not Compose semantics)
 * should be done in the first phase. Examples include adding prefixes or suffixes to the text or
 * inserting formatting characters.
 *
 * The second phase should only be used for purely visual or layout transformations. Examples
 * include password masking or inserting spaces for Scribe.
 *
 * In most cases, one or both phases will be noops. E.g., password fields will usually only use the
 * second phase, and non-password fields will usually only use the first phase.
 *
 * Here's a diagram explaining the phases:
 * ```
 *  ┌──────────────────┐
 *  │                  │
 *  │  TextFieldState3  │        "Sam"      - Semantics setSelection: relativeToOriginalText=true
 *  │                  │
 *  └──────────────────┘
 *            │
 *  OutputTransformation    s/^/Hello, /
 *            │
 *            ▼
 *  ┌──────────────────┐
 *  │                  │                   - Talkback
 *  │   Output text    │    "Hello, Sam"   - Tests
 *  │                  │                   - Semantics setSelection: relativeToOriginalText=false
 *  └──────────────────┘
 *            │
 * CodepointTransformation     s/./•/g
 *            │
 *            ▼
 *  ┌──────────────────┐
 *  │                  │                   - Measured
 *  │   Visual text    │    "••••••••••"   - Wrapping, ellipsis, etc.
 *  │                  │                   - Drawn on screen
 *  └──────────────────┘
 * ```
 */
@OptIn(ExperimentalFoundationApi::class)
@Stable
internal class TransformedTextFieldState3(
    private val textFieldState: TextFieldState3,
    private var inputTransformation: InputTransformation? = null,
    private val codepointTransformation: CodepointTransformation? = null,
    private val outputTransformation: OutputTransformation? = null,
) {
    private val outputTransformedText: State<TransformedText?>? =
        // Don't allocate a derived state object if we don't need it, they're expensive.
        outputTransformation?.let { transformation ->
            derivedStateOf {
                // text is a state read. transformation may also perform state reads when ran.
                calculateTransformedText(
                    untransformedValue = textFieldState.value,
                    outputTransformation = transformation,
                    wedgeAffinity = selectionWedgeAffinity
                )
            }
        }

    private val codepointTransformedText: State<TransformedText?>? =
        codepointTransformation?.let { transformation ->
            derivedStateOf {
                calculateTransformedText(
                    // These are state reads. codepointTransformation may also perform state reads
                    // when ran.
                    untransformedValue = outputTransformedText?.value?.text ?: textFieldState.value,
                    codepointTransformation = transformation,
                    wedgeAffinity = selectionWedgeAffinity
                )
            }
        }

    /**
     * The raw text in the underlying [TextFieldState3]. This text does not have any
     * [CodepointTransformation] applied.
     */
    val untransformedText: TextFieldCharSequence
        get() = textFieldState.value

    /**
     * The text that should be presented to the user in most cases. If an [OutputTransformation] is
     * specified, this text has the transformation applied. If there's no transformation, this will
     * be the same as [untransformedText].
     *
     * See the diagram on [TransformedTextFieldState3] for a graphical representation of how this
     * value relates to [untransformedText] and [visualText].
     */
    val outputText: TextFieldCharSequence
        get() = outputTransformedText?.value?.text ?: untransformedText

    /**
     * The text that should be laid out and drawn to the screen. If a [CodepointTransformation] is
     * specified, this text has the transformation applied. If there's no transformation, this will
     * be the same as [outputText].
     *
     * See the diagram on [TransformedTextFieldState3] for a graphical representation of how this
     * value relates to [untransformedText] and [outputText].
     */
    val visualText: TextFieldCharSequence
        get() = codepointTransformedText?.value?.text ?: outputText

    /**
     * Indicates which side of a wedge (text inserted by the [OutputTransformation]) the start and
     * end of the selection should map to. This allows the user to move the cursor to both sides of
     * the wedge even though both those indices map to the same index in the untransformed text.
     */
    var selectionWedgeAffinity by mutableStateOf(SelectionWedgeAffinity(WedgeAffinity.Start))

    /**
     * [TransformedTextFieldState3] is not recreated when only [InputTransformation] changes. This
     * method simply updates the internal [InputTransformation] to be used by input methods like the
     * IME, hardware keyboard, or gestures.
     *
     * [InputTransformation] property is not backed by snapshot state, so it can't be updated
     * directly in composition. Make sure to call this method from outside the composition.
     */
    fun update(inputTransformation: InputTransformation?) {
        this.inputTransformation = inputTransformation
    }

    fun placeCursorBeforeCharAt(transformedOffset: Int) {
        selectCharsIn(TextRange(transformedOffset))
    }

    fun selectCharsIn(transformedRange: TextRange) {
        val untransformedRange = mapFromTransformed(transformedRange)
        selectUntransformedCharsIn(untransformedRange)
    }

    fun selectUntransformedCharsIn(untransformedRange: TextRange) {
        textFieldState.editAsUser(inputTransformation) {
            setSelectionCoerced(untransformedRange.start, untransformedRange.end)
        }
    }

    fun highlightCharsIn(type: TextHighlightType, transformedRange: TextRange) {
        val untransformedRange = mapFromTransformed(transformedRange)
        textFieldState.editAsUser(inputTransformation) {
            setHighlight(type, untransformedRange.start, untransformedRange.end)
        }
    }

    /** Replaces the entire content of the [textFieldState] with [newText]. */
    fun replaceAll(newText: CharSequence) {
        textFieldState.editAsUser(inputTransformation) {
            delete(0, length)
            append(newText.toString())
            updateWedgeAffinity()
        }
    }

    fun selectAll() {
        textFieldState.editAsUser(inputTransformation) { setSelectionCoerced(0, length) }
    }

    fun deleteSelectedText() {
        textFieldState.editAsUser(
            inputTransformation,
            undoBehavior = TextFieldEditUndoBehavior.NeverMerge
        ) {
            // `selection` is read from the buffer, so we don't need to transform it.
            delete(selection.min, selection.max)
            setSelectionCoerced(selection.min)
            updateWedgeAffinity()
        }
    }

    /**
     * Replaces the text in given [range] with [newText]. Like all other methods in this class,
     * [range] is considered to be in transformed space.
     */
    fun replaceText(
        newText: CharSequence,
        range: TextRange,
        undoBehavior: TextFieldEditUndoBehavior = TextFieldEditUndoBehavior.MergeIfPossible,
        restartImeIfContentChanges: Boolean = true
    ) {
        textFieldState.editAsUser(
            inputTransformation = inputTransformation,
            undoBehavior = undoBehavior,
            restartImeIfContentChanges = restartImeIfContentChanges
        ) {
            val selection = mapFromTransformed(range)
            replace(selection.min, selection.max, newText)
            val cursor = selection.min + newText.length
            setSelectionCoerced(cursor)
            updateWedgeAffinity()
        }
    }

    fun replaceSelectedText(
        newText: CharSequence,
        clearComposition: Boolean = false,
        undoBehavior: TextFieldEditUndoBehavior = TextFieldEditUndoBehavior.MergeIfPossible,
        restartImeIfContentChanges: Boolean = true
    ) {
        textFieldState.editAsUser(
            inputTransformation = inputTransformation,
            restartImeIfContentChanges = restartImeIfContentChanges,
            undoBehavior = undoBehavior
        ) {
            if (clearComposition) {
                commitComposition()
            }

            // `selection` is read from the buffer, so we don't need to transform it.
            val selection = selection
            replace(selection.min, selection.max, newText)
            val cursor = selection.min + newText.length
            setSelectionCoerced(cursor)
            updateWedgeAffinity()
        }
    }

    fun collapseSelectionToMax() {
        textFieldState.editAsUser(inputTransformation) {
            // `selection` is read from the buffer, so we don't need to transform it.
            setSelectionCoerced(selection.max)
        }
    }

    fun collapseSelectionToEnd() {
        textFieldState.editAsUser(inputTransformation) {
            // `selection` is read from the buffer, so we don't need to transform it.
            setSelectionCoerced(selection.end)
        }
    }

    fun undo() {
        textFieldState.undoState.undo()
    }

    fun redo() {
        textFieldState.undoState.redo()
    }

    /**
     * Runs [block] with a buffer that contains the source untransformed text. This is the text that
     * will be fed into the [outputTransformation]. Any operations performed on this buffer MUST
     * take care to explicitly convert between transformed and untransformed offsets and ranges.
     * When possible, use the other methods on this class to manipulate selection to avoid having to
     * do these conversions manually. Additionally any edit that ends up collapsing the selection
     * resets the [selectionWedgeAffinity] back to [WedgeAffinity.Start].
     *
     * @see mapToTransformed
     * @see mapFromTransformed
     */
    inline fun editUntransformedTextAsUser(
        restartImeIfContentChanges: Boolean = true,
        block: TextFieldBuffer.() -> Unit
    ) {
        textFieldState.editAsUser(
            inputTransformation = inputTransformation,
            restartImeIfContentChanges = restartImeIfContentChanges
        ) {
            block()
            updateWedgeAffinity()
        }
    }

    /**
     * If the text content changes after text is edited and the selection is collapsed into a
     * cursor, wedge affinity needs to be updated.
     */
    private fun TextFieldBuffer.updateWedgeAffinity() {
        if (changeTracker.changeCount > 0 && this@updateWedgeAffinity.selection.collapsed) {
            selectionWedgeAffinity = SelectionWedgeAffinity(WedgeAffinity.Start)
        }
    }

    /**
     * Maps an [offset] in the untransformed text to the corresponding offset or range in
     * [visualText].
     *
     * An untransformed offset will map to non-collapsed range if the offset is in the middle of a
     * surrogate pair in the untransformed text, in which case it will return the range of the
     * codepoint that the surrogate maps to. Offsets on either side of a surrogate pair will return
     * collapsed ranges.
     *
     * If there is no transformation, or the transformation does not change the text, a collapsed
     * range of [offset] will be returned.
     *
     * @see mapFromTransformed
     */
    fun mapToTransformed(offset: Int): TextRange {
        val presentMapping = outputTransformedText?.value?.offsetMapping
        val visualMapping = codepointTransformedText?.value?.offsetMapping

        val intermediateRange = presentMapping?.mapFromSource(offset) ?: TextRange(offset)
        return visualMapping?.let {
            mapToTransformed(intermediateRange, it, selectionWedgeAffinity)
        } ?: intermediateRange
    }

    /**
     * Maps a [range] in the untransformed text to the corresponding range in [visualText].
     *
     * If there is no transformation, or the transformation does not change the text, [range] will
     * be returned.
     *
     * @see mapFromTransformed
     */
    fun mapToTransformed(range: TextRange): TextRange {
        val presentMapping = outputTransformedText?.value?.offsetMapping
        val visualMapping = codepointTransformedText?.value?.offsetMapping

        // Only apply the wedge affinity to the final range. If the first mapping returns a range,
        // the first range should have both edges expanded by the second.
        val intermediateRange = presentMapping?.let { mapToTransformed(range, it) } ?: range
        return visualMapping?.let {
            mapToTransformed(intermediateRange, it, selectionWedgeAffinity)
        } ?: intermediateRange
    }

    /**
     * Maps an [offset] in [visualText] to the corresponding offset in the untransformed text.
     *
     * Multiple transformed offsets may map to the same untransformed offset. In particular, any
     * offset in the middle of a surrogate pair will map to offset of the corresponding codepoint in
     * the untransformed text.
     *
     * If there is no transformation, or the transformation does not change the text, [offset] will
     * be returned.
     *
     * @see mapToTransformed
     */
    fun mapFromTransformed(offset: Int): TextRange {
        val presentMapping = outputTransformedText?.value?.offsetMapping
        val visualMapping = codepointTransformedText?.value?.offsetMapping

        val intermediateOffset = visualMapping?.mapFromDest(offset) ?: TextRange(offset)
        return presentMapping?.let { mapFromTransformed(intermediateOffset, it) }
            ?: intermediateOffset
    }

    /**
     * Maps a [range] in [visualText] to the corresponding range in the untransformed text.
     *
     * If there is no transformation, or the transformation does not change the text, [range] will
     * be returned.
     *
     * @see mapToTransformed
     */
    fun mapFromTransformed(range: TextRange): TextRange {
        val presentMapping = outputTransformedText?.value?.offsetMapping
        val visualMapping = codepointTransformedText?.value?.offsetMapping

        val intermediateRange = visualMapping?.let { mapFromTransformed(range, it) } ?: range
        return presentMapping?.let { mapFromTransformed(intermediateRange, it) }
            ?: intermediateRange
    }

    // TODO(b/296583846) Get rid of this.
    /**
     * Adds a [TextFieldState3.NotifyImeListener] to the underlying [TextFieldState3] and then
     * suspends until cancelled, removing the listener before continuing.
     *
     * This listener is responsible for updating the IME about the latest changes to the underlying
     * [TextFieldState3]. Please note that the IME should be aware of the [outputText], rather than
     * [untransformedText] since users mainly interact with the output representation.
     *
     * The real challenge comes from the fact that IME doesn't need updates if its commands are not
     * interfered with. That's why [TextFieldState3.NotifyImeListener] actually sends the latest
     * synced value from IME, rather than the previous value inside the [TextFieldState3] before the
     * changes are applied. In the existence of [OutputTransformation], we have to transform these
     * values once more before updating the IME.
     */
    suspend fun collectImeNotifications(
        notifyImeListener: TextFieldState3.NotifyImeListener
    ): Nothing {
        val transformedNotifyImeListener =
            if (outputTransformation != null) {
                TextFieldState3.NotifyImeListener { oldValue, _, restartIme ->
                    notifyImeListener.onChange(
                        oldValue =
                            calculateTransformedText(
                                untransformedValue = oldValue,
                                outputTransformation = outputTransformation,
                                wedgeAffinity = selectionWedgeAffinity
                            )
                                ?.text ?: oldValue,
                        newValue = visualText,
                        restartIme = restartIme
                    )
                }
            } else {
                notifyImeListener
            }
        suspendCancellableCoroutine<Nothing> { continuation ->
            textFieldState.addNotifyImeListener(transformedNotifyImeListener)
            continuation.invokeOnCancellation {
                textFieldState.removeNotifyImeListener(transformedNotifyImeListener)
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TransformedTextFieldState3) return false
        if (textFieldState != other.textFieldState) return false
        if (codepointTransformation != other.codepointTransformation) return false
        return outputTransformation == other.outputTransformation
    }

    override fun hashCode(): Int {
        var result = textFieldState.hashCode()
        result = 31 * result + (codepointTransformation?.hashCode() ?: 0)
        result = 31 * result + (outputTransformation?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String =
        "TransformedTextFieldState3(" +
                "textFieldState=$textFieldState, " +
                "outputTransformation=$outputTransformation, " +
                "outputTransformedText=$outputTransformedText, " +
                "codepointTransformation=$codepointTransformation, " +
                "codepointTransformedText=$codepointTransformedText, " +
                "outputText=\"$outputText\", " +
                "visualText=\"$visualText\"" +
                ")"

    private data class TransformedText(
        val text: TextFieldCharSequence,
        val offsetMapping: OffsetMappingCalculator,
    )

    private companion object {

        /**
         * Applies an [OutputTransformation] to a [TextFieldCharSequence], returning the transformed
         * text content, the selection/cursor from the [untransformedValue] mapped to the offsets in
         * the transformed text, and an [OffsetMappingCalculator] that can be used to map offsets in
         * both directions between the transformed and untransformed text.
         *
         * This function is relatively expensive, since it creates a copy of [untransformedValue],
         * so its result should be cached.
         */
        @kotlin.jvm.JvmStatic
        private fun calculateTransformedText(
            untransformedValue: TextFieldCharSequence,
            outputTransformation: OutputTransformation,
            wedgeAffinity: SelectionWedgeAffinity
        ): TransformedText? {
            val offsetMappingCalculator = OffsetMappingCalculator()
            val buffer =
                TextFieldBuffer(
                    initialValue = untransformedValue,
                    offsetMappingCalculator = offsetMappingCalculator
                )

            // This is the call to external code.
            with(outputTransformation) { buffer.transformOutput() }

            // Avoid allocations + mapping if there weren't actually any transformations.
            if (buffer.changes.changeCount == 0) {
                return null
            }

            val transformedTextWithSelection =
                buffer.toTextFieldCharSequence(
                    // Pass the calculator explicitly since the one on transformedText won't be
                    // updated yet.
                    selection =
                        mapToTransformed(
                            range = untransformedValue.selection,
                            mapping = offsetMappingCalculator,
                            selectionWedgeAffinity = wedgeAffinity
                        ),
                    composition =
                        untransformedValue.composition?.let {
                            mapToTransformed(
                                range = it,
                                mapping = offsetMappingCalculator,
                                selectionWedgeAffinity = wedgeAffinity
                            )
                        }
                )
            return TransformedText(transformedTextWithSelection, offsetMappingCalculator)
        }

        /**
         * Applies a [CodepointTransformation] to a [TextFieldCharSequence], returning the
         * transformed text content, the selection/cursor from the [untransformedValue] mapped to
         * the offsets in the transformed text, and an [OffsetMappingCalculator] that can be used to
         * map offsets in both directions between the transformed and untransformed text.
         *
         * This function is relatively expensive, since it creates a copy of [untransformedValue],
         * so its result should be cached.
         */
        @kotlin.jvm.JvmStatic
        private fun calculateTransformedText(
            untransformedValue: TextFieldCharSequence,
            codepointTransformation: CodepointTransformation,
            wedgeAffinity: SelectionWedgeAffinity
        ): TransformedText? {
            val offsetMappingCalculator = OffsetMappingCalculator()

            // This is the call to external code. Returns same instance if no codepoints change.
            val transformedText =
                untransformedValue.toVisualText(codepointTransformation, offsetMappingCalculator)

            // Avoid allocations + mapping if there weren't actually any transformations.
            if (transformedText === untransformedValue) {
                return null
            }

            val transformedTextWithSelection =
                TextFieldCharSequence(
                    text = transformedText,
                    // Pass the calculator explicitly since the one on transformedText won't be
                    // updated
                    // yet.
                    selection =
                        mapToTransformed(
                            untransformedValue.selection,
                            offsetMappingCalculator,
                            wedgeAffinity
                        ),
                    composition =
                        untransformedValue.composition?.let {
                            mapToTransformed(it, offsetMappingCalculator, wedgeAffinity)
                        }
                )
            return TransformedText(transformedTextWithSelection, offsetMappingCalculator)
        }

        /**
         * Maps [range] from untransformed to transformed indices.
         *
         * @param selectionWedgeAffinity The [SelectionWedgeAffinity] to use to collapse the
         *   transformed range if necessary. If null, the range will be returned uncollapsed.
         */
        @kotlin.jvm.JvmStatic
        private fun mapToTransformed(
            range: TextRange,
            mapping: OffsetMappingCalculator,
            selectionWedgeAffinity: SelectionWedgeAffinity? = null
        ): TextRange {
            var transformedStart = mapping.mapFromSource(range.start)
            // Avoid calculating mapping again if it's going to be the same value.
            var transformedEnd =
                if (range.collapsed) transformedStart
                else {
                    mapping.mapFromSource(range.end)
                }

            // Do not use separate affinities when the selection is collapsed into a cursor.
            // This can show a selected region around a wedge when there is no selection in
            // the untransformed space. We use startAffinity for cursors.
            val startAffinity = selectionWedgeAffinity?.startAffinity
            val endAffinity =
                if (range.collapsed) {
                    startAffinity
                } else {
                    selectionWedgeAffinity?.endAffinity
                }

            if (startAffinity != null && !transformedStart.collapsed) {
                transformedStart =
                    when (startAffinity) {
                        WedgeAffinity.Start -> TextRange(transformedStart.start)
                        WedgeAffinity.End -> TextRange(transformedStart.end)
                    }
            }

            if (endAffinity != null && !transformedEnd.collapsed) {
                transformedEnd =
                    when (endAffinity) {
                        WedgeAffinity.Start -> TextRange(transformedEnd.start)
                        WedgeAffinity.End -> TextRange(transformedEnd.end)
                    }
            }

            val transformedMin = minOf(transformedStart.min, transformedEnd.min)
            val transformedMax = maxOf(transformedStart.max, transformedEnd.max)
            val transformedRange =
                if (range.reversed) {
                    TextRange(transformedMax, transformedMin)
                } else {
                    TextRange(transformedMin, transformedMax)
                }

            return transformedRange
        }

        @kotlin.jvm.JvmStatic
        private fun mapFromTransformed(
            range: TextRange,
            mapping: OffsetMappingCalculator
        ): TextRange {
            val untransformedStart = mapping.mapFromDest(range.start)
            // Avoid calculating mapping again if it's going to be the same value.
            val untransformedEnd =
                if (range.collapsed) untransformedStart
                else {
                    mapping.mapFromDest(range.end)
                }

            val untransformedMin = minOf(untransformedStart.min, untransformedEnd.min)
            val untransformedMax = maxOf(untransformedStart.max, untransformedEnd.max)
            return if (range.reversed) {
                TextRange(untransformedMax, untransformedMin)
            } else {
                TextRange(untransformedMin, untransformedMax)
            }
        }
    }
}

/**
 * Determines if the [transformedQueryIndex] is inside an insertion, replacement, deletion, or none
 * of the above as specified by the transformations on this [TransformedTextFieldState3].
 *
 * This function uses continuation-passing style to return multiple values without allocating.
 *
 * @param onResult Called with the determined [IndexTransformationType] and the ranges that
 *   [transformedQueryIndex] maps to both in the [TransformedTextFieldState3.untransformedText] and
 *   when that range is mapped back into the [TransformedTextFieldState3.visualText].
 */
internal inline fun <R> TransformedTextFieldState3.getIndexTransformationType(
    transformedQueryIndex: Int,
    onResult: (IndexTransformationType, untransformed: TextRange, retransformed: TextRange) -> R
): R {
    val untransformed = mapFromTransformed(transformedQueryIndex)
    val retransformed = mapToTransformed(untransformed)
    val type =
        when {
            untransformed.collapsed && retransformed.collapsed -> {
                // Simple case: no transformation in effect.
                Untransformed
            }
            !untransformed.collapsed && !retransformed.collapsed -> {
                // Replacement: An non-empty range in the source was replaced with a non-empty
                // string.
                Replacement
            }
            untransformed.collapsed && !retransformed.collapsed -> {
                // Insertion: An empty range in the source was replaced with a non-empty range.
                Insertion
            }
            else /* !untransformed.collapsed && retransformed.collapsed */ -> {
                // Deletion: A non-empty range in the source was replaced with an empty string.
                Deletion
            }
        }
    return onResult(type, untransformed, retransformed)
}
