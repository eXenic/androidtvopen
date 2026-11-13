package com.androidtvopen.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class SettingsRepository(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadProfiles(): List<TargetProfile> {
        val raw = prefs.getString(KEY_PROFILES_JSON, null)
        if (!raw.isNullOrBlank()) {
            try {
                val array = JSONArray(raw)
                val list = mutableListOf<TargetProfile>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(
                        TargetProfile(
                            id = obj.optString("id", TargetProfile.DEFAULT_ID),
                            pcName = obj.optString("pcName", TargetProfile.DEFAULT_PC_NAME)
                                .ifBlank { TargetProfile.DEFAULT_PC_NAME },
                            pcIp = obj.optString("pcIp", TargetProfile.DEFAULT_PC_IP).trim(),
                            pcMac = obj.optString("pcMac", TargetProfile.DEFAULT_PC_MAC).trim().uppercase(),
                            wolPort = obj.optInt("wolPort", TargetProfile.DEFAULT_WOL_PORT).coerceIn(1, 65535),
                            sourceLabel = obj.optString("sourceLabel", TargetProfile.DEFAULT_SOURCE_LABEL)
                                .ifBlank { TargetProfile.DEFAULT_SOURCE_LABEL },
                            sourceInputId = obj.optString("sourceInputId", "").trim(),
                            sleepUrl = obj.optString("sleepUrl", "").trim()
                        )
                    )
                }
                if (list.isNotEmpty()) return list
            } catch (_: Exception) {
            }
        }

        // Migrate legacy single-profile keys if present, else return default
        val legacyIp = prefs.getString(KEY_PC_IP, null)
        val initial = if (legacyIp != null) {
            TargetProfile(
                id = TargetProfile.DEFAULT_ID,
                pcName = prefs.getString(KEY_PC_NAME, TargetProfile.DEFAULT_PC_NAME)
                    ?.ifBlank { TargetProfile.DEFAULT_PC_NAME } ?: TargetProfile.DEFAULT_PC_NAME,
                pcIp = legacyIp.trim().ifBlank { TargetProfile.DEFAULT_PC_IP },
                pcMac = prefs.getString(KEY_PC_MAC, TargetProfile.DEFAULT_PC_MAC)
                    ?.trim().orEmpty().ifBlank { TargetProfile.DEFAULT_PC_MAC },
                wolPort = prefs.getInt(KEY_WOL_PORT, TargetProfile.DEFAULT_WOL_PORT).coerceIn(1, 65535),
                sourceLabel = prefs.getString(KEY_SOURCE_LABEL, TargetProfile.DEFAULT_SOURCE_LABEL)
                    ?.trim().orEmpty().ifBlank { TargetProfile.DEFAULT_SOURCE_LABEL },
                sourceInputId = prefs.getString(KEY_SOURCE_INPUT_ID, "")?.trim().orEmpty(),
                sleepUrl = ""
            )
        } else {
            TargetProfile.defaultProfile()
        }
        saveProfiles(listOf(initial), initial.id)
        return listOf(initial)
    }

    fun getActiveProfileId(): String {
        return prefs.getString(KEY_ACTIVE_PROFILE_ID, TargetProfile.DEFAULT_ID)
            ?.ifBlank { TargetProfile.DEFAULT_ID } ?: TargetProfile.DEFAULT_ID
    }

    fun getActiveProfile(): TargetProfile {
        val profiles = loadProfiles()
        val activeId = getActiveProfileId()
        return profiles.firstOrNull { it.id == activeId } ?: profiles.first()
    }

    fun setActiveProfileId(id: String) {
        prefs.edit().putString(KEY_ACTIVE_PROFILE_ID, id).apply()
    }

    fun saveProfiles(profiles: List<TargetProfile>, activeId: String) {
        val safeList = profiles.ifEmpty { listOf(TargetProfile.defaultProfile()) }
        val array = JSONArray()
        for (p in safeList) {
            val obj = JSONObject()
            obj.put("id", p.id)
            obj.put("pcName", p.pcName.trim().ifBlank { TargetProfile.DEFAULT_PC_NAME })
            obj.put("pcIp", p.pcIp.trim())
            obj.put("pcMac", p.pcMac.trim().uppercase())
            obj.put("wolPort", p.wolPort.coerceIn(1, 65535))
            obj.put("sourceLabel", p.sourceLabel.trim().ifBlank { TargetProfile.DEFAULT_SOURCE_LABEL })
            obj.put("sourceInputId", p.sourceInputId.trim())
            obj.put("sleepUrl", p.sleepUrl.trim())
            array.put(obj)
        }
        val resolvedActiveId = if (safeList.any { it.id == activeId }) activeId else safeList.first().id
        prefs.edit()
            .putString(KEY_PROFILES_JSON, array.toString())
            .putString(KEY_ACTIVE_PROFILE_ID, resolvedActiveId)
            .apply()
    }

    fun upsertProfile(profile: TargetProfile, makeActive: Boolean = true) {
        val current = loadProfiles().toMutableList()
        val idx = current.indexOfFirst { it.id == profile.id }
        if (idx >= 0) {
            current[idx] = profile
        } else {
            current.add(profile)
        }
        saveProfiles(current, if (makeActive) profile.id else getActiveProfileId())
    }

    fun deleteProfile(profileId: String) {
        val current = loadProfiles().filterNot { it.id == profileId }
        val safe = current.ifEmpty { listOf(TargetProfile.defaultProfile()) }
        val active = getActiveProfileId()
        val nextActive = if (safe.any { it.id == active }) active else safe.first().id
        saveProfiles(safe, nextActive)
    }

    fun loadBehavior(): AppBehaviorSettings {
        return AppBehaviorSettings(
            autoRunOnLaunch = prefs.getBoolean(KEY_AUTO_RUN, false),
            waitForPcOnline = prefs.getBoolean(KEY_WAIT_FOR_PC_ONLINE, true),
            wolRetryDuringWait = prefs.getBoolean(KEY_WOL_RETRY_DURING_WAIT, true),
            waitTimeoutSeconds = prefs.getInt(KEY_WAIT_TIMEOUT, 45).coerceIn(5, 180),
            switchDelaySeconds = prefs.getInt(KEY_SWITCH_DELAY, 1).coerceIn(0, 10)
        )
    }

    fun saveBehavior(behavior: AppBehaviorSettings) {
        prefs.edit()
            .putBoolean(KEY_AUTO_RUN, behavior.autoRunOnLaunch)
            .putBoolean(KEY_WAIT_FOR_PC_ONLINE, behavior.waitForPcOnline)
            .putBoolean(KEY_WOL_RETRY_DURING_WAIT, behavior.wolRetryDuringWait)
            .putInt(KEY_WAIT_TIMEOUT, behavior.waitTimeoutSeconds.coerceIn(5, 180))
            .putInt(KEY_SWITCH_DELAY, behavior.switchDelaySeconds.coerceIn(0, 10))
            .apply()
    }

    fun resetToDefaults(): Pair<TargetProfile, AppBehaviorSettings> {
        val defProfile = TargetProfile.defaultProfile()
        val defBehavior = AppBehaviorSettings()
        saveProfiles(listOf(defProfile), defProfile.id)
        saveBehavior(defBehavior)
        return defProfile to defBehavior
    }

    companion object {
        private const val PREFS_NAME = "android_tv_open_prefs"
        private const val KEY_PROFILES_JSON = "profiles_json"
        private const val KEY_ACTIVE_PROFILE_ID = "active_profile_id"
        private const val KEY_PC_NAME = "pc_name"
        private const val KEY_PC_IP = "pc_ip"
        private const val KEY_PC_MAC = "pc_mac"
        private const val KEY_WOL_PORT = "wol_port"
        private const val KEY_SOURCE_LABEL = "source_label"
        private const val KEY_SOURCE_INPUT_ID = "source_input_id"
        private const val KEY_AUTO_RUN = "auto_run_on_launch"
        private const val KEY_WAIT_FOR_PC_ONLINE = "wait_for_pc_online"
        private const val KEY_WOL_RETRY_DURING_WAIT = "wol_retry_during_wait"
        private const val KEY_WAIT_TIMEOUT = "wait_timeout_seconds"
        private const val KEY_SWITCH_DELAY = "switch_delay_seconds"
    }
}
