package com.tracker.app.location

import org.junit.Assert.*
import org.junit.Test

class AlertEngineTest {
    private fun fix(id: String, km: Double, cap: Boolean = false) = Fix(id, -6.2 + km / 111.19, 106.8, 0, cap)

    @Test fun tightGroupNoAlert() {
        assertTrue(AlertEngine().evaluate(listOf(fix("c", 0.5, true), fix("a", 0.0)), 0).isEmpty())
    }
    @Test fun gapOver1100Flags() {
        val r = AlertEngine().evaluate(listOf(fix("c", 1.2, true), fix("b", 0.0)), 0)
        assertEquals(setOf("b"), r.keys)
        assertTrue(r["b"]!! in 1190..1210)
    }
    @Test fun chainNotFlagged() {
        val r = AlertEngine().evaluate(listOf(fix("c", 3.0, true), fix("a", 2.0), fix("b", 1.0), fix("d", 0.0)), 0)
        assertTrue(r.isEmpty())
    }
    @Test fun hysteresis() {
        val e = AlertEngine()
        e.evaluate(listOf(fix("c", 1.2, true), fix("b", 0.0)), 0)
        assertEquals(setOf("b"), e.evaluate(listOf(fix("c", 1.05, true), fix("b", 0.0)), 1).keys) // still separated
        assertTrue(e.evaluate(listOf(fix("c", 0.9, true), fix("b", 0.0)), 2).isEmpty())
    }
}
