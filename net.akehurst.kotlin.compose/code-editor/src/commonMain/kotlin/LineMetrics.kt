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

class LineMetrics(
    initialText: CharSequence
) {
    companion object {
        val eol = Regex("\n")
    }

    //private val mutex = Mutex()
    private var lineEndsAt = eol.findAll(initialText).toList()

    val lineCount get() = lineEndsAt.size + 1
    val firstPosition = 0
    var lastPosition = initialText.length; private set

//    fun update(newText: String) {
////        mutex.withLock {
//        lastPosition = newText.length
//        lineEndsAt = eol.findAll(newText).toList()
////        }
//    }

    fun lineForPosition(position: Int): Int {
        val ln = lineEndsAt.indexOfLast { it.range.first < position }
        return when (ln) {
            -1 -> 0 // position must be on first line
            else -> ln + 1
        }
    }

    /**
     * start of firstLine
     * finish of lastLine
     */
    fun viewEnds(firstLine: Int, lastLine: Int): Pair<Int, Int> {
//        mutex.withLock {
        val s = lineStart(firstLine)
        val f = lineFinish(lastLine)
        return Pair(s, f)
//        }
    }

    /**
     * line start and finish
     */
    fun lineEnds(lineNumber: Int): Pair<Int, Int> {
//        mutex.withLock {
        val s = lineStart(lineNumber)
        val f = lineFinish(lineNumber)
        return Pair(s, f)
//        }
    }

    /**
     * index/position in text where lineNumber starts (after previous line EOL)
     */
    private fun lineStart(lineNumber: Int) = when {
        0 == lineNumber -> 0
        lineNumber >= (lineCount) -> lastPosition
        else -> lineEndsAt[lineNumber - 1].range.last + 1
    }

    /**
     * index/position in text where lineNumber finishes (before EOL)
     */
    private fun lineFinish(lineNumber: Int) = when {
        lineNumber >= (lineCount - 1) -> lastPosition
        else -> lineEndsAt[lineNumber].range.first
    }
}