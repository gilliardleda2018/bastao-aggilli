package br.com.aggilli.bastao.leitor

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothSocket
import android.os.Handler
import android.os.Looper
import br.com.aggilli.bastao.ble.BleClient
import java.io.IOException
import java.io.InputStream
import java.util.UUID

/**
 * Bastões comerciais pelo Bluetooth clássico, perfil serial (SPP): Allflex (RS420, AWR250, LPR),
 * Datamars/Tru-Test (XRS2, XRS2i no modo padrão), Gallagher (HR4, HR5), Agrident e similares.
 *
 * O bastão é pareado antes nas configurações de Bluetooth do Android (PIN costuma ser 0000 ou 1234).
 * Aqui só abrimos o canal serial, lemos o texto linha a linha e entregamos cada número de brinco.
 * Se a conexão cair, tentamos de novo sozinhos enquanto o usuário não desconectar.
 * Os retornos do [Listener] chegam na thread principal.
 */
@SuppressLint("MissingPermission")  // o app pede BLUETOOTH_CONNECT antes de conectar
class SppClient(private val listener: Listener) {

    interface Listener {
        fun onSppState(state: BleClient.ConnState, detail: String?)
        fun onSppTag(tag: String)
    }

    private val main = Handler(Looper.getMainLooper())
    @Volatile private var running = false

    /** Cada start() abre uma geração nova; a thread de uma geração antiga para e fica muda. */
    @Volatile private var geracao = 0
    @Volatile private var socket: BluetoothSocket? = null
    private var thread: Thread? = null

    fun start(adapter: BluetoothAdapter, address: String) {
        stop()
        running = true
        val g = ++geracao
        thread = Thread({ loop(adapter, address, g) }, "bastao-spp").also { it.start() }
    }

    fun stop() {
        running = false
        fechar(socket)  // destrava o read() bloqueado da thread atual
        thread?.interrupt()
        thread = null
    }

    private fun ativo(g: Int) = running && g == geracao

    private fun loop(adapter: BluetoothAdapter, address: String, g: Int) {
        var espera = 2_000L
        while (ativo(g)) {
            estado(g, BleClient.ConnState.CONNECTING, null)
            var s: BluetoothSocket? = null
            try {
                adapter.cancelDiscovery()
                val device = adapter.getRemoteDevice(address)
                s = device.createRfcommSocketToServiceRecord(SPP_UUID)
                socket = s
                try {
                    s.connect()
                } catch (e: IOException) {
                    // alguns leitores só aceitam o canal sem criptografia
                    fechar(s)
                    s = device.createInsecureRfcommSocketToServiceRecord(SPP_UUID)
                    socket = s
                    s.connect()
                }
                espera = 2_000L
                if (!ativo(g)) break
                estado(g, BleClient.ConnState.CONNECTED, null)
                ler(requireNotNull(s).inputStream, g)
            } catch (e: IOException) {
                if (ativo(g)) estado(g, BleClient.ConnState.CONNECTING, "Sem contato com o bastão. Ligado e perto? Tentando de novo…")
            } catch (e: SecurityException) {
                estado(g, BleClient.ConnState.DISCONNECTED, "Permissão de Bluetooth negada.")
                break
            } finally {
                fechar(s)  // só o socket desta tentativa, nunca o de uma conexão nova
                if (socket === s) socket = null
            }
            if (!ativo(g)) break
            try {
                Thread.sleep(espera)
            } catch (e: InterruptedException) {
                break
            }
            espera = (espera * 2).coerceAtMost(30_000L)
        }
        estado(g, BleClient.ConnState.DISCONNECTED, null)
    }

    /** Lê até a conexão cair. Cada fim de linha (CR, LF ou ETX) fecha um registro. */
    private fun ler(input: InputStream, g: Int) {
        val linha = StringBuilder()
        val buf = ByteArray(256)
        while (ativo(g)) {
            val n = input.read(buf)
            if (n < 0) throw IOException("conexão encerrada pelo bastão")
            for (i in 0 until n) {
                val c = (buf[i].toInt() and 0xFF).toChar()
                when {
                    c == '\r' || c == '\n' || c == ETX -> { entregar(linha, g); linha.clear() }
                    c >= ' ' && linha.length < 512 -> linha.append(c)  // STX e outros controles ficam de fora
                }
            }
            // Leitor que não manda fim de linha: entrega quando o número já chegou inteiro
            if (linha.isNotEmpty() && input.available() == 0 && EidParser.extrair(linha.toString()) != null) {
                entregar(linha, g)
                linha.clear()
            }
        }
    }

    private fun entregar(linha: CharSequence, g: Int) {
        val tag = EidParser.extrair(linha.toString()) ?: return
        main.post { if (g == geracao) listener.onSppTag(tag) }
    }

    private fun estado(g: Int, state: BleClient.ConnState, detail: String?) {
        main.post { if (g == geracao) listener.onSppState(state, detail) }
    }

    private fun fechar(s: BluetoothSocket?) {
        try {
            s?.close()
        } catch (_: IOException) {
        }
    }

    companion object {
        /** UUID padrão do perfil serial (SPP). */
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        private const val ETX = '\u0003'
    }
}
