package com.androidtvopen.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var repository: SettingsRepository
    private lateinit var currentProfile: TargetProfile
    private lateinit var behavior: AppBehaviorSettings

    private lateinit var tvNetworkSubtitle: TextView
    private lateinit var tvPcTitle: TextView
    private lateinit var tvPcMac: TextView
    private lateinit var tvPcStatus: TextView
    private lateinit var tvSourceTitle: TextView
    private lateinit var tvSourceInputId: TextView
    private lateinit var tvActionLog: TextView

    private lateinit var rvProfiles: RecyclerView
    private lateinit var profileAdapter: ProfileChipAdapter

    private lateinit var cardPc: LinearLayout
    private lateinit var cardSource: LinearLayout
    private lateinit var btnSettings: Button
    private lateinit var btnAddProfile: Button
    private lateinit var btnWakeAndSwitch: Button
    private lateinit var btnWakeOnly: Button
    private lateinit var btnSwitchOnly: Button
    private lateinit var btnSleepPc: Button
    private lateinit var btnRefreshStatus: Button
    private lateinit var btnCancelAutoRun: Button

    private var autoRunJob: Job? = null
    private var wakeAndSwitchJob: Job? = null
    private var autoRunTriggeredOnce = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        repository = SettingsRepository(this)
        bindViews()
        setupProfileBar()
        setupListeners()
        btnWakeAndSwitch.requestFocus()
    }

    override fun onResume() {
        super.onResume()
        reloadState()

        if (behavior.autoRunOnLaunch && !autoRunTriggeredOnce) {
            autoRunTriggeredOnce = true
            startAutoRunCountdown()
        }
    }

    override fun onPause() {
        super.onPause()
        cancelAutoRun(silent = true)
    }

    private fun bindViews() {
        tvNetworkSubtitle = findViewById(R.id.tvNetworkSubtitle)
        tvPcTitle = findViewById(R.id.tvPcTitle)
        tvPcMac = findViewById(R.id.tvPcMac)
        tvPcStatus = findViewById(R.id.tvPcStatus)
        tvSourceTitle = findViewById(R.id.tvSourceTitle)
        tvSourceInputId = findViewById(R.id.tvSourceInputId)
        tvActionLog = findViewById(R.id.tvActionLog)

        rvProfiles = findViewById(R.id.rvProfiles)
        cardPc = findViewById(R.id.cardPc)
        cardSource = findViewById(R.id.cardSource)
        btnSettings = findViewById(R.id.btnSettings)
        btnAddProfile = findViewById(R.id.btnAddProfile)
        btnWakeAndSwitch = findViewById(R.id.btnWakeAndSwitch)
        btnWakeOnly = findViewById(R.id.btnWakeOnly)
        btnSwitchOnly = findViewById(R.id.btnSwitchOnly)
        btnSleepPc = findViewById(R.id.btnSleepPc)
        btnRefreshStatus = findViewById(R.id.btnRefreshStatus)
        btnCancelAutoRun = findViewById(R.id.btnCancelAutoRun)
    }

    private fun setupProfileBar() {
        profileAdapter = ProfileChipAdapter { selected ->
            cancelAutoRun(silent = true)
            repository.setActiveProfileId(selected.id)
            reloadState()
            tvActionLog.text = "Aktif profil değiştirildi: ${selected.pcName} → ${selected.sourceLabel}"
        }
        rvProfiles.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        rvProfiles.adapter = profileAdapter
    }

    private fun setupListeners() {
        val openCurrentSettings = View.OnClickListener {
            cancelAutoRun(silent = true)
            val intent = Intent(this, SettingsActivity::class.java).apply {
                putExtra(SettingsActivity.EXTRA_PROFILE_ID, currentProfile.id)
            }
            startActivity(intent)
        }
        btnSettings.setOnClickListener(openCurrentSettings)
        cardPc.setOnClickListener(openCurrentSettings)
        cardSource.setOnClickListener(openCurrentSettings)

        btnAddProfile.setOnClickListener {
            cancelAutoRun(silent = true)
            val intent = Intent(this, SettingsActivity::class.java).apply {
                putExtra(SettingsActivity.EXTRA_NEW_PROFILE, true)
            }
            startActivity(intent)
        }

        btnWakeAndSwitch.setOnClickListener {
            cancelAutoRun(silent = true)
            executeWakeAndSwitch()
        }
        btnWakeOnly.setOnClickListener {
            cancelAutoRun(silent = true)
            executeWakeOnly()
        }
        btnSwitchOnly.setOnClickListener {
            cancelAutoRun(silent = true)
            executeSwitchOnly()
        }
        btnSleepPc.setOnClickListener {
            cancelAutoRun(silent = true)
            executeSleepPc()
        }
        btnRefreshStatus.setOnClickListener {
            cancelAutoRun(silent = true)
            refreshStatus()
        }
        btnCancelAutoRun.setOnClickListener {
            cancelAutoRun(silent = false)
        }
    }

    private fun reloadState() {
        val profiles = repository.loadProfiles()
        currentProfile = repository.getActiveProfile()
        behavior = repository.loadBehavior()
        profileAdapter.submitList(profiles, currentProfile.id)
        renderSettings()
        refreshStatus()
    }

    private fun renderSettings() {
        tvPcTitle.text = if (currentProfile.pcIp.isNotBlank()) {
            "${currentProfile.pcName} • ${currentProfile.pcIp}"
        } else {
            currentProfile.pcName
        }
        tvPcMac.text = "MAC: ${currentProfile.pcMac}"
        tvSourceTitle.text = currentProfile.sourceLabel

        val resolvedId = TvSourceManager.resolveInputId(
            this,
            currentProfile.sourceLabel,
            currentProfile.sourceInputId
        )
        tvSourceInputId.text = when {
            currentProfile.sourceInputId.isNotBlank() -> "ID: ${currentProfile.sourceInputId}"
            resolvedId != null -> "ID (Eşleşti): $resolvedId"
            else -> "ID: Otomatik Eşleşme (${currentProfile.sourceLabel})"
        }
    }

    private fun refreshStatus() {
        val netInfo = NetworkScanner.getLocalNetworkInfo()
        tvNetworkSubtitle.text = if (netInfo != null) {
            "Yerel Ağ: ${netInfo.localIp} (${netInfo.interfaceName})"
        } else {
            "Yerel ağ bağlantısı bulunamadı"
        }

        if (currentProfile.pcIp.isBlank()) {
            tvPcStatus.text = "● IP adresi tanımlı değil"
            tvPcStatus.setTextColor(ContextCompat.getColor(this, R.color.tv_text_secondary))
            return
        }

        tvPcStatus.text = "● Durum kontrol ediliyor..."
        tvPcStatus.setTextColor(ContextCompat.getColor(this, R.color.tv_warning))

        lifecycleScope.launch {
            val (online, ms) = NetworkScanner.checkHostOnline(currentProfile.pcIp)
            if (online) {
                tvPcStatus.text = "● Çevrimiçi (${ms} ms)"
                tvPcStatus.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.tv_success))
            } else {
                tvPcStatus.text = "● Kapalı / Uyku Modunda"
                tvPcStatus.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.tv_error))
            }
        }
    }

    private fun startAutoRunCountdown() {
        autoRunJob?.cancel()
        btnCancelAutoRun.text = "Otomatik Çalıştırmayı İptal Et"
        btnCancelAutoRun.visibility = View.VISIBLE
        autoRunJob = lifecycleScope.launch {
            for (sec in 3 downTo 1) {
                tvActionLog.text = "Otomatik başlatma: $sec saniye içinde '${currentProfile.pcName}' uyandırılacak ve '${currentProfile.sourceLabel}' kaynağına geçilecek..."
                delay(1000)
            }
            btnCancelAutoRun.visibility = View.GONE
            executeWakeAndSwitch()
        }
    }

    private fun cancelAutoRun(silent: Boolean) {
        var cancelled = false
        if (autoRunJob?.isActive == true) {
            autoRunJob?.cancel()
            cancelled = true
        }
        if (wakeAndSwitchJob?.isActive == true) {
            wakeAndSwitchJob?.cancel()
            setActionButtonsEnabled(true)
            cancelled = true
        }
        btnCancelAutoRun.visibility = View.GONE
        btnCancelAutoRun.text = "Otomatik Çalıştırmayı İptal Et"
        if (cancelled && !silent) {
            tvActionLog.text = "İşlem iptal edildi."
        }
    }

    private fun executeWakeAndSwitch() {
        wakeAndSwitchJob?.cancel()
        setActionButtonsEnabled(false)
        tvActionLog.text = "1/2: ${currentProfile.pcMac} adresine Wake-on-LAN paketi gönderiliyor..."

        wakeAndSwitchJob = lifecycleScope.launch {
            val netInfo = NetworkScanner.getLocalNetworkInfo()
            val broadcast = netInfo?.broadcastIp ?: "255.255.255.255"
            val wolResult = WakeOnLanSender.sendWakeOnLan(
                macAddress = currentProfile.pcMac,
                ipAddress = currentProfile.pcIp,
                broadcastIp = broadcast,
                port = currentProfile.wolPort
            )

            val logBuilder = StringBuilder()
            wolResult.onSuccess { msg ->
                logBuilder.append("✓ ").append(msg)
            }.onFailure { err ->
                logBuilder.append("✗ WoL Hatası: ").append(err.message)
                tvActionLog.text = logBuilder.toString()
                setActionButtonsEnabled(true)
                return@launch
            }

            if (behavior.waitForPcOnline && currentProfile.pcIp.isNotBlank()) {
                btnCancelAutoRun.text = "Beklemeyi İptal Et"
                btnCancelAutoRun.visibility = View.VISIBLE
                val timeoutSec = behavior.waitTimeoutSeconds.coerceIn(5, 180)
                val startMs = System.currentTimeMillis()
                var lastRetryMs = startMs
                var retryCount = 0
                var pcOnline = false

                while (true) {
                    val nowMs = System.currentTimeMillis()
                    val elapsedSec = ((nowMs - startMs) / 1000L).toInt()

                    if (behavior.wolRetryDuringWait && nowMs - lastRetryMs >= 5000L) {
                        lastRetryMs = nowMs
                        retryCount++
                        WakeOnLanSender.sendWakeOnLan(
                            macAddress = currentProfile.pcMac,
                            ipAddress = currentProfile.pcIp,
                            broadcastIp = broadcast,
                            port = currentProfile.wolPort
                        )
                    }

                    val retryHint = if (retryCount > 0) " • Tekrar WoL #$retryCount" else ""
                    tvActionLog.text = "${logBuilder}\n2/2: ${currentProfile.pcIp} açılması bekleniyor... (${elapsedSec}s / ${timeoutSec}s$retryHint)"

                    val (online, ms) = NetworkScanner.checkHostOnline(currentProfile.pcIp, 650)
                    if (online) {
                        pcOnline = true
                        tvPcStatus.text = "● Çevrimiçi (${ms} ms)"
                        tvPcStatus.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.tv_success))
                        logBuilder.append("\n✓ PC çevrimiçi oldu (${ms} ms).")
                        break
                    }
                    if (nowMs - startMs >= timeoutSec * 1000L) {
                        break
                    }
                    delay(1000)
                }

                btnCancelAutoRun.visibility = View.GONE
                btnCancelAutoRun.text = "Otomatik Çalıştırmayı İptal Et"

                if (!pcOnline) {
                    tvActionLog.text = "${logBuilder}\n✗ PC $timeoutSec sn içinde yanıt vermedi; '${currentProfile.sourceLabel}' kaynağına geçilmedi."
                    setActionButtonsEnabled(true)
                    refreshStatus()
                    return@launch
                }
            } else if (behavior.switchDelaySeconds > 0) {
                logBuilder.append("\n2/2: ${behavior.switchDelaySeconds} sn sonra '${currentProfile.sourceLabel}' kaynağına geçiliyor...")
                tvActionLog.text = logBuilder.toString()
                delay(behavior.switchDelaySeconds * 1000L)
            }

            val switchResult = TvSourceManager.switchToSource(
                this@MainActivity,
                currentProfile.sourceLabel,
                currentProfile.sourceInputId
            )

            switchResult.onSuccess { msg ->
                tvActionLog.text = "${logBuilder}\n✓ $msg"
            }.onFailure { err ->
                tvActionLog.text = "${logBuilder}\n✗ Kaynak Hatası: ${err.message}"
            }

            setActionButtonsEnabled(true)
            refreshStatus()
        }
    }

    private fun executeWakeOnly() {
        setActionButtonsEnabled(false)
        tvActionLog.text = "${currentProfile.pcMac} adresine Wake-on-LAN paketi gönderiliyor..."

        lifecycleScope.launch {
            val netInfo = NetworkScanner.getLocalNetworkInfo()
            val broadcast = netInfo?.broadcastIp ?: "255.255.255.255"
            val wolResult = WakeOnLanSender.sendWakeOnLan(
                macAddress = currentProfile.pcMac,
                ipAddress = currentProfile.pcIp,
                broadcastIp = broadcast,
                port = currentProfile.wolPort
            )

            wolResult.onSuccess { msg ->
                tvActionLog.text = "✓ $msg"
            }.onFailure { err ->
                tvActionLog.text = "✗ WoL Hatası: ${err.message}"
            }

            setActionButtonsEnabled(true)
            delay(1500)
            refreshStatus()
        }
    }

    private fun executeSwitchOnly() {
        val switchResult = TvSourceManager.switchToSource(
            this,
            currentProfile.sourceLabel,
            currentProfile.sourceInputId
        )
        switchResult.onSuccess { msg ->
            tvActionLog.text = "✓ $msg"
        }.onFailure { err ->
            tvActionLog.text = "✗ Kaynak Hatası: ${err.message}"
        }
    }

    private fun executeSleepPc() {
        if (currentProfile.sleepUrl.isBlank()) {
            tvActionLog.text = "ℹ '${currentProfile.pcName}' için Uzak Uyku/Kapatma URL'si tanımlı değil.\nAyarlar ekranından (örn: http://${currentProfile.pcIp}:5050/sleep) ekleyebilirsiniz."
            return
        }
        setActionButtonsEnabled(false)
        tvActionLog.text = "${currentProfile.pcName} uyku moduna alınıyor..."

        lifecycleScope.launch {
            val result = RemotePowerClient.sendSleepCommand(currentProfile.sleepUrl)
            result.onSuccess { msg ->
                tvActionLog.text = "✓ $msg"
            }.onFailure { err ->
                tvActionLog.text = "✗ Uyku/Kapatma Hatası: ${err.message}"
            }
            setActionButtonsEnabled(true)
            delay(1500)
            refreshStatus()
        }
    }

    private fun setActionButtonsEnabled(enabled: Boolean) {
        btnWakeAndSwitch.isEnabled = enabled
        btnWakeOnly.isEnabled = enabled
        btnSwitchOnly.isEnabled = enabled
        btnSleepPc.isEnabled = enabled
    }
}
