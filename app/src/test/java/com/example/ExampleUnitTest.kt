package com.example

import com.example.ai.AiInterruptionAnalyzer
import com.example.model.ActionType
import com.example.model.MacroProfile
import com.example.model.TargetPoint
import com.example.model.TimerUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class ExampleUnitTest {

    @Test
    fun timerUnit_conversions_areCorrect() {
        assertEquals(60L, TimerUnit.MINUTES.toSeconds(1L))
        assertEquals(3600L, TimerUnit.HOURS.toSeconds(1L))
        assertEquals(120L, TimerUnit.SECONDS.toSeconds(120L))
        assertEquals(2L, TimerUnit.MINUTES.fromSeconds(120L))
        assertEquals(1L, TimerUnit.HOURS.fromSeconds(3600L))
    }

    @Test
    fun crossClickDetection_identifiesOverlappingTargets() {
        val t1 = TargetPoint(number = 1, xRatio = 0.5f, yRatio = 0.5f)
        val t2 = TargetPoint(number = 2, xRatio = 0.51f, yRatio = 0.51f)
        val distance = hypot((t1.xRatio - t2.xRatio).toDouble(), (t1.yRatio - t2.yRatio).toDouble())
        assertTrue("Targets should be detected as overlapping", distance < 0.065)

        val t3 = TargetPoint(number = 3, xRatio = 0.8f, yRatio = 0.8f)
        val farDistance = hypot((t1.xRatio - t3.xRatio).toDouble(), (t1.yRatio - t3.yRatio).toDouble())
        assertFalse("Far targets should not conflict", farDistance < 0.065)
    }

    @Test
    fun aiInterruptionAnalyzer_detectsAdKeywords() {
        val adScreenTexts = listOf("Game Level 5", "Reward granted in 5s", "Sponsored Advertisement", "✕ Close")
        val result = AiInterruptionAnalyzer.fastHeuristicCheck(adScreenTexts)
        assertTrue("Should detect interruption", result.isInterruption)
        assertTrue("Should detect dismiss button", result.isDismissable)

        val cleanScreenTexts = listOf("Dashboard", "Settings", "Account Profile", "Save")
        val cleanResult = AiInterruptionAnalyzer.fastHeuristicCheck(cleanScreenTexts)
        assertFalse("Clean screen should not be an interruption", cleanResult.isInterruption)
    }

    @Test
    fun macroProfile_targetOrder_preserved() {
        val targets = listOf(
            TargetPoint(number = 1, type = ActionType.TAP, xRatio = 0.3f, yRatio = 0.4f),
            TargetPoint(number = 2, type = ActionType.SWIPE, xRatio = 0.5f, yRatio = 0.6f, endXRatio = 0.5f, endYRatio = 0.2f)
        )
        val macro = MacroProfile(name = "Test Macro", targets = targets, loopCount = 3)
        assertEquals(2, macro.targets.size)
        assertEquals(ActionType.TAP, macro.targets[0].type)
        assertEquals(ActionType.SWIPE, macro.targets[1].type)
        assertEquals(3, macro.loopCount)
    }
}
