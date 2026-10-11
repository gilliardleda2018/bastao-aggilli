package br.com.aggilli.bastao.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid

/** Status periódico do bastão (característica STATUS). Campos novos são nulos em firmware antigo. */
data class WandStatus(
    val batteryMv: Int,
    val triggerMode: Int,
    val firmware: String,
    val pending: Int,
    val dropped: Long,
    val batteryPct: Int?,
    val sd: Int?,      // 0 sem cartão, 1 ok, 2 erro
    val boot: Int?,
)

/** Bastão encontrado na busca. */
data class FoundWand(val name: String, val address: String, val rssi: Int)

/**
 * Cliente BLE do bastão: busca, conecta, assina as notificações e reconecta sozinho
 * enquanto [start] estiver ativo. Todos os callbacks do [Listener] chegam na thread principal.
 * As permissões de Bluetooth devem ser pedidas pela Activity antes de usar.
 */
@SuppressLint("MissingPermission")
class BleClient(private val context: Context, private val listener: Listener) {

    enum class ConnState { DISCONNECTED, SCANNING, CONNECTING, CONNECTED }

    interface Listener {
        fun onState(state: ConnState, detail: String?)
        /** epochS = data/hora UNIX da leitura (0 = desconhecida); ageMs = -1 se de outra ligação. */
        fun onReading(seq: Long, deviceMs: Long, tag: String, ageMs: Long, epochS: Long)
        fun onStatus(status: WandStatus)
        fun onReady()
        fun onFound(devices: List<FoundWand>)
    }

    private val main = Handler(Looper.getMainLooper())
    private val adapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

    private var gatt: BluetoothGatt? = null
    private var control: BluetoothGattCharacteristic? = null
    private var wantConnected = false
    private var target: String? = null        // endereço escolhido pelo usuário (null = o primeiro que achar)
    private var failures = 0
    private var scanning = false
    private var scanTimeout: Runnable? = null
    private val notifyQueue = ArrayDeque<BluetoothGattCharacteristic>()

    // Escritas no CONTROL: o Android só aceita uma operação GATT por vez, então enfileiramos.
    private val writeQueue = ArrayDeque<String>()
    private var writeBusy = false
    private var pendingAck: Long? = null

    // Busca de bastões para o usuário escolher
    private var discovering = false
    private val found = LinkedHashMap<String, FoundWand>()

    fun isEnabled(): Boolean = adapter?.isEnabled == true

    // ------------------------------------------------------------------ conexão

    fun start(address: String?) {
        if (adapter == null || !adapter.isEnabled) {
            post(ConnState.DISCONNECTED, "Bluetooth desligado.")
            return
        }
        target = address
        wantConnected = true
        failures = 0
        connectOrScan()
    }

    fun stop() {
        wantConnected = false
        main.removeCallbacksAndMessages(RETRY_TOKEN)
        stopScan()
        val g = gatt
        if (g != null) g.disconnect() else post(ConnState.DISCONNECTED, null)
    }

    /** Envia um comando ao bastão (ver docs/04-protocolo-ble.md). */
    fun sendControl(command: String) {
        main.post {
            writeQueue.addLast(command)
            pumpWrites()
        }
    }

    /** Confirma ao bastão as leituras até [seq]. ACKs seguidos são agrupados. */
    fun ack(seq: Long) {
        main.post {
            val cur = pendingAck
            pendingAck = if (cur == null || seq > cur) seq else cur
            pumpWrites()
        }
    }

    // ------------------------------------------------------------------ busca

    fun startDiscovery() {
        val scanner = adapter?.bluetoothLeScanner ?: return
        if (discovering) return
        found.clear()
        discovering = true
        listener.onFound(emptyList())
        scanner.startScan(listOf(serviceFilter()), lowLatency(), discoveryCallback)
        main.postDelayed({ stopDiscovery() }, DISCOVERY_MS)
    }

