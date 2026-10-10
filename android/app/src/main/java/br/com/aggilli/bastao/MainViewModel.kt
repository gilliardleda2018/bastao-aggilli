package br.com.aggilli.bastao

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import br.com.aggilli.bastao.ble.BleClient
import br.com.aggilli.bastao.data.Reading
import br.com.aggilli.bastao.data.ReadingDb
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
    val message: String? = null,
)

class MainViewModel(app: Application) : AndroidViewModel(app), BleClient.Listener {

    private val db = ReadingDb(app)
    private val prefs = app.getSharedPreferences("bastao", Context.MODE_PRIVATE)
    private val ble = BleClient(app, this)
    private val feedback = Feedback(app)

    private var session: String = prefs.getString("session", null) ?: createSession()
    private var conn = BleClient.ConnState.DISCONNECTED
    private var active = false
    private var lastTag: String? = null
    private var lastWasDuplicate = false
    private var batteryPercent: Int? = null
    private var triggerMode: Int? = null
    private var firmware: String? = null
    private var message: String? = null

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        publish()
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

    // -------------------------------------------------------- BleClient.Listener

    override fun onState(state: BleClient.ConnState, detail: String?) {
        conn = state
        if (detail != null) message = detail
        if (state == BleClient.ConnState.CONNECTED) message = null
        publish()
    }

    override fun onReading(seq: Long, deviceMs: Long, tag: String) {
        val isNew = db.insert(session, tag, System.currentTimeMillis())
        lastTag = tag
        lastWasDuplicate = !isNew
        if (isNew) feedback.ok() else feedback.duplicate()
        publish()
    }

    override fun onStatus(batteryMv: Int, triggerMode: Int, firmware: String) {
        // Estimativa linear simples: 3,3 V = 0% e 4,2 V = 100%
        this.batteryPercent = ((batteryMv - 3300) * 100 / 900).coerceIn(0, 100)
        this.triggerMode = triggerMode
        this.firmware = firmware
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
            message = message,
        )
    }

    override fun onCleared() {
        ble.stop()
        feedback.release()
        db.close()
        super.onCleared()
    }
}
