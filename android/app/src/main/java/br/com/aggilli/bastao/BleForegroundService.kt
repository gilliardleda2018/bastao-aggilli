package br.com.aggilli.bastao

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import br.com.aggilli.bastao.ble.BleClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Mantém a conexão com o bastão viva com a tela apagada e o celular no bolso.
 * Mostra uma notificação fixa com a contagem da sessão e um botão "Desconectar".
 */
class BleForegroundService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        createChannel()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, build(UiState()), type)
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        scope.launch {
            BastaoController.get(this@BleForegroundService).state.collect { s ->
                nm.notify(NOTIFICATION_ID, build(s))
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_DISCONNECT) {
            BastaoController.get(this).disconnect()
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun build(s: UiState): android.app.Notification {
        val title = when (s.conn) {
            BleClient.ConnState.CONNECTED -> "Bastão conectado"
            BleClient.ConnState.CONNECTING -> "Conectando ao bastão…"
            BleClient.ConnState.SCANNING -> "Procurando o bastão…"
            BleClient.ConnState.DISCONNECTED -> "Bastão desconectado"
        }
        val text = buildString {
            append("Sessão: ${s.total} brinco(s)")
            s.lastTag?.let { append(" · último ${it.takeLast(4)}") }
            if (s.wandPending > 0) append(" · ${s.wandPending} na fila do bastão")
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, BleForegroundService::class.java).setAction(ACTION_DISCONNECT),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_bastao)
            .setColor(getColor(R.color.aggilli_azul))
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(open)
            .addAction(0, "Desconectar", stop)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "Conexão com o bastão", NotificationManager.IMPORTANCE_LOW)
            ch.description = "Mantém a coleta funcionando com a tela apagada"
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
        }
    }

    companion object {
        private const val CHANNEL_ID = "bastao_conexao"
        private const val NOTIFICATION_ID = 42
        const val ACTION_DISCONNECT = "br.com.aggilli.bastao.DESCONECTAR"
    }
}
