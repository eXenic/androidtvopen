package com.androidtvopen.app

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.net.Uri
import android.os.Build

object TvSourceManager {

    fun scanTvSources(context: Context): List<TvSourceItem> {
        val tvInputManager = context.getSystemService(Context.TV_INPUT_SERVICE) as? TvInputManager
            ?: return emptyList()

        val inputs = try {
            tvInputManager.tvInputList
        } catch (_: Exception) {
            emptyList()
        }

        if (inputs.isEmpty()) return emptyList()

        // Prefer passthrough/hardware inputs, fallback to all if none are marked passthrough
        val passthroughOrHdmi = inputs.filter { it.isPassthroughInput || it.type == TvInputInfo.TYPE_HDMI }
        val targetList = if (passthroughOrHdmi.isNotEmpty()) passthroughOrHdmi else inputs

        return targetList.map { info ->
            val rawLabel = info.loadLabel(context)?.toString()?.trim().orEmpty()
            val label = rawLabel.ifBlank { info.id }
            val state = try {
                tvInputManager.getInputState(info.id)
            } catch (_: Exception) {
                TvInputManager.INPUT_STATE_DISCONNECTED
            }

            val isConnected = state == TvInputManager.INPUT_STATE_CONNECTED ||
                state == TvInputManager.INPUT_STATE_CONNECTED_STANDBY

            val stateLabel = when (state) {
                TvInputManager.INPUT_STATE_CONNECTED -> "Bağlı"
                TvInputManager.INPUT_STATE_CONNECTED_STANDBY -> "Beklemede"
                else -> "Bağlı Değil"
            }

            TvSourceItem(
                inputId = info.id,
                label = label,
                typeLabel = typeToString(info.type),
                stateLabel = stateLabel,
                isHdmi = info.type == TvInputInfo.TYPE_HDMI || label.contains("hdmi", ignoreCase = true),
                isConnected = isConnected
            )
        }.sortedWith(
            compareByDescending<TvSourceItem> { it.isHdmi }
                .thenBy { it.label.lowercase() }
        )
    }

    fun resolveInputId(context: Context, sourceLabel: String, savedInputId: String): String? {
        val tvInputManager = context.getSystemService(Context.TV_INPUT_SERVICE) as? TvInputManager
        val inputs = try {
            tvInputManager?.tvInputList.orEmpty()
        } catch (_: Exception) {
            emptyList()
        }

        val trimmedId = savedInputId.trim()
        if (trimmedId.isNotBlank()) {
            // If it matches an actual TvInputInfo.id or looks like a component/input ID, use it directly
            if (inputs.any { it.id == trimmedId } || trimmedId.contains("/")) {
                return trimmedId
            }
            // Otherwise also try matching it as a label
            findMatchingInputId(context, inputs, trimmedId)?.let { return it }
            return trimmedId
        }

        if (sourceLabel.isNotBlank()) {
            findMatchingInputId(context, inputs, sourceLabel)?.let { return it }
        }

        return null
    }

    private fun findMatchingInputId(
        context: Context,
        inputs: List<TvInputInfo>,
        queryLabel: String
    ): String? {
        val normalizedQuery = normalizeLabel(queryLabel)
        if (normalizedQuery.isBlank()) return null

        // 1. Exact normalized label match (e.g., "HDMI 4" == "hdmi4")
        for (info in inputs) {
            val label = info.loadLabel(context)?.toString().orEmpty()
            if (normalizeLabel(label) == normalizedQuery) {
                return info.id
            }
        }

        // 2. Partial label match (e.g., "HDMI 4 (ARC)" contains "hdmi4")
        for (info in inputs) {
            val label = info.loadLabel(context)?.toString().orEmpty()
            if (normalizeLabel(label).contains(normalizedQuery)) {
                return info.id
            }
        }

        // 3. HDMI port number match in label or inputId if query ends with a number (e.g., "HDMI 4")
        val portNum = Regex("(\\d+)$").find(queryLabel.trim())?.groupValues?.get(1)
        if (portNum != null) {
            for (info in inputs) {
                if (info.type == TvInputInfo.TYPE_HDMI) {
                    val label = info.loadLabel(context)?.toString().orEmpty()
                    if (label.endsWith(portNum) || info.id.contains("HDMI$portNum", ignoreCase = true)) {
                        return info.id
                    }
                }
            }
        }

        return null
    }

    fun switchToSource(
        context: Context,
        sourceLabel: String,
        savedInputId: String
    ): Result<String> {
        val trimmedId = savedInputId.trim()

        // Support direct URI if user manually entered content:// or tv://
        if (trimmedId.startsWith("content://") || trimmedId.startsWith("tv://")) {
            return launchViewIntent(context, Uri.parse(trimmedId), sourceLabel.ifBlank { trimmedId })
        }

        val resolvedId = resolveInputId(context, sourceLabel, trimmedId)
            ?: return Result.failure(
                IllegalStateException(
                    "TV kaynağı bulunamadı: '${sourceLabel.ifBlank { trimmedId }}'. " +
                        "Ayarlar ekranından TV kaynaklarını tarayın veya Input ID girin."
                )
            )

        val uri = TvContract.buildChannelUriForPassthroughInput(resolvedId)
        val display = sourceLabel.ifBlank { resolvedId }
        return launchViewIntent(context, uri, display)
    }

    private fun launchViewIntent(context: Context, uri: Uri, displayLabel: String): Result<String> {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Result.success("TV kaynağı açılıyor: $displayLabel")
        } catch (_: ActivityNotFoundException) {
            Result.failure(
                IllegalStateException(
                    "Bu cihazda '$displayLabel' kaynağını açacak sistem TV uygulaması bulunamadı."
                )
            )
        } catch (e: SecurityException) {
            Result.failure(
                IllegalStateException("TV kaynağına geçiş izni reddedildi: ${e.message}")
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun normalizeLabel(value: String): String {
        return value.lowercase().replace(Regex("[^a-z0-9]"), "")
    }

    private fun typeToString(type: Int): String {
        return when (type) {
            TvInputInfo.TYPE_HDMI -> "HDMI"
            TvInputInfo.TYPE_TUNER -> "Tuner / TV"
            TvInputInfo.TYPE_COMPOSITE -> "AV / Composite"
            TvInputInfo.TYPE_COMPONENT -> "Component"
            TvInputInfo.TYPE_DISPLAY_PORT -> "DisplayPort"
            TvInputInfo.TYPE_VGA -> "VGA"
            TvInputInfo.TYPE_DVI -> "DVI"
            TvInputInfo.TYPE_SCART -> "SCART"
            TvInputInfo.TYPE_SVIDEO -> "S-Video"
            else -> "Diğer"
        }
    }
}
