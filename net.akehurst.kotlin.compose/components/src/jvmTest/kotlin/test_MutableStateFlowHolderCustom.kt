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

@file:OptIn(ExperimentalCoroutinesApi::class)

package net.akehurst.kotlin.compose.components.flowHolder

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class MutableStateFlowHolderCustomContainer {

    val stringValue = mutableStateFlowHolderOf("s-initial")
    val listValue = mutableStateFlowHolderListOf(listOf("l-initial"))
    val mapValue = mutableStateFlowHolderMapOf(mapOf("mk-initial" to "mv-initial"))
    val partsMap = mutableStateFlowHolderMapOf<String, MutableStateFlowHolderCustomPart>(emptyMap())

    val contentFlow
        get() = mergeParts(
            Pair("string", stringValue.stateFlow),
            Pair("list", listValue.contentFlow),
            Pair("map", mapValue.contentFlow),
            Pair("parts", partsMap.contentFlow),
        )

    fun onUpdated(scope: CoroutineScope, action: (MutableStateFlowHolderCustomContainer, CompositeEvent) -> Unit) {
        scope.launch {
            contentFlow
                .shareIn(this, SharingStarted.Eagerly, 0) // use values from now onwards
                .collect { it ->
                    println("collected: $it")
                    action.invoke(this@MutableStateFlowHolderCustomContainer, it)
                }
        }
    }
}

class MutableStateFlowHolderCustomPart : MutableStateFlowHolderComposite {
    val stringValue = mutableStateFlowHolderOf("p-initial")

    override val contentFlow get() = mergeParts(Pair("string", stringValue.stateFlow))
}

class test_MutableStateFlowHolderCustom {

    @Test
    fun value__initial() {
        val sut = mutableStateFlowHolderOf(MutableStateFlowHolderCustomContainer())

        assertEquals("s-initial", sut.value.stringValue.value)
    }

    @Test
    fun update_string() = runTest {
        val sut = MutableStateFlowHolderCustomContainer()

        var actual = Pair("unset", "unset")
        advanceUntilIdle()
        val updatedJob = launch {
            sut.onUpdated(this) { h, (k, v) ->
                actual = Pair(k.joinToString("."), v as String)
            }
        }
        advanceUntilIdle()

        sut.stringValue.update { "new value" }
        advanceUntilIdle()

        assertEquals(Pair("string", "new value"), actual)
        advanceUntilIdle()
        updatedJob.cancel()
    }

    @Test
    fun update_list_element() = runTest {
        val sut = MutableStateFlowHolderCustomContainer()

        var actual = Pair("unset", "unset")
        val updatedJob = launch {
            sut.onUpdated(this) { h, (k, v) ->
                actual = Pair(k.joinToString("."), v as String)
            }
        }
        advanceUntilIdle()

        sut.listValue.update(1) { "new value" }
        advanceUntilIdle()

        assertEquals(Pair("list.1", "new value"), actual)
        advanceUntilIdle()
        updatedJob.cancel()
    }

    @Test
    fun update_map_element() = runTest {
        val sut = MutableStateFlowHolderCustomContainer()

        var actual = Pair("unset", "unset")
        val updatedJob = launch {
            sut.onUpdated(this) { h, (k, v) ->
                actual = Pair(k.joinToString("."), v as String)
            }
        }
        advanceUntilIdle()

        sut.mapValue.update("a") { "new value" }
        advanceUntilIdle()

        assertEquals(Pair("map.a", "new value"), actual)
        advanceUntilIdle()
        updatedJob.cancel()
    }

    @Test
    fun update_part_string() = runTest {
        val sut = MutableStateFlowHolderCustomContainer()

        var actual = Pair("unset", "unset")
        val updatedJob = launch {
            sut.onUpdated(this) { h, (k, v) ->
                actual = Pair(k.joinToString("."), v as String)
            }
        }
        assertEquals(Pair("unset", "unset"), actual)
        advanceUntilIdle()

        sut.partsMap.update("p1") { MutableStateFlowHolderCustomPart() }
        advanceUntilIdle()  // this coroutine needs to suspend before update occurs

        sut.partsMap["p1"]?.value?.stringValue?.update { "new value" }
        advanceUntilIdle()

        assertEquals(Pair("parts.p1.string", "new value"), actual)
        advanceUntilIdle()
        updatedJob.cancel()
    }
}