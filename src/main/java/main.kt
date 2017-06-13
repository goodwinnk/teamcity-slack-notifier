@file:Suppress("PackageDirectoryMismatch")

package com.nk.tsn

import com.nk.tsn.args.initFromArgs

data class Settings(
        val slackWebHookUrl: String, // = "https://hooks.slack.com/services/{icoming-webhook-url}"
        val slackChannel: String?, // = @nk

        val serverUrl: String = "https://teamcity.jetbrains.com",
        val branches: String = "<default>",
        val number: String = "1.1.4-dev-518",
        val buildConfigurationId: String = "bt345",

        // For status change event
        val statusChangeEnabled: Boolean = true,
        val statusChangeForce: Boolean = false,

        // For long failed build event
        val longFailedEnabled: Boolean = true,
        val longFailedForce: Boolean = false,
        val longFailedTriggerAfterDays: Int = 3,
        val longFailedReTriggerAfterDays: Int = 2
)

fun main(args: Array<String>) {
    val settings = initFromArgs(Settings::class, args)
    println(settings)

    if (settings.longFailedEnabled || settings.longFailedForce) {
        checkBuildLongFailedEvent(settings)
    }

    if (settings.statusChangeEnabled || settings.statusChangeForce) {
        checkBuildStatusChangedEvent(settings)
    }
}