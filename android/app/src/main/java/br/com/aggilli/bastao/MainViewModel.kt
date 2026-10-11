package br.com.aggilli.bastao

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import br.com.aggilli.bastao.ble.FoundWand
import br.com.aggilli.bastao.sync.SyncConfig
import kotlinx.coroutines.flow.StateFlow

/**
 * Ponte entre a tela e o [BastaoController]. A lógica fica no controlador, que sobrevive
 * à tela (a coleta continua com o app em segundo plano).
 */
class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val c = BastaoController.get(app)

    val state: StateFlow<UiState> = c.state
    val syncConfig: SyncConfig get() = c.syncConfig

    fun connect() = c.connect()
    fun disconnect() = c.disconnect()
    fun isActive(): Boolean = c.isActive()
    fun startDiscovery() = c.startDiscovery()
    fun stopDiscovery() = c.stopDiscovery()
    fun selectWand(w: FoundWand) = c.selectWand(w)
    fun forgetWand() = c.forgetWand()
    fun startNewSession() = c.startNewSession()
    fun beepWand() = c.beepWand()
    fun setTriggerMode(on: Boolean) = c.setTriggerMode(on)
    fun setVoice(on: Boolean) = c.setVoice(on)
    fun exportCsv(): String = c.exportCsv()
    fun sessionId(): String = c.sessionId()
    fun setMessage(text: String?) = c.setMessage(text)
    fun syncNow() = c.syncNow()
    fun saveSyncConfig(url: String, token: String, farmId: String, device: String, auto: Boolean) =
        c.saveSyncConfig(url, token, farmId, device, auto)
    fun refresh() = c.refresh()
}
