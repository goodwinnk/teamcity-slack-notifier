@file:Suppress("PackageDirectoryMismatch")

package com.nk.tsn

import com.nk.tsn.args.initFromArgs
import net.gpedro.integrations.slack.SlackApi
import net.gpedro.integrations.slack.SlackAttachment
import net.gpedro.integrations.slack.SlackMessage
import org.jetbrains.teamcity.rest.*
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.util.*

data class Settings(
        val slackWebHookUrl: String, // = "https://hooks.slack.com/services/{icoming-webhook-url}"
        val slackChannel: String?, // = @nk

        val serverUrl: String = "https://teamcity.jetbrains.com",

        // For status change event
        val statusChangeEnabled: Boolean = true,
        val number: String = "1.1.4-dev-518",
        val buildConfigurationId: String = "bt345",
        val branches: String = "<default>",

        // For long failed build event
        val longFailedEnabled: Boolean = true,
        val triggerAfterDays: Int = 3
)

fun main(args: Array<String>) {
    val settings = initFromArgs(Settings::class, args)
    println(settings)

    if (settings.longFailedEnabled) {
        checkBuildLongFailedEvent(settings)
    }

//    if (settings.statusChangeEnabled) {
//        checkBuildStatusChangedEvent(settings)
//    }
}

private fun checkBuildLongFailedEvent(settings: Settings) {
    val teamCityInstance = TeamCityInstance.guestAuth(settings.serverUrl)
    val lastSuccessfulBuild = teamCityInstance
            .builds()
            .fromConfiguration(BuildConfigurationId(settings.buildConfigurationId))
            .withBranch(settings.branches)
            .withStatus(BuildStatus.SUCCESS)
            .limitResults(1)
            .latest() ?: return

    val finishDate: Date = lastSuccessfulBuild.fetchFinishDate()
    val now = Date()

    val diff = Math.abs(now.getTime() - finishDate.getTime())
    val daysWithoutSuccessful = (diff / (24 * 60 * 60 * 1000)).toInt()

    if (daysWithoutSuccessful <= settings.triggerAfterDays) {
        println("Only $daysWithoutSuccessful days have passed. Will trigger after ${settings.triggerAfterDays} days.")
        return
    }

    // TODO: sinceBuild locator
    val failedBuilds = teamCityInstance
            .builds()
            .fromConfiguration(BuildConfigurationId(settings.buildConfigurationId))
            .withBranch(settings.branches)
            .withStatus(BuildStatus.FAILURE)
            .limitResults(50)
            .list()

    val lastSuccessfulId = lastSuccessfulBuild.id.stringId.toInt()
    val firstFailedBuild = failedBuilds.takeWhile { it.id.stringId.toInt() > lastSuccessfulId }.last()

    println("SUCCESS: ${lastSuccessfulBuild.buildNumber} FAILED: ${firstFailedBuild.buildNumber}")

    val buildConfiguration = teamCityInstance.buildConfiguration(BuildConfigurationId(settings.buildConfigurationId))
    println(buildConfiguration.name)

    val slackNotification = firstFailedBuild.createLongFailedSlackNotification(buildConfiguration, daysWithoutSuccessful)
    println(slackNotification)

    SlackApi(settings.slackWebHookUrl).call(slackNotification)
}

private fun checkBuildStatusChangedEvent(settings: Settings) {
    val teamCityInstance = TeamCityInstance.guestAuth(settings.serverUrl)
    val builds = teamCityInstance
            .builds()
            .fromConfiguration(BuildConfigurationId(settings.buildConfigurationId))
            .limitResults(5)
            .withAnyStatus()
            .withBranch(settings.branches)
            .list()
    println(builds)

    val buildConfiguration = teamCityInstance.buildConfiguration(BuildConfigurationId(settings.buildConfigurationId))
    println(buildConfiguration.name)

    val slackMessage = prepareNotification(settings.number, buildConfiguration, builds)
    println(slackMessage)

    SlackApi(settings.slackWebHookUrl).call(slackMessage)
}

fun Build.createLongFailedSlackNotification(configuration: BuildConfiguration, numberOfDays: Int): SlackMessage {
    val changes = fetchChanges()
    val authors = changes.map { it.username }.distinct().joinToString(" ")
    val fire = when {
        numberOfDays == 1 -> ":fire:"
        numberOfDays > 1 -> ":fire:" + " :fire:".repeat(numberOfDays - 1)
        else -> " "
    }

    val title = "$buildNumber ${configuration.name} ${branch.name}"
    val message = "The build is failed for $numberOfDays days!$fire"

    val attachment = SlackAttachment().apply {
        setFallback("$title $message")
        setTitle(title)
        setTitleLink("https://teamcity.jetbrains.com/viewLog.html?buildId=${id.stringId}")
        setText("$message\n$authors")
        setColor(if (status == BuildStatus.SUCCESS) "#36a64f" else "#a6364f")
    }

    return SlackMessage("").addAttachments(attachment)
}

fun Build.createSlackNotification(configuration: BuildConfiguration): SlackMessage {
    val changes = fetchChanges()
    val authors = changes.map { it.username }.distinct().joinToString(" ")
    val title = "$buildNumber ${configuration.name} ${branch.name}"

    val attachment = SlackAttachment().apply {
        setFallback("$title ${if (status == BuildStatus.SUCCESS) "Failed" else "Fixed"}")
        setTitle(title)
        setTitleLink("https://teamcity.jetbrains.com/viewLog.html?buildId=${id.stringId}")
        setText("${fetchStatusText()}\n$authors")
        setColor(if (status == BuildStatus.SUCCESS) "#36a64f" else "#a6364f")
    }

    return SlackMessage("").addAttachments(attachment)
}

fun prepareNotification(buildNumber: String, buildConfiguration: BuildConfiguration, builds: List<Build>): SlackMessage? {
    val index = builds.indexOfFirst { it.buildNumber == buildNumber }
    if (index == -1) return null

    if (index + 1 >= builds.size) return null

    val currentBuild = builds[index]
    val previousBuild = builds[index + 1]

    if (currentBuild.status == previousBuild.status) {
        // Nothing interesting
        println("Same status")
        return null
    }

    return currentBuild.createSlackNotification(buildConfiguration)
}