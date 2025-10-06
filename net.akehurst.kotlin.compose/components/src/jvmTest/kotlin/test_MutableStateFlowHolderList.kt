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

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals


class test_MutableStateFlowHolderList {

    @Test
    fun value__initial() {
        val sut = mutableStateFlowHolderListOf(listOf("initial"))

        assertEquals(listOf("initial"), sut.value)
    }

    @Test
    fun value__afterUpdate() {
        val sut = mutableStateFlowHolderListOf(listOf("initial"))
        assertEquals(listOf("initial"), sut.value)

        sut.updateAll { listOf("a", "b", "c") }

        assertEquals(listOf("a", "b", "c"), sut.value)
    }

    @Test
    fun OnUpdated__afterUpdateAll() = runTest {
        val sut = mutableStateFlowHolderListOf(listOf("initial"))

        var update = listOf<String>()
        val updatedJob = launch {
            sut.onUpdated(this) { list, e ->
                update = list
            }
        }
        advanceUntilIdle()

        sut.updateAll { listOf("a", "b", "c") }
        advanceUntilIdle()

        assertEquals(listOf("a", "b", "c"), sut.value)
        assertEquals(listOf("a", "b", "c"), update)

        updatedJob.cancel()
    }


    @Test
    fun OnUpdated__afterUpdateOne() = runTest(context = StandardTestDispatcher()) {
        val sut = mutableStateFlowHolderListOf(listOf("a", "b", "c"))
        assertEquals(listOf("a", "b", "c"), sut.value)
        var update = listOf<String>()
        val updatedJob = launch {
            sut.onUpdated(this) { list, e ->
                update = list
            }
        }
        advanceUntilIdle() //wait for init

        sut.update(1) { "z" }
        advanceUntilIdle() //wait for update
        assertEquals(listOf("a", "z", "c"), update)

        sut.update(1) { "y" }
        advanceUntilIdle() //wait for update
        assertEquals(listOf("a", "y", "c"), update)

        sut.update(1) { "x" }
        advanceUntilIdle() //wait for update
        assertEquals(listOf("a", "x", "c"), update)

        sut.update(1) { "w" }
        advanceUntilIdle() //wait for update
        assertEquals(listOf("a", "w", "c"), sut.value)
        assertEquals(listOf("a", "w", "c"), update)

        updatedJob.cancel()
    }

}