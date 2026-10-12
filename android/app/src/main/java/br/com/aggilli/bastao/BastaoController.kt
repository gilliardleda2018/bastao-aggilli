package br.com.aggilli.bastao

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import br.com.aggilli.bastao.ble.BleClient
import br.com.aggilli.bastao.ble.FoundWand
import br.com.aggilli.bastao.ble.WandStatus
import br.com.aggilli.bastao.data.Reading
import br.com.aggilli.bastao.data.ReadingDb
import br.com.aggilli.bastao.leitor.EidParser
import br.com.aggilli.bastao.leitor.SppClient
import br.com.aggilli.bastao.sync.SyncConfig
import br.com.aggilli.bastao.sync.SyncWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class UiState(
    val conn: BleClient.ConnState = BleClient.ConnState.DISCONNECTED,
    val active: Boolean = false,
    val session: String = "",
    val readings: List<Reading> = emptyList(),
    val total: Int = 0,
    val lastTag: String? = null,
    val lastWasDuplicate: Boolean = false,
    // bastão: [WAND_AGGILLI] (BLE, com fila, bateria e bip) ou [WAND_SPP] (comercial, só o número)
    val wandType: String = WAND_AGGILLI,
    val paired: List<FoundWand> = emptyList(),
    val wandName: String? = null,
    val wandAddress: String? = null,
    val batteryPercent: Int? = null,
    val triggerMode: Int? = null,
    val firmware: String? = null,
    val wandPending: Int = 0,
    val wandDropped: Long = 0,
    val sdState: Int? = null,
    val discovered: List<FoundWand> = emptyList(),
    val discovering: Boolean = false,
    val voice: Boolean = false,
    val message: String? = null,
    // sincronização com o Aggilli Agro
    val syncConfigured: Boolean = false,
    val syncPending: Int = 0,
    val lastSyncAt: Long = 0,
    val syncError: String? = null,
)

/** Bastão Aggilli: BLE com o protocolo próprio (docs/04). */
const val WAND_AGGILLI = "aggilli"

/** Bastão comercial (Allflex, Datamars, Gallagher…): Bluetooth clássico, perfil serial. */
const val WAND_SPP = "spp"

/**
 * Coração do app: conexão com o bastão, sessão de leituras, avisos e sincronização.
 * É um objeto único do processo (não da tela): continua coletando com a tela apagada,
 * mantido vivo pelo [BleForegroundService] enquanto a conexão estiver ativa.
 * Todos os métodos rodam na thread principal.
 */
@SuppressLint("MissingPermission")  // a tela pede as permissões de Bluetooth antes de conectar ou listar
class BastaoController private constructor(private val app: Context) : BleClient.Listener, SppClient.Listener {

