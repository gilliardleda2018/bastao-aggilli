package br.com.aggilli.bastao.sync

import br.com.aggilli.bastao.data.Reading

/**
 * Ponto de integração com o sistema Aggilli.
 *
 * A API (endpoint, autenticação, formato do payload) ainda não foi definida.
 * Quando existir, implemente esta interface e use ReadingDb.pendingSync() / markSynced()
 * para enviar apenas o que ainda não foi sincronizado.
 */
interface SyncService {
    /** Envia as leituras; retorna os ids que o servidor confirmou. */
    suspend fun push(readings: List<Reading>): List<Long>
}

/** Implementação provisória: não envia nada. */
class NoopSyncService : SyncService {
    override suspend fun push(readings: List<Reading>): List<Long> = emptyList()
}
