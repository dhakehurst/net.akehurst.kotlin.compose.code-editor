package net.akehurst.kotlin.components.layout.graph

import kotlin.test.Test
import kotlin.test.assertEquals

class test_SugiyamaLayout {

    @Test
    fun t() {
        val sl = SugiyamaLayout<Int>(

        )
        val actual = sl.layoutGraph(listOf(1,2),listOf(1 to 2))

        val expected = SugiyamaLayoutData(
            mapOf(
                1 to Pair(0.0,0.0),
                2 to Pair(0.0,130.0)
            ),
            mapOf(Pair(1,2) to listOf(Pair(0.0,0.0), Pair(0.0,130.0)))
        )
        assertEquals(expected,actual)
    }
}