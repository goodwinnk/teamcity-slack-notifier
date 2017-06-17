@file:Suppress("PackageDirectoryMismatch")

package com.nk.tsn

import com.nk.tsn.args.initFromArgs
import org.jetbrains.teamcity.rest.Build
import org.jetbrains.teamcity.rest.BuildConfigurationId
import org.jetbrains.teamcity.rest.TeamCityInstance

data class Settings(
        val slackWebHookUrl: String, // = "https://hooks.slack.com/services/{icoming-webhook-url}"
        val counter: String,
        val ownConfigurationId: String,

        val slackChannel: String? = null, // = @nk

        val serverUrl: String = "https://teamcity.jetbrains.com",
        val branches: String = "<default>",
        var number: String = "",
        val buildConfigurationId: String = "bt345",

        // For status change event
        val statusChangeEnabled: Boolean = true,
        val statusChangeForce: Boolean = false,
        val statusChangeOnSuccess: Boolean = true,

        // For long failed build event
        val longFailedEnabled: Boolean = true,
        val longFailedForce: Boolean = false,
        val longFailedTriggerAfterDays: Int = 3,
        val longFailedReTriggerAfterDays: Int = 2
)

fun main(args: Array<String>) {
    val settings = initFromArgs(Settings::class, args)
    println(settings)

    val lastTargetBuild = lastBuildInConfiguration(settings.serverUrl, settings.buildConfigurationId, settings.branches)
    if (lastTargetBuild == null) {
        println("Last build wasn't found")
        return
    }

    println("##teamcity[buildNumber '${settings.counter}:${lastTargetBuild.id.stringId}:${lastTargetBuild.buildNumber}']")
    if (!(settings.longFailedForce || settings.statusChangeForce)) {
        settings.number = lastTargetBuild.buildNumber
    }

    if (settings.number.isEmpty()) {
        println("Build number wasn't set")
        return
    }

    if (settings.longFailedEnabled || settings.longFailedForce) {
        checkBuildLongFailedEvent(settings)
    }

    if (settings.statusChangeEnabled || settings.statusChangeForce) {
        checkBuildStatusChangedEvent(settings)
    }
}

fun lastBuildInConfiguration(url: String, configurationId: String, branch: String = "<default>"): Build? {
    val teamCityInstance = TeamCityInstance.guestAuth(url)
    val buildConfigurationId = BuildConfigurationId(configurationId)
    return teamCityInstance.builds()
            .fromConfiguration(buildConfigurationId)
            .withBranch(branch)
            .withAnyStatus().withAnyFailedToStart()
            .limitResults(1)
            .latest()
}