    fun stopDiscovery() {
        if (!discovering) return
        discovering = false
        try {
            adapter?.bluetoothLeScanner?.stopScan(discoveryCallback)
        } catch (_: Exception) {
        }
    }

    private val discoveryCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val name = result.scanRecord?.deviceName ?: result.device.name ?: "Bastão sem nome"
            found[result.device.address] = FoundWand(name, result.device.address, result.rssi)
            val list = found.values.sortedByDescending { it.rssi }
            main.post { listener.onFound(list) }
        }
    }

    // ------------------------------------------------------- conexão / scan

    private fun connectOrScan() {
        if (!wantConnected || gatt != null || scanning) return
        val addr = target
        if (addr != null) {
            val device = try {
                adapter?.getRemoteDevice(addr)
            } catch (e: IllegalArgumentException) {
                null
            }
            if (device != null) {
                connect(device)
                return
            }
        }
        startScan()
    }

    private fun startScan() {
        val scanner = adapter?.bluetoothLeScanner
        if (scanner == null) {
            post(ConnState.DISCONNECTED, "Scanner BLE indisponível.")
            return
        }
        scanning = true
        post(ConnState.SCANNING, null)
        scanner.startScan(listOf(serviceFilter()), lowLatency(), scanCallback)
        val timeout = Runnable {
            if (scanning) {
                stopScan()
                post(ConnState.DISCONNECTED, "Bastão não encontrado. Ligue o bastão.")
                scheduleRetry(3000)
            }
        }
        scanTimeout = timeout
        main.postDelayed(timeout, 15_000)
    }

    private fun stopScan() {
        scanTimeout?.let { main.removeCallbacks(it) }
        scanTimeout = null
        if (scanning) {
            scanning = false
            try {
                adapter?.bluetoothLeScanner?.stopScan(scanCallback)
            } catch (_: Exception) {
            }
        }
    }

    private fun scheduleRetry(delayMs: Long) {
        if (!wantConnected) return
        main.postAtTime({ connectOrScan() }, RETRY_TOKEN, android.os.SystemClock.uptimeMillis() + delayMs)
    }

    private fun connect(device: BluetoothDevice) {
        post(ConnState.CONNECTING, null)
        gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            if (!scanning) return
            stopScan()
            main.post { connect(result.device) }
        }

        override fun onScanFailed(errorCode: Int) {
            scanning = false
            post(ConnState.DISCONNECTED, "Falha na busca Bluetooth (código $errorCode).")
            scheduleRetry(3000)
        }
    }

    private fun serviceFilter(): ScanFilter =
        ScanFilter.Builder().setServiceUuid(ParcelUuid(BleUuids.SERVICE)).build()

    private fun lowLatency(): ScanSettings =
        ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()

    // ----------------------------------------------------------------- GATT

    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                failures = 0
                if (!g.requestMtu(185)) g.discoverServices()
                return
            }
            g.close()
            main.post {
                if (gatt !== g) return@post
                gatt = null
                control = null
                notifyQueue.clear()
                resetWrites()
                post(ConnState.DISCONNECTED, if (wantConnected) "Conexão perdida. Reconectando…" else null)
                if (wantConnected) {
                    failures++
                    scheduleRetry(if (failures < 5) 2000 else 5000)
                }
            }
        }

        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
            g.discoverServices()
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val service = g.getService(BleUuids.SERVICE)
            val reading = service?.getCharacteristic(BleUuids.READING)
            if (status != BluetoothGatt.GATT_SUCCESS || service == null || reading == null) {
                post(ConnState.DISCONNECTED, "Este aparelho não é um bastão Aggilli.")
                g.disconnect()
                return
            }
            main.post {
                control = service.getCharacteristic(BleUuids.CONTROL)
                notifyQueue.clear()
                notifyQueue.add(reading)
                service.getCharacteristic(BleUuids.STATUS)?.let { notifyQueue.add(it) }
                enableNext(g)
            }
        }

        override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            main.post { enableNext(g) }
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, ch: BluetoothGattCharacteristic, status: Int) {
            main.post {
                writeBusy = false
                pumpWrites()
            }
        }

        @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
        override fun onCharacteristicChanged(g: BluetoothGatt, ch: BluetoothGattCharacteristic) {
            val text = String(ch.value ?: return, Charsets.UTF_8)
            when (ch.uuid) {
                BleUuids.READING -> parseReading(text)
                BleUuids.STATUS -> parseStatus(text)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun enableNotifications(g: BluetoothGatt, ch: BluetoothGattCharacteristic): Boolean {
        g.setCharacteristicNotification(ch, true)
        val descriptor = ch.getDescriptor(BleUuids.CCCD) ?: return false
        descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        return g.writeDescriptor(descriptor)
    }

    /** Habilita uma notificação por vez (o Android exige escritas de descritor em sequência). */
    private fun enableNext(g: BluetoothGatt) {
        val next = notifyQueue.removeFirstOrNull()
        if (next == null) {
            resetWrites()
            listener.onState(ConnState.CONNECTED, null)
            sendControl("H")          // bastão (re)envia a fila
            listener.onReady()        // o app manda a hora e a contagem
            return
        }
        if (!enableNotifications(g, next)) enableNext(g)
    }

    // ---------------------------------------------------------------- escrita

    @Suppress("DEPRECATION")
    private fun pumpWrites() {
        if (writeBusy) return
        val g = gatt ?: return
        val ch = control ?: return
        val cmd = writeQueue.removeFirstOrNull()
            ?: pendingAck?.let { pendingAck = null; "A;$it" }
            ?: return
        ch.value = cmd.toByteArray(Charsets.UTF_8)
        ch.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        writeBusy = g.writeCharacteristic(ch)
        if (!writeBusy) {
            if (cmd.startsWith("A;")) {
                val seq = cmd.substring(2).toLong()
                val cur = pendingAck
                pendingAck = if (cur == null || seq > cur) seq else cur
            } else {
                writeQueue.addFirst(cmd)
            }
            main.postDelayed({ pumpWrites() }, 50)
        }
    }

    private fun resetWrites() {
        writeQueue.clear()
        writeBusy = false
        pendingAck = null
    }

    // -------------------------------------------------------------- parsing

    /** R;<seq>;<uptime_ms>;<id>[;<idade_ms>[;<epoch>]] */
    private fun parseReading(text: String) {
        val p = text.trim().split(";")
        if (p.size < 4 || p[0] != "R") return
        val seq = p[1].toLongOrNull() ?: return
        val ms = p[2].toLongOrNull() ?: return
        val tag = p[3]
        if (tag.isEmpty()) return
        val age = p.getOrNull(4)?.toLongOrNull() ?: 0L
        val epoch = p.getOrNull(5)?.toLongOrNull() ?: 0L
        main.post { listener.onReading(seq, ms, tag, age, epoch) }
    }

    /** S;<mV>;<modo>;<fw>[;<pendentes>;<descartadas>[;<bateria_%>;<sd>;<ligação>]] */
    private fun parseStatus(text: String) {
        val p = text.trim().split(";")
        if (p.size < 4 || p[0] != "S") return
        val st = WandStatus(
            batteryMv = p[1].toIntOrNull() ?: return,
            triggerMode = p[2].toIntOrNull() ?: return,
            firmware = p[3],
            pending = p.getOrNull(4)?.toIntOrNull() ?: 0,
            dropped = p.getOrNull(5)?.toLongOrNull() ?: 0L,
            batteryPct = p.getOrNull(6)?.toIntOrNull(),
            sd = p.getOrNull(7)?.toIntOrNull(),
            boot = p.getOrNull(8)?.toIntOrNull(),
        )
        main.post { listener.onStatus(st) }
    }

    private fun post(state: ConnState, detail: String?) {
        main.post { listener.onState(state, detail) }
    }

    companion object {
        private val RETRY_TOKEN = Any()
        private const val DISCOVERY_MS = 8_000L
    }
}