    private val db = ReadingDb.get(app)
    private val prefs = app.getSharedPreferences("bastao", Context.MODE_PRIVATE)
    private val ble = BleClient(app, this)
    private val spp = SppClient(this)
    private val adapter: BluetoothAdapter? =
        (app.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    private val feedback = Feedback(app)
    private val speaker = Speaker(app)
    private val main = Handler(Looper.getMainLooper())
    val syncConfig = SyncConfig(app)

    private var session: String = prefs.getString("session", null) ?: createSession()
    private var conn = BleClient.ConnState.DISCONNECTED
    private var active = false
    private var lastTag: String? = null
    private var lastWasDuplicate = false
    private var status: WandStatus? = null
    private var wandDropped = 0L
    private var discovered: List<FoundWand> = emptyList()
    private var discovering = false
    private var paired: List<FoundWand> = emptyList()
    private var message: String? = null

    /** Bastão comercial com o brinco parado perto da antena repete o número: ignora por 3 s. */
    private var ultimoExterno: String? = null
    private var ultimoExternoEm = 0L

    private var recoveredBatch = 0
    private val flushRecovered = Runnable {
        if (recoveredBatch > 0) {
            message = "$recoveredBatch leitura(s) recuperada(s) do bastão."
            feedback.ok()
            speaker.announce("", false, recoveredBatch)
            recoveredBatch = 0
            publish()
        }
    }

    private val ticker = object : Runnable {
        override fun run() {
            publish()
            main.postDelayed(this, 5_000)
        }
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        speaker.enabled = prefs.getBoolean("voice", false)
        SyncWorker.schedulePeriodic(app)
        if (db.pendingCount() > 0) SyncWorker.requestSoon(app, 2)
        publish()
        main.postDelayed(ticker, 5_000)
    }

    // ------------------------------------------------------------ ações

    fun connect() {
        val address = prefs.getString("wand_address", null)
        if (wandType() == WAND_SPP) {
            val bt = adapter
            if (address == null || bt == null || !bt.isEnabled) {
                message = if (bt?.isEnabled == true) "Escolha o bastão na lista de pareados." else "Ligue o Bluetooth do celular."
                publish()
                return
            }
            active = true
            message = null
            stopDiscovery()
            spp.start(bt, address)
        } else {
            active = true
            message = null
            stopDiscovery()
            ble.start(address)
        }
        ContextCompat.startForegroundService(app, Intent(app, BleForegroundService::class.java))
        publish()
    }

    fun disconnect() {
        active = false
        ble.stop()
        spp.stop()
        app.stopService(Intent(app, BleForegroundService::class.java))
        publish()
    }

    fun isActive(): Boolean = active

    fun startDiscovery() {
        discovering = true
        paired = pairedDevices()
        ble.startDiscovery()
        main.postDelayed({ discovering = false; publish() }, 8_000)
        publish()
    }

    /** Aparelhos já pareados no Android: é assim que os bastões comerciais aparecem. */
    private fun pairedDevices(): List<FoundWand> = try {
        adapter?.bondedDevices.orEmpty()
            .map { FoundWand(it.name ?: it.address, it.address, 0) }
            .sortedBy { it.name.lowercase() }
    } catch (e: SecurityException) {
        emptyList()
    }

    private fun wandType(): String = prefs.getString("wand_type", WAND_AGGILLI) ?: WAND_AGGILLI

    /** O usuário escolheu um bastão comercial já pareado: lembra dele e conecta pelo SPP. */
    fun selectPaired(w: FoundWand) {
        prefs.edit().putString("wand_address", w.address).putString("wand_name", w.name)
            .putString("wand_type", WAND_SPP).apply()
        if (active) disconnect()
        connect()
    }

    /** Número digitado, colado ou "digitado" por um bastão no modo teclado (HID). */
    fun manualEntry(text: String): Boolean {
        val tag = EidParser.extrair(text)
        if (tag == null) {
            message = "Não achei um número de brinco de 15 dígitos em \"${text.trim().take(30)}\"."
            publish()
            return false
        }
        registrarAoVivo(tag)
        return true
    }

    fun stopDiscovery() {
        ble.stopDiscovery()
        discovering = false
        publish()
    }

    /** O usuário escolheu um bastão na lista: lembra dele e conecta. */
    fun selectWand(w: FoundWand) {
        prefs.edit().putString("wand_address", w.address).putString("wand_name", w.name)
            .putString("wand_type", WAND_AGGILLI).apply()
        if (active) disconnect()
        connect()
    }

    fun forgetWand() {
        if (active) disconnect()
        prefs.edit().remove("wand_address").remove("wand_name").remove("wand_type").apply()
        publish()
    }

    fun startNewSession() {
        session = createSession()
        lastTag = null
        lastWasDuplicate = false
        ble.sendControl("C;0")
        publish()
    }

    fun beepWand() = ble.sendControl("B")

    fun setTriggerMode(on: Boolean) = ble.sendControl(if (on) "M1" else "M0")

    fun setVoice(on: Boolean) {
        speaker.enabled = on
        prefs.edit().putBoolean("voice", on).apply()
        publish()
    }

    fun exportCsv(): String = db.csv(session)
    fun sessionId(): String = session

    fun setMessage(text: String?) {
        message = text
        publish()
    }

    fun syncNow() {
        if (!syncConfig.isConfigured) {
            message = "Configure a sincronização com o Aggilli Agro."
        } else {
            SyncWorker.requestNow(app)
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
        if (syncConfig.isConfigured && auto) SyncWorker.requestNow(app)
        publish()
    }

    fun refresh() = publish()

    // -------------------------------------------------------- BleClient.Listener

    override fun onState(state: BleClient.ConnState, detail: String?) {
        conn = state
        if (detail != null) message = detail
        if (state == BleClient.ConnState.CONNECTED) message = null
        publish()
    }

    override fun onReady() {
        // Hora e fuso do celular: o bastão data as leituras e nomeia os arquivos do cartão SD
        val now = System.currentTimeMillis()
        val tzMin = TimeZone.getDefault().getOffset(now) / 60_000
        ble.sendControl("T;${now / 1000};$tzMin")
        ble.sendControl("C;${db.count(session)}")
    }

    override fun onReading(seq: Long, deviceMs: Long, tag: String, ageMs: Long, epochS: Long) {
        val now = System.currentTimeMillis()
        val ts = when {
            epochS > 0 -> epochS * 1000
            ageMs >= 0 -> now - ageMs
            else -> now  // leitura de uma ligação anterior sem hora: usa a hora de chegada
        }
        val recovered = ageMs < 0 || ageMs > RECOVERED_AFTER_MS
        val isNew = db.insert(session, tag, ts, recovered)
        ble.ack(seq)  // gravado no celular: o bastão pode tirar da fila

        if (recovered) {
            if (isNew) recoveredBatch++
            main.removeCallbacks(flushRecovered)
            main.postDelayed(flushRecovered, 800)
        } else {
            lastTag = tag
            lastWasDuplicate = !isNew
            if (isNew) feedback.ok() else feedback.duplicate()
            if (!isNew) ble.sendControl("D")  // bastão dá o bip de repetido e mostra no display
            speaker.announce(tag, !isNew)
        }
        ble.sendControl("C;${db.count(session)}")
        if (isNew && syncConfig.autoSync && syncConfig.isConfigured) SyncWorker.requestSoon(app)
        publish()
    }

    // -------------------------------------------------------- SppClient.Listener

    override fun onSppState(state: BleClient.ConnState, detail: String?) = onState(state, detail)

    override fun onSppTag(tag: String) = registrarAoVivo(tag)

    /** Leitura que chega na hora (bastão comercial ou digitada): grava, avisa e sincroniza. */
    private fun registrarAoVivo(tag: String) {
        val now = System.currentTimeMillis()
        if (tag == ultimoExterno && now - ultimoExternoEm < REPETICAO_MS) return
        ultimoExterno = tag
        ultimoExternoEm = now

        val isNew = db.insert(session, tag, now, false)
        lastTag = tag
        lastWasDuplicate = !isNew
        message = null
        if (isNew) feedback.ok() else feedback.duplicate()
        speaker.announce(tag, !isNew)
        if (isNew && syncConfig.autoSync && syncConfig.isConfigured) SyncWorker.requestSoon(app)
        publish()
    }

    override fun onStatus(status: WandStatus) {
        if (status.dropped > wandDropped) {
            message = "Atenção: a fila do bastão encheu e ${status.dropped - wandDropped} leitura(s) foram perdidas."
        }
        if (status.sd == 2) message = "Cartão de memória do bastão com erro ou cheio."
        wandDropped = status.dropped
        this.status = status
        publish()
    }

    override fun onFound(devices: List<FoundWand>) {
        discovered = devices
        publish()
    }

    // ------------------------------------------------------------------ interno

    private fun createSession(): String {
        val id = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        prefs.edit().putString("session", id).apply()
        return id
    }

    private fun batteryPercent(s: WandStatus?): Int? {
        if (s == null) return null
        return s.batteryPct ?: ((s.batteryMv - 3300) * 100 / 900).coerceIn(0, 100)
    }

    private fun publish() {
        val s = status
        _state.value = UiState(
            conn = conn,
            active = active,
            session = session,
            readings = db.list(session),
            total = db.count(session),
            lastTag = lastTag,
            lastWasDuplicate = lastWasDuplicate,
            wandType = wandType(),
            paired = paired,
            wandName = prefs.getString("wand_name", null),
            wandAddress = prefs.getString("wand_address", null),
            batteryPercent = batteryPercent(s),
            triggerMode = s?.triggerMode,
            firmware = s?.firmware,
            wandPending = s?.pending ?: 0,
            wandDropped = wandDropped,
            sdState = s?.sd,
            discovered = discovered,
            discovering = discovering,
            voice = speaker.enabled,
            message = message,
            syncConfigured = syncConfig.isConfigured,
            syncPending = db.pendingCount(),
            lastSyncAt = syncConfig.lastSyncAt,
            syncError = syncConfig.lastError,
        )
    }

    companion object {
        /** Leitura com mais de 3 s de idade veio da fila offline do bastão. */
        private const val RECOVERED_AFTER_MS = 3_000L

        /** Mesmo número repetido por um bastão comercial dentro deste intervalo é a mesma leitura. */
        private const val REPETICAO_MS = 3_000L

        @Volatile private var instance: BastaoController? = null

        fun get(context: Context): BastaoController =
            instance ?: synchronized(this) {
                instance ?: BastaoController(context.applicationContext).also { instance = it }
            }
    }
}
