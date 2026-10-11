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
        /** ageMs: há quanto tempo o bastão fez a leitura (> 0 quando veio da fila offline). */
        fun onReading(seq: Long, deviceMs: Long, tag: String, ageMs: Long)
        fun onStatus(batteryMv: Int, triggerMode: Int, firmware: String, pending: Int, dropped: Long)
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

    // Escritas no CONTROL: o Android só aceita uma operação GATT por vez, então enfileiramos.
    private val writeQueue = ArrayDeque<String>()
    private var writeBusy = false
    private var pendingAck: Long? = null

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

    /** Envia um comando ao bastão: "B" (bip), "M0" (modo contínuo), "M1" (modo gatilho), "H" (pronto). */
    fun sendControl(command: String) {
        mainHandler.post {
            writeQueue.addLast(command)
            pumpWrites()
        }
    }

    /**
     * Confirma ao bastão que as leituras até [seq] foram gravadas no celular.
     * ACKs seguidos são agrupados: só o maior vai para a fila.
     */
    fun ack(seq: Long) {
        mainHandler.post {
            val cur = pendingAck
            pendingAck = if (cur == null || seq > cur) seq else cur
            pumpWrites()
        }
    }

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
            // Pilha ocupada: tenta de novo em instantes
            if (cmd.startsWith("A;")) {
                val seq = cmd.substring(2).toLong()
                val cur = pendingAck
                pendingAck = if (cur == null || seq > cur) seq else cur
            } else {
                writeQueue.addFirst(cmd)
            }
            mainHandler.postDelayed({ pumpWrites() }, 50)
        }
    }

    private fun resetWrites() {
        writeQueue.clear()
        writeBusy = false
        pendingAck = null
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
            mainHandler.post { resetWrites() }
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

        override fun onCharacteristicWrite(g: BluetoothGatt, ch: BluetoothGattCharacteristic, status: Int) {
            mainHandler.post {
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
            post(ConnState.CONNECTED, null)
            // Notificações ativas: avisa o bastão para (re)enviar as leituras não confirmadas
            mainHandler.post { resetWrites() }
            sendControl("H")
            return
        }
        if (!enableNotifications(g, next)) enableNext(g)
    }

    // -------------------------------------------------------------- parsing

    /** R;<seq>;<uptime_ms>;<id>[;<idade_ms>]  (idade_ms a partir do firmware 0.2.0) */
    private fun parseReading(text: String) {
        val p = text.trim().split(";")
        if (p.size < 4 || p[0] != "R") return
        val seq = p[1].toLongOrNull() ?: return
        val ms = p[2].toLongOrNull() ?: return
        val tag = p[3]
        if (tag.isEmpty()) return
        val age = p.getOrNull(4)?.toLongOrNull() ?: 0L
        mainHandler.post { listener.onReading(seq, ms, tag, age) }
    }

    /** S;<bateria_mV>;<modo_gatilho>;<versao_fw>[;<pendentes>;<descartadas>] */
    private fun parseStatus(text: String) {
        val p = text.trim().split(";")
        if (p.size < 4 || p[0] != "S") return
        val mv = p[1].toIntOrNull() ?: return
        val mode = p[2].toIntOrNull() ?: return
        val pending = p.getOrNull(4)?.toIntOrNull() ?: 0
        val dropped = p.getOrNull(5)?.toLongOrNull() ?: 0L
        mainHandler.post { listener.onStatus(mv, mode, p[3], pending, dropped) }
    }

    private fun post(state: ConnState, detail: String?) {
        mainHandler.post { listener.onState(state, detail) }
    }
}
