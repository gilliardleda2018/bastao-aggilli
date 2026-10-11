package br.com.aggilli.bastao.sync

import br.com.aggilli.bastao.BuildConfig
import br.com.aggilli.bastao.data.Reading
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Erro que não adianta repetir (token inválido, configuração errada). */
class SyncAuthException(message: String) : Exception(message)

/**
 * Ponto de integração com o sistema Aggilli.
 * Contrato da API: docs/08-integracao-aggilli-agro.md
 */
interface SyncService {
    /** Envia as leituras; retorna os UUIDs que o servidor confirmou. */
    suspend fun push(readings: List<Reading>): Set<String>
}

/** Envia as leituras ao Aggilli Agro por HTTPS (POST JSON com token Bearer). */
class HttpSyncService(private val config: SyncConfig) : SyncService {

    private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)

    override suspend fun push(readings: List<Reading>): Set<String> = withContext(Dispatchers.IO) {
        if (readings.isEmpty()) return@withContext emptySet()

        val body = JSONObject().apply {
            put("fazenda_id", config.farmId)
            put("dispositivo", config.deviceName)
            put("app_versao", BuildConfig.VERSION_NAME)
            put("leituras", JSONArray().apply {
                readings.forEach { r ->
                    put(JSONObject().apply {
                        put("uuid", r.uuid)
                        put("sessao", r.session)
                        put("brinco", r.tag)
                        put("data_hora", isoFormat.format(Date(r.timestamp)))
                        put("recuperada", r.recovered)
                    })
                }
            })
        }

        val conn = URL(config.baseUrl + SyncConfig.ENDPOINT).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("Authorization", "Bearer ${config.token}")
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            when {
                code == 401 || code == 403 -> throw SyncAuthException("Token recusado pelo Aggilli Agro (HTTP $code).")
                code == 404 -> throw SyncAuthException("Endereço da API não encontrado (HTTP 404). Confira a URL.")
                code !in 200..299 -> throw IOException("Aggilli Agro respondeu HTTP $code.")
            }

            val text = conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
            parseAccepted(text, readings)
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Resposta esperada: {"aceitas": ["uuid", ...]}.
     * Leituras que o servidor já tinha também devem vir em "aceitas" (envio idempotente).
     * Sem o campo "aceitas", um 2xx confirma o lote inteiro.
     */
    private fun parseAccepted(text: String, sent: List<Reading>): Set<String> {
        val json = try { JSONObject(text) } catch (_: Exception) { null }
        val arr = json?.optJSONArray("aceitas") ?: return sent.map { it.uuid }.toSet()
        return (0 until arr.length()).map { arr.getString(it) }.toSet()
    }
}

/** Implementação que não envia nada (testes, ou app sem integração configurada). */
class NoopSyncService : SyncService {
    override suspend fun push(readings: List<Reading>): Set<String> = emptySet()
}
