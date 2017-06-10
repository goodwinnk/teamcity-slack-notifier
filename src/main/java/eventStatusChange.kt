package com.nk.tsn

import net.gpedro.integrations.slack.SlackApi
import net.gpedro.integrations.slack.SlackAttachment
import net.gpedro.integrations.slack.SlackMessage
import org.jetbrains.teamcity.rest.*

fun checkBuildStatusChangedEvent(settings: Settings) {
    val teamCityInstance = TeamCityInstance.guestAuth(settings.serverUrl)
    val builds = teamCityInstance
            .builds()
            .fromConfiguration(BuildConfigurationId(settings.buildConfigurationId))
            .limitResults(5)
            .withAnyStatus()
            .withAnyFailedToStart()
            .withBranch(settings.branches)
            .list()
    println(builds)

    val buildConfiguration = teamCityInstance.buildConfiguration(BuildConfigurationId(settings.buildConfigurationId))
    println(buildConfiguration.name)

    val slackMessage = prepareNotification(settings.number, buildConfiguration, builds)
    println(slackMessage)

    SlackApi(settings.slackWebHookUrl).call(slackMessage)
}

private fun prepareNotification(buildNumber: String, buildConfiguration: BuildConfiguration, builds: List<Build>): SlackMessage? {
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

private fun Build.createSlackNotification(configuration: BuildConfiguration): SlackMessage {
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
