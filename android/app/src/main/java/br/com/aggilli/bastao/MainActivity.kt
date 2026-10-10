package br.com.aggilli.bastao

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.aggilli.bastao.ble.BleClient
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.all { it }) vm.connect()
        else vm.setMessage("Permissões de Bluetooth negadas.")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            MaterialTheme {
                Screen(
                    vm = vm,
                    onConnect = ::onConnectClicked,
                    onExport = ::onExportClicked,
                )
            }
        }
    }

    private fun requiredPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    private fun onConnectClicked() {
        if (vm.isActive()) {
            vm.disconnect()
        } else {
            permissionLauncher.launch(requiredPermissions())
        }
    }

    private fun onExportClicked() {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_SUBJECT, "Leituras ${vm.state.value.session}")
            putExtra(Intent.EXTRA_TEXT, vm.exportCsv())
        }
        startActivity(Intent.createChooser(send, "Exportar leituras"))
    }
}

private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

private fun connLabel(s: UiState): String = when (s.conn) {
    BleClient.ConnState.DISCONNECTED -> "Desconectado"
    BleClient.ConnState.SCANNING -> "Procurando o bastão…"
    BleClient.ConnState.CONNECTING -> "Conectando…"
    BleClient.ConnState.CONNECTED -> "Conectado"
}

@Composable
fun Screen(vm: MainViewModel, onConnect: () -> Unit, onExport: () -> Unit) {
    val s by vm.state.collectAsStateWithLifecycle()

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(connLabel(s), fontWeight = FontWeight.Bold)
                val battery = s.batteryPercent?.let { "Bateria ~$it%" } ?: "Bateria --"
                Text(battery)
            }
            s.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            // Última leitura
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Último brinco", style = MaterialTheme.typography.labelLarge)
                    Text(
                        text = s.lastTag ?: "—",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    if (s.lastWasDuplicate) {
                        Text("Já lido nesta sessão", color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            Text("Sessão ${s.session}  •  ${s.total} animais")

            // Botões
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onConnect) {
                    Text(if (s.active) "Desconectar" else "Conectar")
                }
                OutlinedButton(onClick = { vm.startNewSession() }) { Text("Nova sessão") }
                OutlinedButton(onClick = onExport) { Text("Exportar") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { vm.beepWand() },
                    enabled = s.conn == BleClient.ConnState.CONNECTED,
                ) { Text("Bip no bastão") }
                OutlinedButton(
                    onClick = { vm.setTriggerMode(s.triggerMode != 1) },
                    enabled = s.conn == BleClient.ConnState.CONNECTED,
                ) { Text(if (s.triggerMode == 1) "Modo: gatilho" else "Modo: contínuo") }
            }

            HorizontalDivider()

            // Lista
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(s.readings, key = { it.id }) { r ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(r.tag, fontWeight = FontWeight.Medium)
                        Text(timeFormat.format(Date(r.timestamp)))
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}
