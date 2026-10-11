package br.com.aggilli.bastao.sync

import android.content.Context
import android.os.Build

/**
 * Configuração da integração com o Aggilli Agro (web).
 * Preenchida na tela "Configurar sincronização" do app.
 */
class SyncConfig(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("bastao_sync", Context.MODE_PRIVATE)

    /** Endereço do Aggilli Agro, ex.: https://agro.aggillintelligence.com.br */
    var baseUrl: String
        get() = prefs.getString("base_url", DEFAULT_URL) ?: DEFAULT_URL
        set(v) = prefs.edit().putString("base_url", v.trim().trimEnd('/')).apply()

    /** Token de acesso do dispositivo, gerado no Aggilli Agro para a fazenda. */
    var token: String
        get() = prefs.getString("token", "") ?: ""
        set(v) = prefs.edit().putString("token", v.trim()).apply()

    /** Identificador da fazenda no Aggilli Agro. */
    var farmId: String
        get() = prefs.getString("farm_id", "") ?: ""
        set(v) = prefs.edit().putString("farm_id", v.trim()).apply()

    /** Nome do bastão/celular, aparece no Aggilli Agro como origem da leitura. */
    var deviceName: String
        get() = prefs.getString("device_name", null) ?: "Bastao ${Build.MODEL}"
        set(v) = prefs.edit().putString("device_name", v.trim()).apply()

    var autoSync: Boolean
        get() = prefs.getBoolean("auto_sync", true)
        set(v) = prefs.edit().putBoolean("auto_sync", v).apply()

    var lastSyncAt: Long
        get() = prefs.getLong("last_sync_at", 0L)
        set(v) = prefs.edit().putLong("last_sync_at", v).apply()

    var lastError: String?
        get() = prefs.getString("last_error", null)
        set(v) = prefs.edit().putString("last_error", v).apply()

    val isConfigured: Boolean
        get() = baseUrl.startsWith("https://") && token.isNotEmpty() && farmId.isNotEmpty()

    companion object {
        const val DEFAULT_URL = "https://agro.aggillintelligence.com.br"
        const val ENDPOINT = "/api/v1/bastao/leituras"
    }
}
