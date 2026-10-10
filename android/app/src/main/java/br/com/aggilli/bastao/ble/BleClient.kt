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

/**
 * Cliente BLE do bastão: procura o serviço, conecta, assina as notificações
 * e reconecta sozinho enquanto [start] estiver ativo.
 * As permissões de Bluetooth devem ser pedidas pela Activity antes de [start].
 */
@SuppressLint("MissingPermission")
class BleClient(private val context: Context, private val listener: Listener) {

    enum class ConnState { DISCONNECTED, SCANNING, CONNECTING, CONNECTED }

    interface Listener {
        fun onState(state: ConnState, detail: String?)
        fun onReading(seq: Long, deviceMs: Long, tag: String)
        fun onStatus(batteryMv: Int, triggerMode: Int, firmware: String)
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val timerHandler = Handler(Looper.getMainLooper())

    private val adapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

    private var gatt: BluetoothGatt? = null
    private var control: BluetoothGattCharacteristic? = null
    private var wantConnected = false
    private var lastAddress: String? = null
    private var failures = 0
    private var scanning = false
    private var scanTimeout: Runnable? = null
    private val notifyQueue = ArrayDeque<BluetoothGattCharacteristic>()

    // ------------------------------------------------------------------ API

    fun start() {
        if (adapter == null || !adapter.isEnabled) {
            post(ConnState.DISCONNECTED, "Bluetooth desligado.")
            return
        }
        wantConnected = true
        connectOrScan()
    }

    fun stop() {
        wantConnected = false
        timerHandler.removeCallbacksAndMessages(null)
        stopScan()
        val g = gatt
        if (g != null) g.disconnect() else post(ConnState.DISCONNECTED, null)
    }

    /** Envia um comando ao bastão: "B" (bip), "M0" (modo contínuo), "M1" (modo gatilho). */
    @Suppress("DEPRECATION")
    fun sendControl(command: String) {
        val g = gatt ?: return
        val ch = control ?: return
        ch.value = command.toByteArray(Charsets.UTF_8)
        ch.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        g.writeCharacteristic(ch)
    }

    // ------------------------------------------------------- conexão / scan

    private fun connectOrScan() {
        if (!wantConnected || gatt != null || scanning) return
        val addr = lastAddress
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
        val filter = ScanFilter.Builder().setServiceUuid(ParcelUuid(BleUuids.SERVICE)).build()
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        scanning = true
        post(ConnState.SCANNING, null)
        scanner.startScan(listOf(filter), settings, scanCallback)

        val timeout = Runnable {
            if (scanning) {
                stopScan()
                post(ConnState.DISCONNECTED, "Bastão não encontrado.")
                scheduleRetry(3000)
            }
        }
        scanTimeout = timeout
        timerHandler.postDelayed(timeout, 15_000)
    }

    private fun stopScan() {
        scanTimeout?.let { timerHandler.removeCallbacks(it) }
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
        timerHandler.postDelayed({ connectOrScan() }, delayMs)
    }

    private fun connect(device: BluetoothDevice) {
        lastAddress = device.address
        post(ConnState.CONNECTING, null)
        gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            if (!scanning) return
            stopScan()
            connect(result.device)
        }

        override fun onScanFailed(errorCode: Int) {
            scanning = false
            post(ConnState.DISCONNECTED, "Falha no scan BLE (código $errorCode).")
            scheduleRetry(3000)
        }
    }

    // ----------------------------------------------------------------- GATT

    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                failures = 0
                if (!g.requestMtu(185)) g.discoverServices()
                return
            }

            // Desconectou ou falhou
            g.close()
            if (gatt !== g) return
            gatt = null
            control = null
            notifyQueue.clear()
            post(ConnState.DISCONNECTED, if (wantConnected) "Conexão perdida. Reconectando…" else null)
            if (wantConnected) {
                failures++
                if (failures >= 3) lastAddress = null  // volta a procurar por scan
                scheduleRetry(2000)
            }
        }

        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
            g.discoverServices()
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val service = g.getService(BleUuids.SERVICE)
            val reading = service?.getCharacteristic(BleUuids.READING)
            if (status != BluetoothGatt.GATT_SUCCESS || service == null || reading == null) {
                post(ConnState.DISCONNECTED, "Serviço do bastão não encontrado.")
                g.disconnect()
                return
            }
            control = service.getCharacteristic(BleUuids.CONTROL)
            notifyQueue.clear()
            notifyQueue.add(reading)
            service.getCharacteristic(BleUuids.STATUS)?.let { notifyQueue.add(it) }
            enableNext(g)
        }

        override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            enableNext(g)
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
            post(ConnState.CONNECTED, null)
            return
        }
        if (!enableNotifications(g, next)) enableNext(g)
    }

    // -------------------------------------------------------------- parsing

    /** R;<seq>;<uptime_ms>;<id> */
    private fun parseReading(text: String) {
        val p = text.trim().split(";")
        if (p.size < 4 || p[0] != "R") return
        val seq = p[1].toLongOrNull() ?: return
        val ms = p[2].toLongOrNull() ?: return
        val tag = p.subList(3, p.size).joinToString(";")
        mainHandler.post { listener.onReading(seq, ms, tag) }
    }

    /** S;<bateria_mV>;<modo_gatilho>;<versao_fw> */
    private fun parseStatus(text: String) {
        val p = text.trim().split(";")
        if (p.size < 4 || p[0] != "S") return
        val mv = p[1].toIntOrNull() ?: return
        val mode = p[2].toIntOrNull() ?: return
        mainHandler.post { listener.onStatus(mv, mode, p[3]) }
    }

    private fun post(state: ConnState, detail: String?) {
        mainHandler.post { listener.onState(state, detail) }
    }
}
