package br.com.aggilli.bastao.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import br.com.aggilli.bastao.data.ReadingDb
import java.util.concurrent.TimeUnit

/**
 * Envia as leituras pendentes ao Aggilli Agro em lotes.
 * Roda em segundo plano, só com internet, e o Android repete sozinho se falhar (backoff).
 * Continua funcionando com o app fechado.
 */
class SyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val config = SyncConfig(applicationContext)
        if (!config.isConfigured || !config.autoSync) return Result.success()

        val service: SyncService = HttpSyncService(config)
        val db = ReadingDb.get(applicationContext)
        try {
            var rounds = 0
            while (rounds++ < 50) {                  // até 50 lotes de 200 por execução
                val batch = db.pendingSync(BATCH)
                if (batch.isEmpty()) break
                val accepted = service.push(batch)
                val ids = batch.filter { it.uuid in accepted }.map { it.id }
                db.markSynced(ids)
                if (ids.size < batch.size) {
                    config.lastError = "${batch.size - ids.size} leitura(s) recusada(s) pelo servidor."
                    break                            // evita laço infinito com leituras recusadas
                }
                config.lastError = null
            }
            config.lastSyncAt = System.currentTimeMillis()
            return Result.success()
        } catch (e: SyncAuthException) {
            config.lastError = e.message
            return Result.failure()                 // não adianta repetir até corrigir a configuração
        } catch (e: Exception) {
            config.lastError = "Sem conexão com o Aggilli Agro: ${e.message ?: e.javaClass.simpleName}"
            return Result.retry()
        }
    }

    companion object {
        private const val BATCH = 200
        private const val NOW = "bastao-sync-agora"
        private const val PERIODIC = "bastao-sync-periodico"

        private val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        /** Agenda um envio em alguns segundos (agrupa leituras feitas em sequência). */
        fun requestSoon(context: Context, delaySeconds: Long = 10) {
            val req = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints)
                .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            // KEEP: se já há um envio agendado, ele levará estas leituras também
            WorkManager.getInstance(context).enqueueUniqueWork(NOW, ExistingWorkPolicy.KEEP, req)
        }

        /** Envio imediato (botão "Sincronizar agora"). */
        fun requestNow(context: Context) {
            val req = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, req)
        }

        /** Rede de segurança: varre as pendências a cada 15 min (mínimo do Android). */
        fun schedulePeriodic(context: Context) {
            val req = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
