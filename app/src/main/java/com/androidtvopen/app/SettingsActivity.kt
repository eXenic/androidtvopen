package com.androidtvopen.app

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch
import java.util.UUID

class SettingsActivity : AppCompatActivity() {

    private lateinit var repository: SettingsRepository

    private lateinit var btnBack: Button
    private lateinit var btnSaveSettings: Button
    private lateinit var btnNewProfile: Button
    private lateinit var btnDeleteProfile: Button
    private lateinit var btnScanNetwork: Button
    private lateinit var btnScanSources: Button
    private lateinit var btnResetDefaults: Button

    private lateinit var tvNetworkScanStatus: TextView
    private lateinit var tvSourceScanStatus: TextView

    private lateinit var rvSettingsProfiles: RecyclerView
    private lateinit var rvDiscoveredPcs: RecyclerView
    private lateinit var rvTvSources: RecyclerView

    private lateinit var etPcName: EditText
    private lateinit var etPcIp: EditText
    private lateinit var etPcMac: EditText
    private lateinit var etWolPort: EditText
    private lateinit var etSleepUrl: EditText
    private lateinit var etSourceLabel: EditText
    private lateinit var etSourceInputId: EditText
    private lateinit var etWaitTimeout: EditText
    private lateinit var etSwitchDelay: EditText
    private lateinit var switchWaitForPcOnline: SwitchCompat
    private lateinit var switchWolRetry: SwitchCompat
    private lateinit var switchAutoRun: SwitchCompat

    private lateinit var profileAdapter: ProfileChipAdapter
    private lateinit var pcAdapter: DiscoveredPcAdapter
    private lateinit var sourceAdapter: TvSourceAdapter

    private var editingProfileId: String = TargetProfile.DEFAULT_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        repository = SettingsRepository(this)
        bindViews()
        setupAdapters()

        val createNew = intent.getBooleanExtra(EXTRA_NEW_PROFILE, false)
        if (createNew) {
            startNewProfileDraft()
        } else {
            val requestedId = intent.getStringExtra(EXTRA_PROFILE_ID)
            val profiles = repository.loadProfiles()
            val target = profiles.firstOrNull { it.id == requestedId } ?: repository.getActiveProfile()
            editingProfileId = target.id
            loadProfileFields(target)
        }

