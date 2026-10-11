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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.aggilli.bastao.ble.BleClient
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    /** O que fazer depois que as permissões forem respondidas. */
    private var afterPermissions: (() -> Unit)? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        // A notificação é opcional; só as permissões de Bluetooth são obrigatórias
        val bluetoothOk = bluetoothPermissions().all { result[it] ?: true }
        if (bluetoothOk) afterPermissions?.invoke()
        else vm.setMessage("Permissões de Bluetooth negadas.")
        afterPermissions = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            MaterialTheme {
                Screen(
                    vm = vm,
                    onConnect = ::onConnectClicked,
                    onPick = { withPermissions { vm.startDiscovery() } },
                    onExport = ::onExportClicked,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.refresh()
    }

    private fun bluetoothPermissions(): List<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    private fun withPermissions(action: () -> Unit) {
        val perms = bluetoothPermissions().toMutableList()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) perms += Manifest.permission.POST_NOTIFICATIONS
        afterPermissions = action
        permissionLauncher.launch(perms.toTypedArray())
    }

    private fun onConnectClicked() {
        if (vm.isActive()) vm.disconnect() else withPermissions { vm.connect() }
    }

    private fun onExportClicked() {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_SUBJECT, "Leituras ${vm.sessionId()}")
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

private fun sdLabel(sd: Int?): String? = when (sd) {
    1 -> "Cartão SD ok"
    2 -> "Cartão SD com erro"
    0 -> "Sem cartão SD"
    else -> null
}

private fun syncLabel(s: UiState): String {
    if (!s.syncConfigured) return "Sincronização não configurada"
    val last = if (s.lastSyncAt > 0) "última às ${timeFormat.format(Date(s.lastSyncAt))}" else "ainda não sincronizado"
    return if (s.syncPending == 0) "Tudo enviado • $last" else "${s.syncPending} pendente(s) • $last"
}

@Composable
fun Screen(vm: MainViewModel, onConnect: () -> Unit, onPick: () -> Unit, onExport: () -> Unit) {
    val s by vm.state.collectAsStateWithLifecycle()
    var showSyncConfig by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }

    if (showSyncConfig) SyncConfigDialog(vm = vm, onDismiss = { showSyncConfig = false })
    if (showPicker) {
        WandPickerDialog(s = s, onPick = { vm.selectWand(it); showPicker = false }, onForget = { vm.forgetWand() },
            onRescan = onPick, onDismiss = { vm.stopDiscovery(); showPicker = false })
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Bastão
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(connLabel(s), fontWeight = FontWeight.Bold)
                    Text(s.wandName ?: "Nenhum bastão escolhido", style = MaterialTheme.typography.bodySmall)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(s.batteryPercent?.let { "Bateria $it%" } ?: "Bateria --")
                    sdLabel(s.sdState)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
            s.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (s.wandPending > 0 && s.conn == BleClient.ConnState.CONNECTED) {
                Text("Recebendo ${s.wandPending} leitura(s) guardada(s) no bastão…")
            }

            // Último brinco
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Último brinco", style = MaterialTheme.typography.labelLarge)
                    Text(text = s.lastTag ?: "—", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    if (s.lastWasDuplicate) Text("Já lido nesta sessão", color = MaterialTheme.colorScheme.error)
                }
            }

            Text("Sessão ${s.session}  •  ${s.total} animais")

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onConnect) { Text(if (s.active) "Desconectar" else "Conectar") }
                OutlinedButton(onClick = { onPick(); showPicker = true }) { Text("Escolher bastão") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.startNewSession() }) { Text("Nova sessão") }
                OutlinedButton(onClick = onExport) { Text("Exportar") }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val connected = s.conn == BleClient.ConnState.CONNECTED
                OutlinedButton(onClick = { vm.beepWand() }, enabled = connected) { Text("Bip") }
                OutlinedButton(onClick = { vm.setTriggerMode(s.triggerMode != 1) }, enabled = connected) {
                    Text(if (s.triggerMode == 1) "Modo: gatilho" else "Modo: contínuo")
                }
                Switch(checked = s.voice, onCheckedChange = { vm.setVoice(it) })
                Text("Voz")
            }

            // Sincronização com o Aggilli Agro
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Aggilli Agro", fontWeight = FontWeight.Bold)
                        Text(syncLabel(s), style = MaterialTheme.typography.bodySmall)
                        s.syncError?.let {
                            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    TextButton(onClick = { vm.syncNow() }, enabled = s.syncConfigured) { Text("Enviar") }
                    TextButton(onClick = { showSyncConfig = true }) { Text("Configurar") }
                }
            }

            HorizontalDivider()

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(s.readings, key = { it.id }) { r ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(r.tag, fontWeight = FontWeight.Medium)
                        Text(timeFormat.format(Date(r.timestamp)) + if (r.synced) " ✓" else "")
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
fun WandPickerDialog(
    s: UiState,
    onPick: (br.com.aggilli.bastao.ble.FoundWand) -> Unit,
    onForget: () -> Unit,
    onRescan: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Escolher bastão") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    if (s.discovering) "Procurando bastões ligados por perto…" else "Ligue o bastão e toque em Procurar.",
                    style = MaterialTheme.typography.bodySmall,
                )
                s.discovered.forEach { w ->
                    val current = w.address == s.wandAddress
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onPick(w) }.padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(w.name + if (current) "  (atual)" else "", fontWeight = FontWeight.Medium)
                        Text(signal(w.rssi), style = MaterialTheme.typography.bodySmall)
                    }
                    HorizontalDivider()
                }
                if (s.wandAddress != null) {
                    TextButton(onClick = onForget) { Text("Esquecer ${s.wandName ?: "bastão atual"}") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onRescan, enabled = !s.discovering) { Text("Procurar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Fechar") } },
    )
}

private fun signal(rssi: Int): String = when {
    rssi > -60 -> "sinal forte"
    rssi > -80 -> "sinal médio"
    else -> "sinal fraco"
}

@Composable
fun SyncConfigDialog(vm: MainViewModel, onDismiss: () -> Unit) {
    val c = vm.syncConfig
    var url by remember { mutableStateOf(c.baseUrl) }
    var token by remember { mutableStateOf(c.token) }
    var farm by remember { mutableStateOf(c.farmId) }
    var device by remember { mutableStateOf(c.deviceName) }
    var auto by remember { mutableStateOf(c.autoSync) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sincronizar com o Aggilli Agro") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = url, onValueChange = { url = it }, label = { Text("Endereço (https)") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
                OutlinedTextField(value = farm, onValueChange = { farm = it }, label = { Text("Fazenda (ID)") }, singleLine = true)
                OutlinedTextField(
                    value = token, onValueChange = { token = it }, label = { Text("Token do dispositivo") },
                    singleLine = true, visualTransformation = PasswordVisualTransformation(),
                )
                OutlinedTextField(value = device, onValueChange = { device = it }, label = { Text("Nome do bastão") }, singleLine = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = auto, onCheckedChange = { auto = it })
                    Text("  Enviar automaticamente")
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                vm.saveSyncConfig(url, token, farm, device, auto)
                onDismiss()
            }) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
