package com.nk.tsn

import net.gpedro.integrations.slack.SlackApi
import net.gpedro.integrations.slack.SlackAttachment
import net.gpedro.integrations.slack.SlackMessage
import org.jetbrains.teamcity.rest.*
import java.util.*

fun checkBuildLongFailedEvent(settings: Settings) {
    val messageData = prepareBuildLongFailedMessage(
            LongFailedSettings(
                    settings.number,
                    settings.serverUrl,
                    settings.buildConfigurationId,
                    settings.branches,
                    settings.longFailedTriggerAfterDays,
                    settings.longFailedDaysStep)) ?: return

    val slackMessage = createLongFailedSlackNotification(messageData)
    log(messageData.toString())

    SlackApi(settings.slackWebHookUrl).call(slackMessage)
}

class LongFailedSettings(
        val number: String,
        val serverUrl: String,
        val buildConfigurationId: String,
        val branches: String,
        val longFailedTriggerAfterDays: Int,
        val longFailedDaysStep: Int
)

data class LongStatusEventTriggeredData(
        val firstFailedBuild: Build, val numberOfDays: Int, val configuration: BuildConfiguration)

fun prepareBuildLongFailedMessage(settings: LongFailedSettings): LongStatusEventTriggeredData? {
    val teamCityInstance = TeamCityInstance.guestAuth(settings.serverUrl)

    val buildConfigurationId = BuildConfigurationId(settings.buildConfigurationId)
    val buildConfiguration = teamCityInstance.buildConfiguration(buildConfigurationId)
    log("Configuration name: ${buildConfiguration.name}")

    val currentBuild = teamCityInstance.builds()
            .fromConfiguration(buildConfigurationId)
            .withBranch(settings.branches)
            .withAnyStatus().withAnyFailedToStart()
            .withNumber(settings.number)
            .limitResults(1)
            .list().firstOrNull()

    if (currentBuild == null) {
        log("Can't find build with number ${settings.number}")
        return null
    }

    if (currentBuild.status == BuildStatus.SUCCESS) {
        log("Requested build with number  ${settings.number} is successful")
        return null
    }

    val lastSuccessfulBeforeCurrent = teamCityInstance
            .builds()
            .fromConfiguration(buildConfigurationId)
            .withBranch(settings.branches)
            .withStatus(BuildStatus.SUCCESS)
            .withFinishDateQuery(beforeBuildQuery(teamCityInstance.builds().withAnyStatus().withId(currentBuild.id)))
            .limitResults(1)
            .latest()

    if (lastSuccessfulBeforeCurrent == null) {
        log("Can't find last successful build for long failed event")
        return null
    }

    val finishDateOfSuccessful = lastSuccessfulBeforeCurrent.fetchFinishDate()
    val finishDateOfCurrent = currentBuild.fetchFinishDate()

    val daysWithoutSuccessful = daysDiff(finishDateOfSuccessful, finishDateOfCurrent)
    log("Days without success $daysWithoutSuccessful")
    if (daysWithoutSuccessful < settings.longFailedTriggerAfterDays) {
        log("Only $daysWithoutSuccessful days have passed. Will trigger after ${settings.longFailedTriggerAfterDays} days.")
        return null
    }

    val failedBeforeCurrent = teamCityInstance.builds()
            .fromConfiguration(buildConfigurationId)
            .withBranch(settings.branches)
            .withStatus(BuildStatus.FAILURE).withAnyFailedToStart()
            .withFinishDateQuery(beforeBuildQuery(teamCityInstance.builds().withAnyStatus().withId(currentBuild.id)))
            .limitResults(1)
            .list().firstOrNull()

    if (failedBeforeCurrent == null) {
        log("There should be at least two failed builds")
        return null
    }

    val previousFailedDate = failedBeforeCurrent.fetchFinishDate()
    val previousFailDays = daysDiff(finishDateOfSuccessful, previousFailedDate)
    if (previousFailDays == daysWithoutSuccessful) {
        log("Same event might be generated for previous failed build ${failedBeforeCurrent.buildNumber}")
        return null
    }

    // Don't allow to trigger event more frequently than longFailedDaysStep
    if ((daysWithoutSuccessful - settings.longFailedTriggerAfterDays) % settings.longFailedDaysStep != 0) {
        // Ignore the rule if last notification in valid step was missed because of absent build
        // NOTE: This may produce two subsequent notifications in interval less than longFailedDaysStep
        val previousFailInterval = (previousFailDays - settings.longFailedTriggerAfterDays) / settings.longFailedDaysStep
        val currentFailInterval = (daysWithoutSuccessful - settings.longFailedTriggerAfterDays) / settings.longFailedDaysStep
        if (previousFailInterval == currentFailInterval) {
            log("Exit because of day trigger step. " +
                    "Day passed ${daysWithoutSuccessful}, " +
                    "wait for ${settings.longFailedTriggerAfterDays}, " +
                    "step ${settings.longFailedDaysStep}.")
            return null
        }
    }

    val firstFailedBuild = teamCityInstance.builds()
            .fromConfiguration(buildConfigurationId)
            .withBranch(settings.branches)
            .withStatus(BuildStatus.FAILURE).withAnyFailedToStart()
            .withSinceBuild(teamCityInstance.builds().withAnyStatus().withId(lastSuccessfulBeforeCurrent.id))
            .withFinishDateQuery(beforeBuildQuery(teamCityInstance.builds().withAnyStatus().withId(currentBuild.id)))
            .list().last()

    log("Triggered! Last success ${lastSuccessfulBeforeCurrent.buildNumber},  " +
            "first failed: ${firstFailedBuild.buildNumber}, days: $daysWithoutSuccessful")

    return LongStatusEventTriggeredData(firstFailedBuild, daysWithoutSuccessful, buildConfiguration)
}

private fun createLongFailedSlackNotification(messageData: LongStatusEventTriggeredData) = messageData.firstFailedBuild.run {
    val changes = fetchChanges()
    val authors = changes.map { it.username }.distinct().joinToString(" ")
    val fire = ":fire:".repeat(messageData.numberOfDays, " ")
    val title = "$buildNumber ${messageData.configuration.name} ${branch.name}"
    val message = "The build is failed for ${messageData.numberOfDays} days!$fire"

    val attachment = SlackAttachment().apply {
        setFallback("$title $message")
        setTitle(title)
        setTitleLink("https://teamcity.jetbrains.com/viewLog.html?buildId=${id.stringId}")
        setText("$message\n$authors")
        setColor(if (status == BuildStatus.SUCCESS) "#36a64f" else "#a6364f")
    }

    SlackMessage("").addAttachments(attachment)
}

fun CharSequence.repeat(n: Int, separator: String): String {
    require(n >= 0) { "Count 'n' must be non-negative, but was $n." }
    if (separator.isEmpty()) {
        return repeat(n)
    }

    return when (n) {
        0 -> ""
        1 -> this.toString()
        else -> {
            when (length) {
                0 -> "" // empty string if base is empty
                else -> {
                    val sb = StringBuilder(n * length + (n - 1) * separator.length)
                    sb.append(this)
                    for (i in 2..n) {
                        sb.append(separator)
                        sb.append(this)
                    }
                    sb.toString()
                }
            }
        }
    }
}

private fun daysDiff(date1: Date, date2: Date): Int {
    val diff = Math.abs(date1.time - date2.time)
    return (diff / (24 * 60 * 60 * 1000)).toInt()
}

var lastLFEMessage = ""

private fun log(message: String) {
    lastLFEMessage = message
    println("LFE: $message")
}