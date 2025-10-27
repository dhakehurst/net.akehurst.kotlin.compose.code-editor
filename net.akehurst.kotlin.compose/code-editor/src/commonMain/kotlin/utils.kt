
@file:Suppress("UNUSED", "INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package net.akehurst.kotlin.compose.editor

import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.sp
import me.saket.extendedspans.SquigglyUnderlineSpanPainter
import net.akehurst.kotlin.compose.editor.api.EditorSegmentStyle
import net.akehurst.kotlin.compose.editor.api.TextDecorationStyle

object ComposeEditorUtils {
    val STRAIGHT = SquigglyUnderlineSpanPainter(
        "STRAIGHT",
        width = 3.sp,
        wavelength = 20.sp,
        amplitude = 0.sp,
        bottomOffset = 1.sp,
        //animator = underlineAnimator
    )
    val SQUIGGLY = SquigglyUnderlineSpanPainter(
        "SQUIGGLY",
        width = 3.sp,
        wavelength = 15.sp,
        amplitude = 2.sp,
        bottomOffset = 1.sp,
        //animator = underlineAnimator
    )

    // currently used for margin item positions and limits the position to in range of first/last lines
    internal fun offsetFromTopOfViewport(lineNumber: Int, viewFirstLine: Int, viewLastLine: Int, lineScrollOffset:Float, textLayoutResult: TextLayoutResult): Float {
        //val lineBot = textLayoutResult.getLineBottom(lineNumber)
//println("lineNumber: $lineNumber, viewFirstLine:$viewFirstLine, viewLastLine: $viewLastLine, lineScrollOffset: $lineScrollOffset")
        return when {
            lineNumber < (viewFirstLine+1) -> 0f // if line is partially visible, just use top of viewport
            lineNumber >= (viewLastLine-1) -> textLayoutResult.getLineTop(viewLastLine)- lineScrollOffset // likewise don't go below last line
            else -> {
                val lineIndex = lineNumber - viewFirstLine
                textLayoutResult.getLineTop(lineIndex) - lineScrollOffset
            }
        }
    }

    internal fun lineHeight(textLayoutResult: TextLayoutResult, lineNumber: Int): Float {
        return if (lineNumber >= 0 && lineNumber < textLayoutResult.lineCount) {
            textLayoutResult.getLineBottom(lineNumber) - textLayoutResult.getLineTop(lineNumber)
        } else {
            0f
        }
    }

    internal fun annotateTextFieldBuffer(
        buffer: TextFieldBuffer,
        viewFirstLine: Int,
        viewLastLine: Int,
        lineStyles: Map<Int, List<EditorSegmentStyle>>,
        textMarkers: List<TextMarkerDefault>,
        annotatedTextChange: (AnnotatedString) -> Unit
    ) {
        val rawText = buffer.asCharSequence()
        if (rawText.isNotEmpty()) {
            val annotatedText = annotateText(rawText, viewFirstLine, viewLastLine, lineStyles, textMarkers)
            buffer.setComposition(0, rawText.length, annotatedText.annotations)
            buffer.changeTracker.trackChange(0, rawText.length, rawText.length)
            annotatedTextChange.invoke(annotatedText)
        }
    }

    fun annotateText(rawText: CharSequence, viewFirstLine: Int, viewLastLine: Int, lineTokens: Map<Int, List<EditorSegmentStyle>>, markers: List<TextMarkerDefault>): AnnotatedString {
        return if (rawText.isEmpty()) {
            AnnotatedString(rawText.toString())
        } else {
            // lines from textLayoutResult are possible different to actual ines in text defined by EOL.
            //  eg if lines are wrapped by the layout, thus have to compute own lineMetrics
            val lineMetrics = LineMetrics(rawText)
            buildAnnotatedString {
                append(rawText)
                // annotate from tokens
                for (lineNum in viewFirstLine..viewLastLine) {
                    val (lineStartPos, lineFinishPos) = lineMetrics.lineEnds(lineNum)
                    val toks = lineTokens.getOrElse(lineNum) { emptyList() }
                    for (tk in toks) {
                        val offsetStart = (lineStartPos + tk.start).coerceIn(lineStartPos, lineFinishPos)
                        val offsetFinish = (lineStartPos + tk.finish).coerceIn(lineStartPos, lineFinishPos)
                        addStyle(tk.style, offsetStart, offsetFinish)
                    }
                }

                // annotate from markers
                for (marker in markers) {
                    // println("Marker at: ${marker.position} length ${marker.length} line $lineNum")
                    val offsetStart = (marker.position).coerceIn(0, rawText.length)
                    val offsetFinish = (marker.position + marker.length).coerceIn(0, rawText.length)
                    //println("Style at: ${offsetStart} .. ${offsetFinish}")
                    val ss = when (marker.decoration) {
                        TextDecorationStyle.NONE -> null
                        TextDecorationStyle.STRAIGHT -> STRAIGHT.decorate(marker.style, offsetStart, offsetFinish, builder = this)
                        TextDecorationStyle.SQUIGGLY -> SQUIGGLY.decorate(marker.style, offsetStart, offsetFinish, builder = this)
                    }
                    ss?.let { addStyle(it, offsetStart, offsetFinish) }
                }
            }
        }
    }
}