        loadBehaviorFields(repository.loadBehavior())
        refreshProfileChips()
        setupListeners()
    }

    private fun bindViews() {
        btnBack = findViewById(R.id.btnBack)
        btnSaveSettings = findViewById(R.id.btnSaveSettings)
        btnNewProfile = findViewById(R.id.btnNewProfile)
        btnDeleteProfile = findViewById(R.id.btnDeleteProfile)
        btnScanNetwork = findViewById(R.id.btnScanNetwork)
        btnScanSources = findViewById(R.id.btnScanSources)
        btnResetDefaults = findViewById(R.id.btnResetDefaults)

        tvNetworkScanStatus = findViewById(R.id.tvNetworkScanStatus)
        tvSourceScanStatus = findViewById(R.id.tvSourceScanStatus)

        rvSettingsProfiles = findViewById(R.id.rvSettingsProfiles)
        rvDiscoveredPcs = findViewById(R.id.rvDiscoveredPcs)
        rvTvSources = findViewById(R.id.rvTvSources)

        etPcName = findViewById(R.id.etPcName)
        etPcIp = findViewById(R.id.etPcIp)
        etPcMac = findViewById(R.id.etPcMac)
        etWolPort = findViewById(R.id.etWolPort)
        etSleepUrl = findViewById(R.id.etSleepUrl)
        etSourceLabel = findViewById(R.id.etSourceLabel)
        etSourceInputId = findViewById(R.id.etSourceInputId)
        etWaitTimeout = findViewById(R.id.etWaitTimeout)
        etSwitchDelay = findViewById(R.id.etSwitchDelay)
        switchWaitForPcOnline = findViewById(R.id.switchWaitForPcOnline)
        switchWolRetry = findViewById(R.id.switchWolRetry)
        switchAutoRun = findViewById(R.id.switchAutoRun)
    }

    private fun setupAdapters() {
        profileAdapter = ProfileChipAdapter { selected ->
            editingProfileId = selected.id
            repository.setActiveProfileId(selected.id)
            loadProfileFields(selected)
            refreshProfileChips()
        }
        rvSettingsProfiles.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        rvSettingsProfiles.adapter = profileAdapter

        pcAdapter = DiscoveredPcAdapter { pc ->
            if (pc.hostname.isNotBlank()) {
                etPcName.setText(pc.hostname.substringBefore("."))
            }
            etPcIp.setText(pc.ip)
            if (pc.mac.isNotBlank()) {
                etPcMac.setText(pc.mac)
            }
            Toast.makeText(this, "PC seçildi: ${pc.ip}", Toast.LENGTH_SHORT).show()
        }
        rvDiscoveredPcs.layoutManager = LinearLayoutManager(this)
        rvDiscoveredPcs.adapter = pcAdapter

        sourceAdapter = TvSourceAdapter { source ->
            etSourceLabel.setText(source.label)
            etSourceInputId.setText(source.inputId)
            Toast.makeText(this, "Kaynak seçildi: ${source.label}", Toast.LENGTH_SHORT).show()
        }
        rvTvSources.layoutManager = LinearLayoutManager(this)
        rvTvSources.adapter = sourceAdapter
    }

    private fun refreshProfileChips() {
        val profiles = repository.loadProfiles()
        profileAdapter.submitList(profiles, editingProfileId)
        val canDelete = profiles.size > 1 && profiles.any { it.id == editingProfileId }
        btnDeleteProfile.visibility = if (canDelete) View.VISIBLE else View.GONE
    }

    private fun startNewProfileDraft() {
        editingProfileId = UUID.randomUUID().toString()
        val draft = TargetProfile(
            id = editingProfileId,
            pcName = "Yeni PC",
            pcIp = "",
            pcMac = "",
            wolPort = 9,
            sourceLabel = "HDMI 1",
            sourceInputId = "",
            sleepUrl = ""
        )
        loadProfileFields(draft)
        etPcName.requestFocus()
    }

    private fun loadProfileFields(profile: TargetProfile) {
        etPcName.setText(profile.pcName)
        etPcIp.setText(profile.pcIp)
        etPcMac.setText(profile.pcMac)
        etWolPort.setText(profile.wolPort.toString())
        etSleepUrl.setText(profile.sleepUrl)
        etSourceLabel.setText(profile.sourceLabel)
        etSourceInputId.setText(profile.sourceInputId)
    }

    private fun loadBehaviorFields(behavior: AppBehaviorSettings) {
        etWaitTimeout.setText(behavior.waitTimeoutSeconds.toString())
        etSwitchDelay.setText(behavior.switchDelaySeconds.toString())
        switchWaitForPcOnline.isChecked = behavior.waitForPcOnline
        switchWolRetry.isChecked = behavior.wolRetryDuringWait
        switchAutoRun.isChecked = behavior.autoRunOnLaunch
    }

    private fun setupListeners() {
        btnBack.setOnClickListener { finish() }
        btnNewProfile.setOnClickListener {
            startNewProfileDraft()
            refreshProfileChips()
            Toast.makeText(this, "Yeni profil oluşturun ve Kaydet'e basın.", Toast.LENGTH_SHORT).show()
        }
        btnDeleteProfile.setOnClickListener {
            repository.deleteProfile(editingProfileId)
            val next = repository.getActiveProfile()
            editingProfileId = next.id
            loadProfileFields(next)
            refreshProfileChips()
            Toast.makeText(this, "Profil silindi.", Toast.LENGTH_SHORT).show()
        }

        btnScanNetwork.setOnClickListener { scanNetwork() }
        btnScanSources.setOnClickListener { scanTvSources() }

        btnResetDefaults.setOnClickListener {
            val (defProfile, defBehavior) = repository.resetToDefaults()
            editingProfileId = defProfile.id
            loadProfileFields(defProfile)
            loadBehaviorFields(defBehavior)
            refreshProfileChips()
            Toast.makeText(this, "Varsayılan ayarlar yüklendi (10.0.0.132 / HDMI 4).", Toast.LENGTH_SHORT).show()
        }

        btnSaveSettings.setOnClickListener { saveAndExit() }
    }

    private fun scanNetwork() {
        val netInfo = NetworkScanner.getLocalNetworkInfo()
        if (netInfo == null) {
            tvNetworkScanStatus.text = "Aktif yerel ağ bağlantısı bulunamadı."
            return
        }

        btnScanNetwork.isEnabled = false
        tvNetworkScanStatus.text = "${netInfo.subnetPrefix}.0/24 ağı taranıyor..."

        lifecycleScope.launch {
            val found = NetworkScanner.scanLocalNetwork { done, total ->
                runOnUiThread {
                    tvNetworkScanStatus.text = "${netInfo.subnetPrefix}.0/24 taranıyor... ($done/$total)"
                }
            }

            btnScanNetwork.isEnabled = true
            if (found.isEmpty()) {
                rvDiscoveredPcs.visibility = View.GONE
                tvNetworkScanStatus.text = "Ağda cihaz bulunamadı. IP ve MAC adresini elle girebilirsiniz."
            } else {
                pcAdapter.submitList(found)
                rvDiscoveredPcs.visibility = View.VISIBLE
                tvNetworkScanStatus.text = "${found.size} cihaz bulundu. Seçmek için üzerine tıklayın:"
            }
        }
    }

    private fun scanTvSources() {
        val sources = TvSourceManager.scanTvSources(this)
        if (sources.isEmpty()) {
            rvTvSources.visibility = View.GONE
            tvSourceScanStatus.text = "Sistemde TV giriş kaynağı bulunamadı. Kaynak adını / ID'yi elle girebilirsiniz."
        } else {
            sourceAdapter.submitList(sources)
            rvTvSources.visibility = View.VISIBLE
            tvSourceScanStatus.text = "${sources.size} TV kaynağı bulundu. Seçmek için üzerine tıklayın:"
        }
    }

    private fun saveAndExit() {
        val rawMac = etPcMac.text.toString().trim()
        val normalizedMac = WakeOnLanSender.normalizeMac(rawMac)
        if (normalizedMac == null) {
            etPcMac.error = "Geçerli bir 12 haneli MAC adresi girin (Örn: E0:51:D8:1C:50:DB)"
            etPcMac.requestFocus()
            return
        }

        val port = etWolPort.text.toString().trim().toIntOrNull()?.takeIf { it in 1..65535 } ?: 9
        val waitTimeout = etWaitTimeout.text.toString().trim().toIntOrNull()?.coerceIn(5, 180) ?: 45
        val delaySec = etSwitchDelay.text.toString().trim().toIntOrNull()?.coerceIn(0, 10) ?: 1
        val sourceLabel = etSourceLabel.text.toString().trim().ifBlank { TargetProfile.DEFAULT_SOURCE_LABEL }

        val profile = TargetProfile(
            id = editingProfileId,
            pcName = etPcName.text.toString().trim().ifBlank { TargetProfile.DEFAULT_PC_NAME },
            pcIp = etPcIp.text.toString().trim(),
            pcMac = normalizedMac,
            wolPort = port,
            sourceLabel = sourceLabel,
            sourceInputId = etSourceInputId.text.toString().trim(),
            sleepUrl = etSleepUrl.text.toString().trim()
        )
        val behavior = AppBehaviorSettings(
            autoRunOnLaunch = switchAutoRun.isChecked,
            waitForPcOnline = switchWaitForPcOnline.isChecked,
            wolRetryDuringWait = switchWolRetry.isChecked,
            waitTimeoutSeconds = waitTimeout,
            switchDelaySeconds = delaySec
        )

        repository.upsertProfile(profile, makeActive = true)
        repository.saveBehavior(behavior)
        Toast.makeText(this, "Profil ve ayarlar kaydedildi.", Toast.LENGTH_SHORT).show()
        finish()
    }

    companion object {
        const val EXTRA_PROFILE_ID = "extra_profile_id"
        const val EXTRA_NEW_PROFILE = "extra_new_profile"
    }
}
