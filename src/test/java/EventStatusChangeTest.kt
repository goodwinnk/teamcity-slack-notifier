package com.nk.tsn.test

import com.nk.tsn.fetchBuildWithPreviousByFinishDate
import com.nk.tsn.isChangeStatusEventTriggered
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class EventStatusChangeTest {
    // Test may fail if history in configuration is outdated
    @Test fun testCurrentPrevious() {
        fun test(currentNumber: String, expectedFound: Boolean, previousNumber: String) {
            val (current, previous) = fetchBuildWithPreviousByFinishDate(currentNumber,
                    "https://teamcity.jetbrains.com", "bt345", "<default>")

            Assertions.assertEquals(expectedFound, current != null)
            if (current == null) return

            Assertions.assertEquals(previousNumber, previous!!.buildNumber)
        }

        // Normal
        test("1.1.4-dev-710", true, "1.1.4-dev-702")

        // Don't skip failed to start build
        test("1.1.4-dev-693", true, "1.1.4-dev-692")

        // Start with failed to start build. Failed to start build might start later but finish earlier.
        // It's expected behaviour to skip unfinished builds.
        test("1.1.4-dev-692", true, "1.1.4-dev-683")

        // Some long build might finish after a build that started later, so there might builds reodering.
        test("1.1.4-dev-688", true, "1.1.4-dev-692")

        // Test invalid build number
        test("1.1.4-dev-fake-688", false, "")
    }

    @Test
    fun testEventTriggered() {
        fun test(currentNumber: String, isTriggered: Boolean) {
            val (current, previous) = fetchBuildWithPreviousByFinishDate(currentNumber,
                    "https://teamcity.jetbrains.com", "bt345", "<default>")

            Assertions.assertEquals(isTriggered, isChangeStatusEventTriggered(current!!, previous!!))
        }

        // Normal success
        test("1.1.4-dev-726", true)

        // Normal failed
        test("1.1.4-dev-725", true)

        // Not triggered on success
        test("1.1.4-dev-714", false)

        // Not triggered on failure
        test("1.1.4-dev-702", false)

        // No triggered on success with cancel build
        test("1.1.4-dev-735", false)

        // Triggered for failed build as previous failed build was too long
        test("1.1.4-dev-692", true)

        // Not triggered for long failed build because when it's finished there's more recent build already
        test("1.1.4-dev-688", false)

    }
}