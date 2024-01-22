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

// suppress these so we can access TextFieldScrollerPosition
@file:Suppress("UNUSED", "INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package net.akehurst.kotlin.compose.editor

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.MyCoreTextField
import androidx.compose.foundation.text.MygetLineForVerticalPosition
import androidx.compose.foundation.text.TextFieldScrollerPosition
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.*
import androidx.compose.ui.text.input.TextFieldValue
import kotlin.math.max

interface EditorLineToken {
    val style: SpanStyle
    val start: Int
    val end: Int
}

data class CursorDetails(
    val brush: SolidColor,
    var start: Offset,
    var end: Offset
) {
    fun update(top: Offset, height: Float) {
        start = top
        end = Offset(top.x, top.y + height)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CodeEditor(
    initialText: String = "",
    onTextChange: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    getLineTokens: ((lineNumber: Int, lineText: String) -> List<EditorLineToken>) = { _, _ -> emptyList() },
) {
    val inputScrollerPosition = rememberSaveable(Orientation.Vertical, saver = TextFieldScrollerPosition.Saver) { TextFieldScrollerPosition(Orientation.Vertical) }
    var inputTextValue by remember { mutableStateOf(TextFieldValue(initialText)) }
    var viewTextValue by remember { mutableStateOf(TextFieldValue("")) }
    var viewFirstLinePos by remember { mutableStateOf(0) }
    var viewCursorRect by remember { mutableStateOf(Rect.Zero) }
    val defaultTextStyle = SpanStyle(color = MaterialTheme.colorScheme.onBackground)
    val viewCursors by remember { mutableStateOf(mutableListOf(CursorDetails(SolidColor(Color.Red), Offset.Zero, Offset.Zero))) }

    fun TextRange.toView(textLayoutResult: TextLayoutResult): TextRange {
        val s = this.start
        val e = this.end
        return TextRange(
            max(0, this.start - viewFirstLinePos),
            max(0, this.end - viewFirstLinePos)
        )
    }

    Surface {
        Column(modifier) {
            Surface(
                modifier = Modifier.weight(1f, true),
                color = MaterialTheme.colorScheme.background
            ) {
                Box {
                    CompositionLocalProvider(
                        LocalTextSelectionColors provides TextSelectionColors(
                            handleColor = LocalTextSelectionColors.current.handleColor,
                            backgroundColor = Color.Red
                        )
                    ) {
                        // Visible Viewport
                        // A CoreTextField that displays the styled text
                        // just the subsection of text that is visible is formatted
                        // The 'input' CoreTextField is transparent and sits on top of this.
                        MyCoreTextField(
                            readOnly = false,
                            enabled = true,
                            value = viewTextValue,
                            onValueChange = { viewTextValue = it },
                            onTextLayout = {},
                            onScroll = {
                                // update the drawn cursor position
                                if (it != null) {
                                    viewCursorRect = it.getCursorRect(viewTextValue.selection.start)
                                    val cr = viewCursorRect
                                    viewCursors[0].update(cr.topCenter, cr.height)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .drawWithContent {
                                    drawContent()
                                    // draw the cursors
                                    // (can't see how to make the actual cursor visible unless the control has focus)
                                    viewCursors.forEach {
                                        drawLine(
                                            strokeWidth = 3f,
                                            brush = it.brush,
                                            start = it.start,
                                            end = it.end
                                        )
                                    }
                                },
                            //cursorBrush = SolidColor(MaterialTheme.colorScheme.onBackground),
                        )
                    }

                    // The input CoreTextField
                    // sits on top receives user interactions
                    // and contains the whole text - with no syling
                    // Transparent
                    CompositionLocalProvider(
                        // make selections transparent in the in
                        LocalTextSelectionColors provides TextSelectionColors(
                            handleColor = LocalTextSelectionColors.current.handleColor,
                            backgroundColor = Color.Transparent
                        )
                    ) {
                        MyCoreTextField(
                            cursorBrush = SolidColor(Color.Transparent),
                            textStyle = TextStyle(color = Color.Transparent),
                            value = inputTextValue,
                            onValueChange = {
                                onTextChange(it.text)
                                inputTextValue = it
                            },
                            onTextLayout = {
                                viewTextValue = viewTextValue.copy(selection = inputTextValue.selection.toView(it))
                            },
                            modifier = Modifier
                                .fillMaxWidth(),
                            textScrollerPosition = inputScrollerPosition,
                            onScroll = { textLayoutResult ->
                                if (textLayoutResult != null) {
                                    val st = inputScrollerPosition.offset
                                    val len = inputScrollerPosition.viewportSize
                                    val firstLine = textLayoutResult.MygetLineForVerticalPosition(st)
                                    val lastLine = textLayoutResult.MygetLineForVerticalPosition(st + len)//-1
                                    val firstPos = textLayoutResult.getLineStart(firstLine)
                                    viewFirstLinePos = firstPos
                                    val lastPos = textLayoutResult.getLineEnd(lastLine)
                                    val viewText = inputTextValue.text.substring(firstPos, lastPos)
                                    val annotated = buildAnnotatedString {
                                        for (lineNum in firstLine..lastLine) {
                                            val lineStartPos = textLayoutResult.getLineStart(lineNum)
                                            val lineFinishPos = textLayoutResult.getLineEnd(lineNum)
                                            val lineText = inputTextValue.text.substring(lineStartPos, lineFinishPos)
                                            if (lineNum != firstLine) {
                                                append("\n")
                                            }
                                            append(lineText)
                                            addStyle(
                                                defaultTextStyle,
                                                lineStartPos - firstPos,
                                                lineFinishPos - firstPos
                                            )
                                            val toks = getLineTokens(lineNum, lineText)
                                            for (tk in toks) {
                                                val offsetStart = tk.start - firstPos
                                                val offsetFinish = tk.end - firstPos
                                                addStyle(tk.style, offsetStart, offsetFinish)
                                            }
                                        }
                                    }
                                    val sel = inputTextValue.selection //.toView(textLayoutResult)
                                    viewTextValue = inputTextValue.copy(annotatedString = annotated, selection = sel)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
