package com.nk.tsn

import net.gpedro.integrations.slack.SlackApi
import net.gpedro.integrations.slack.SlackAttachment
import net.gpedro.integrations.slack.SlackMessage
import org.jetbrains.teamcity.rest.*
import java.util.*

fun checkBuildLongFailedEvent(settings: Settings) {
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

    if (daysWithoutSuccessful <= settings.longFailedTriggerAfterDays) {
        println("Only $daysWithoutSuccessful days have passed. Will trigger after ${settings.longFailedTriggerAfterDays} days.")
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

private fun Build.createLongFailedSlackNotification(configuration: BuildConfiguration, numberOfDays: Int): SlackMessage {
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
