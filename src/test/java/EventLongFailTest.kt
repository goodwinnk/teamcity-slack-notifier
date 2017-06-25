package com.nk.tsn.test

import com.nk.tsn.LongFailedSettings
import com.nk.tsn.lastLFEMessage
import com.nk.tsn.prepareBuildLongFailedMessage
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class EventLongFailTest {
    @Test
    fun testNotTriggeredRecentFailure() {
        val settings = createSettings("master-dev-745")
        val data = prepareBuildLongFailedMessage(settings)
        Assertions.assertNull(data)
        Assertions.assertEquals("Only 1 days have passed. Will trigger after 3 days.", lastLFEMessage)
    }

    @Test
    fun testTriggerAfter1Day() {
        val settings = createSettings("master-dev-745", longFailedTriggerAfterDays = 1)
        val data = prepareBuildLongFailedMessage(settings)
        Assertions.assertNotNull(data)
        Assertions.assertEquals(1, data!!.numberOfDays)
        Assertions.assertEquals("master-dev-738", data.firstFailedBuild.buildNumber)
    }

    @Test
    fun testNotTriggeredBecauseOfPreviousBuild() {
        val settings = createSettings("master-dev-728")
        val data = prepareBuildLongFailedMessage(settings)
        Assertions.assertNull(data)
        Assertions.assertEquals("Same event might be generated for previous failed build master-dev-727", lastLFEMessage)
    }

    @Test
    fun testTriggeredInNormalStepDate() {
        val settings = createSettings("master-dev-715")
        val data = prepareBuildLongFailedMessage(settings)
        Assertions.assertNotNull(data)
        Assertions.assertEquals(5, data!!.numberOfDays)
        Assertions.assertEquals("master-dev-696", data.firstFailedBuild.buildNumber)
    }

    @Test
    fun testIgnoreStepSettingsForAbsenceBuilds() {
        val settings = createSettings("master-dev-708")
        val data = prepareBuildLongFailedMessage(settings)
        Assertions.assertNotNull(data)
        Assertions.assertEquals(4, data!!.numberOfDays)
        Assertions.assertEquals("master-dev-696", data.firstFailedBuild.buildNumber)
    }

    private fun createSettings(number: String, longFailedTriggerAfterDays: Int = 3, longFailedDaysStep: Int = 2): LongFailedSettings {
        return LongFailedSettings(
                serverUrl = "https://teamcity.jetbrains.com",
                buildConfigurationId = "Kotlin_master_CompilerAndPlugin_NoTests",
                branches = "<default>",
                longFailedTriggerAfterDays = longFailedTriggerAfterDays,
                longFailedDaysStep = longFailedDaysStep,
                number = number
        )
    }
}