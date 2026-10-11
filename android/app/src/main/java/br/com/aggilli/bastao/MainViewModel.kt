package br.com.aggilli.bastao

import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import br.com.aggilli.bastao.ble.BleClient
import br.com.aggilli.bastao.data.Reading
import br.com.aggilli.bastao.data.ReadingDb
import br.com.aggilli.bastao.sync.SyncConfig
import br.com.aggilli.bastao.sync.SyncWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class UiState(
    val conn: BleClient.ConnState = BleClient.ConnState.DISCONNECTED,
    val active: Boolean = false,
    val session: String = "",
    val readings: List<Reading> = emptyList(),
    val total: Int = 0,
    val lastTag: String? = null,
    val lastWasDuplicate: Boolean = false,
    val batteryPercent: Int? = null,
    val triggerMode: Int? = null,
    val firmware: String? = null,
    val wandPending: Int = 0,
    val wandDropped: Long = 0,
    val message: String? = null,
    // Sincronização com o Aggilli Agro
    val syncConfigured: Boolean = false,
    val syncPending: Int = 0,
    val lastSyncAt: Long = 0,
    val syncError: String? = null,
)

class MainViewModel(app: Application) : AndroidViewModel(app), BleClient.Listener {

    private val db = ReadingDb.get(app)
    private val prefs = app.getSharedPreferences("bastao", Context.MODE_PRIVATE)
    private val ble = BleClient(app, this)
    private val feedback = Feedback(app)
    val syncConfig = SyncConfig(app)

    private var session: String = prefs.getString("session", null) ?: createSession()
    private var conn = BleClient.ConnState.DISCONNECTED
    private var active = false
    private var lastTag: String? = null
    private var lastWasDuplicate = false
    private var batteryPercent: Int? = null
    private var triggerMode: Int? = null
    private var firmware: String? = null
    private var wandPending = 0
    private var wandDropped = 0L
    private var message: String? = null

    // Leituras recuperadas da fila do bastão: agrupadas em uma só mensagem
    private var recoveredBatch = 0
    private val handler = Handler(Looper.getMainLooper())
    private val flushRecovered = Runnable {
        if (recoveredBatch > 0) {
            message = "$recoveredBatch leitura(s) recuperada(s) do bastão."
            feedback.ok()
            recoveredBatch = 0
            publish()
        }
    }

    // Atualiza o status da sincronização (feita em segundo plano pelo WorkManager)
    private val ticker = object : Runnable {
        override fun run() {
            publish()
            handler.postDelayed(this, 5_000)
        }
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        SyncWorker.schedulePeriodic(app)
        if (db.pendingCount() > 0) SyncWorker.requestSoon(app, 2)
        publish()
        handler.postDelayed(ticker, 5_000)
    }

    // ------------------------------------------------------------ ações da UI

    fun connect() {
        active = true
        message = null
        ble.start()
        publish()
    }

    fun disconnect() {
        active = false
        ble.stop()
        publish()
    }

    fun isActive(): Boolean = active

    fun startNewSession() {
        session = createSession()
        lastTag = null
        lastWasDuplicate = false
        publish()
    }

    fun beepWand() = ble.sendControl("B")

    fun setTriggerMode(on: Boolean) = ble.sendControl(if (on) "M1" else "M0")

    fun exportCsv(): String = db.csv(session)

    fun setMessage(text: String?) {
        message = text
        publish()
    }

    fun syncNow() {
        if (!syncConfig.isConfigured) {
            message = "Configure a sincronização com o Aggilli Agro."
        } else {
            SyncWorker.requestNow(getApplication())
            message = "Sincronizando…"
        }
        publish()
    }

    fun saveSyncConfig(url: String, token: String, farmId: String, device: String, auto: Boolean) {
        syncConfig.baseUrl = url
        syncConfig.token = token
        syncConfig.farmId = farmId
        if (device.isNotBlank()) syncConfig.deviceName = device
        syncConfig.autoSync = auto
        syncConfig.lastError = null
        message = if (syncConfig.isConfigured) null else "Informe URL (https), token e fazenda."
        if (syncConfig.isConfigured && auto) SyncWorker.requestNow(getApplication())
        publish()
    }

    /** Chamado quando a tela volta ao primeiro plano: atualiza o status da sincronização. */
    fun refresh() = publish()

    // -------------------------------------------------------- BleClient.Listener

    override fun onState(state: BleClient.ConnState, detail: String?) {
        conn = state
        if (detail != null) message = detail
        if (state == BleClient.ConnState.CONNECTED) message = null
        publish()
    }

    override fun onReading(seq: Long, deviceMs: Long, tag: String, ageMs: Long) {
        val recovered = ageMs > RECOVERED_AFTER_MS
        val ts = System.currentTimeMillis() - ageMs.coerceAtLeast(0)
        val isNew = db.insert(session, tag, ts, recovered)
        ble.ack(seq)  // gravado no celular: o bastão pode tirar da fila

        if (recovered) {
            // Sem bip por leitura ao descarregar a fila: um aviso só no fim
            if (isNew) recoveredBatch++
            handler.removeCallbacks(flushRecovered)
            handler.postDelayed(flushRecovered, 800)
        } else {
            lastTag = tag
            lastWasDuplicate = !isNew
            if (isNew) feedback.ok() else feedback.duplicate()
        }
        if (isNew && syncConfig.autoSync && syncConfig.isConfigured) {
            SyncWorker.requestSoon(getApplication())
        }
        publish()
    }

    override fun onStatus(batteryMv: Int, triggerMode: Int, firmware: String, pending: Int, dropped: Long) {
        // Estimativa linear simples: 3,3 V = 0% e 4,2 V = 100%
        this.batteryPercent = ((batteryMv - 3300) * 100 / 900).coerceIn(0, 100)
        this.triggerMode = triggerMode
        this.firmware = firmware
        this.wandPending = pending
        if (dropped > wandDropped) {
            message = "Atenção: a fila do bastão encheu e ${dropped - wandDropped} leitura(s) foram perdidas."
        }
        this.wandDropped = dropped
        publish()
    }

    // ------------------------------------------------------------------ interno

    private fun createSession(): String {
        val id = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        prefs.edit().putString("session", id).apply()
        return id
    }

    private fun publish() {
        _state.value = UiState(
            conn = conn,
            active = active,
            session = session,
            readings = db.list(session),
            total = db.count(session),
            lastTag = lastTag,
            lastWasDuplicate = lastWasDuplicate,
            batteryPercent = batteryPercent,
            triggerMode = triggerMode,
            firmware = firmware,
            wandPending = wandPending,
            wandDropped = wandDropped,
            message = message,
            syncConfigured = syncConfig.isConfigured,
            syncPending = db.pendingCount(),
            lastSyncAt = syncConfig.lastSyncAt,
            syncError = syncConfig.lastError,
        )
    }

    override fun onCleared() {
        handler.removeCallbacksAndMessages(null)
        ble.stop()
        feedback.release()
        super.onCleared()
    }

    companion object {
        /** Leitura com mais de 3 s de idade veio da fila offline do bastão. */
        private const val RECOVERED_AFTER_MS = 3_000L
    }
}
