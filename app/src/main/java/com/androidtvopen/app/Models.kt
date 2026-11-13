package com.androidtvopen.app

import java.util.UUID

data class TargetProfile(
    val id: String = UUID.randomUUID().toString(),
    val pcName: String = DEFAULT_PC_NAME,
    val pcIp: String = DEFAULT_PC_IP,
    val pcMac: String = DEFAULT_PC_MAC,
    val wolPort: Int = DEFAULT_WOL_PORT,
    val sourceLabel: String = DEFAULT_SOURCE_LABEL,
    val sourceInputId: String = "",
    val sleepUrl: String = ""
) {
    companion object {
        const val DEFAULT_ID = "default-main-pc"
        const val DEFAULT_PC_NAME = "Ana PC"
        const val DEFAULT_PC_IP = "10.0.0.132"
        const val DEFAULT_PC_MAC = "E0:51:D8:1C:50:DB"
        const val DEFAULT_WOL_PORT = 9
        const val DEFAULT_SOURCE_LABEL = "HDMI 4"

        fun defaultProfile(): TargetProfile = TargetProfile(
            id = DEFAULT_ID,
            pcName = DEFAULT_PC_NAME,
            pcIp = DEFAULT_PC_IP,
            pcMac = DEFAULT_PC_MAC,
            wolPort = DEFAULT_WOL_PORT,
            sourceLabel = DEFAULT_SOURCE_LABEL,
            sourceInputId = "",
            sleepUrl = ""
        )
    }
}

data class AppBehaviorSettings(
    val autoRunOnLaunch: Boolean = false,
    val waitForPcOnline: Boolean = true,
    val wolRetryDuringWait: Boolean = true,
    val waitTimeoutSeconds: Int = 45,
    val switchDelaySeconds: Int = 1
)

data class DiscoveredPc(
    val ip: String,
    val mac: String,
    val hostname: String,
    val isOnline: Boolean
)

data class TvSourceItem(
    val inputId: String,
    val label: String,
    val typeLabel: String,
    val stateLabel: String,
    val isHdmi: Boolean,
    val isConnected: Boolean
)
