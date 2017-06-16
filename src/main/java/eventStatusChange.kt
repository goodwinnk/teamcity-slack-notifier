package com.nk.tsn

import net.gpedro.integrations.slack.SlackApi
import net.gpedro.integrations.slack.SlackAttachment
import net.gpedro.integrations.slack.SlackMessage
import org.jetbrains.teamcity.rest.*

fun checkBuildStatusChangedEvent(settings: Settings) {
    val slackMessage = prepareStatusChangeMessage(settings) ?: return
    println(slackMessage)

    SlackApi(settings.slackWebHookUrl).call(slackMessage)
}

fun prepareStatusChangeMessage(settings: Settings): SlackMessage? {
    val (currentBuild, previousBuild) = fetchBuildWithPreviousByFinishDate(
            settings.number, settings.serverUrl, settings.buildConfigurationId, settings.branches)

    if (currentBuild == null) {
        println("Can't find build with number: ${settings.number}")
        return null
    }
    println("Current build: $currentBuild")

    if (!settings.statusChangeForce) {
        if (previousBuild == null) {
            println("Can't find previous build for number: ${settings.number}")
            return null
        }
        println("Previous build: $previousBuild")

        if (!isChangeStatusEventTriggered(currentBuild, previousBuild, settings.statusChangeOnSuccess)) {
            return null
        }
    }

    val teamCityInstance = TeamCityInstance.guestAuth(settings.serverUrl)
    val buildConfigurationId = BuildConfigurationId(settings.buildConfigurationId)

    val buildConfiguration = teamCityInstance.buildConfiguration(buildConfigurationId)
    println(buildConfiguration.name)

    return currentBuild.createSlackNotification(buildConfiguration)
}

fun isChangeStatusEventTriggered(currentBuild: Build, previousBuild: Build, statusChangeOnSuccess: Boolean): Boolean {
    if (currentBuild.id.stringId.toLong() < previousBuild.id.stringId.toLong()) {
        println("Current build has probably started earlier but finished after more recent build. Don't report " +
                "status change as it's outdated. A notification might be already sent for more recent build.\n" +
                "$currentBuild")
        return false
    }

    if (currentBuild.status == previousBuild.status) {
        // Nothing interesting
        println("Status of builds hasn't changed: ${currentBuild.status}\n" +
                "$currentBuild")
        return false
    }

    if (!statusChangeOnSuccess && currentBuild.status == BuildStatus.SUCCESS) {
        println("Canceled because of statusChangeOnSuccess setting")
        return false
    }

    return true
}

fun fetchBuildWithPreviousByFinishDate(
        currentNumber: String, teamcityUrl: String, configurationId: String, branch: String): Pair<Build?, Build?> {
    val teamCityInstance = TeamCityInstance.guestAuth(teamcityUrl)
    val buildConfigurationId = BuildConfigurationId(configurationId)

    val currentBuild = teamCityInstance.builds()
            .fromConfiguration(buildConfigurationId)
            .withBranch(branch)
            .withAnyStatus().withAnyFailedToStart()
            .limitResults(1)
            .withNumber(currentNumber)
            .list().firstOrNull()

    @Suppress("FoldInitializerAndIfToElvis")
    if (currentBuild == null) {
        return (null to null)
    }

    val previousBuild = teamCityInstance.builds()
            .fromConfiguration(buildConfigurationId)
            .withBranch(branch)
            .limitResults(1)
            .withAnyStatus().withAnyFailedToStart()
            .withFinishDateQuery(beforeBuildQuery(teamCityInstance.builds().withAnyStatus().withId(currentBuild.id)))
            .list().firstOrNull()

    return currentBuild to previousBuild
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

