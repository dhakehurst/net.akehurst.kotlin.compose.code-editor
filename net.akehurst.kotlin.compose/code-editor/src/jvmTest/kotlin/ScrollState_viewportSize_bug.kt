/**
 * Copyright (C) 2025 Dr. David H. Akehurst (http://dr.david.h.akehurst.net)
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

package test

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.Surface
import androidx.compose.material.TextField
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import org.junit.Test;
import androidx.compose.ui.window.singleWindowApplication

class ScrollState_viewportSize_bug {

    @Test
    fun test() {

        singleWindowApplication(
            title = "Code Editor 2 Test",
        ) {
            val textState = rememberTextFieldState()
            val scrollState = rememberScrollState()

            Surface {
                TextField(
                    state = textState,
                    scrollState=scrollState,
                    modifier = Modifier
                        .fillMaxSize()
                )

                LaunchedEffect(scrollState.value) {
                    snapshotFlow { scrollState }.collect {
                        println("ss: ${scrollState.value}, ${scrollState.viewportSize}")
                    }
                }

            }
        }

    }

}
