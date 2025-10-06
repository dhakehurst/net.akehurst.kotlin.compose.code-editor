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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals


class test_MutableStateFlowHolderMap {

    @Test
    fun value__initial() {
        val sut = mutableStateFlowHolderMapOf(mapOf(1 to "initial"))

        assertEquals(mapOf(1 to "initial"), sut.value)
    }

    @Test
    fun value__afterUpdate() {
        val sut = mutableStateFlowHolderMapOf(mapOf(1 to "initial"))
        assertEquals(mapOf(1 to "initial"), sut.value)

        sut.updateAll { mapOf(1 to "a", 2 to "b", 3 to "c") }

        assertEquals(mapOf(1 to "a", 2 to "b", 3 to "c"), sut.value)
    }

    @Test
    fun OnUpdated__afterUpdateAll() = runTest {
        val sut = mutableStateFlowHolderMapOf(mapOf(1 to "initial"))
        var update = mapOf<Int,String>()
        val updatedJob = launch {
            sut.onUpdated(this) { map, e ->
                update = map
            }
        }
        advanceUntilIdle()

        sut.updateAll { mapOf(1 to "a", 2 to "b", 3 to "c") }
        advanceUntilIdle()

        assertEquals(mapOf(1 to "a", 2 to "b", 3 to "c"), sut.value)
        assertEquals(mapOf(1 to "a", 2 to "b", 3 to "c"), update)

        updatedJob.cancel()
    }


    @Test
    fun OnUpdated__afterUpdateOne() = runTest(context = StandardTestDispatcher()) {
        val sut = mutableStateFlowHolderMapOf(mapOf(1 to "initial"))
        var update = mapOf<Int,String>()
        val updatedJob = launch {
            sut.onUpdated(this) { list,e ->
                update = list
            }
        }
        advanceUntilIdle() //wait for init

        sut.update(1) { "z" }
        advanceUntilIdle() //wait for update
        assertEquals(mapOf(1 to "z"), update)

        sut.update(1) { "y" }
        advanceUntilIdle() //wait for update
        assertEquals(mapOf(1 to "y"), update)

        sut.update(2) { "x" }
        advanceUntilIdle() //wait for update
        assertEquals(mapOf(1 to "y", 2 to "x"), update)

        sut.update(1) { "w" }
        advanceUntilIdle() //wait for update
        assertEquals(mapOf(1 to "w", 2 to "x"), update)

        updatedJob.cancel()
    }

}