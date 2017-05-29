@file:Suppress("PackageDirectoryMismatch")

package com.nk.tsn

import net.gpedro.integrations.slack.SlackApi
import net.gpedro.integrations.slack.SlackMessage
import org.jetbrains.teamcity.rest.Build
import org.jetbrains.teamcity.rest.BuildConfigurationId
import org.jetbrains.teamcity.rest.TeamCityInstance

data class Settings(
        var number: String = "1.1.4-dev-492",
        var buildConfigurationId: String = "bt345",
        var branches: String = "<default>",
        var slackUrl: String = "https://hooks.slack.com/services/{icoming-webhook-url}"
)

fun main(args: Array<String>) {
    val settings = Settings().apply {
        with(args) {
            getOrNull(0)?.let { slackUrl = it }
            getOrNull(1)?.let { number = it }
        }
    }
    println(settings)

    val builds = TeamCityInstance.guestAuth("https://teamcity.jetbrains.com")
            .builds()
            .fromConfiguration(BuildConfigurationId(settings.buildConfigurationId))
            .limitResults(5)
            .withAnyStatus()
            .withBranch(settings.branches)
            .list()
    println(builds)

    val slackMessage = prepareNotification(settings.number, builds)
    println(slackMessage)

    SlackApi(settings.slackUrl).call(slackMessage)
}

fun createSlackMessage(statusText: String): SlackMessage {
    return SlackMessage().setText(statusText)
}

fun prepareNotification(buildNumber: String, builds: List<Build>): SlackMessage? {
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

    return createSlackMessage(currentBuild.statusText)
}