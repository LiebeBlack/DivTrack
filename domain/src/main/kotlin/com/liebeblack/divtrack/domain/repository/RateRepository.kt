package com.liebeblack.divtrack.domain.repository

import com.liebeblack.divtrack.core.common.result.Result
import com.liebeblack.divtrack.domain.model.ExchangeRate
import com.liebeblack.divtrack.domain.model.ProviderDiagnostics
import com.liebeblack.divtrack.domain.model.SyncSummary
import kotlinx.coroutines.flow.Flow

/**
 * Contrato de tasas.
 *
 * Patrón Online-First con Room como única fuente de verdad para la UI:
 * - Las lecturas son `Flow` de la base local: la UI se actualiza sola cuando la red
 *   escribe y sigue funcionando sin conexión.
 * - [refreshRates] intenta la red; si responde, escribe en Room (lo que re-emite a la UI);
 *   si falla, NO toca los datos y devuelve [Result.Error] para que la UI avise.
 * - [testProviders] consulta cada fuente sin escribir nada: es el diagnóstico de la
 *   pantalla de Ajustes y una conexión que responde JSON sin pares utilizables cuenta
 *   como fallida, no como sana.
 */
interface RateRepository {

    fun observeRates(): Flow<List<ExchangeRate>>

    /**
     * `true` mientras la caché local venga de una reconstrucción: la base estaba dañada, se
     * recreó vacía y el usuario merece saberlo (ADR 16 y ADR 31). Vuelve a `false` cuando una
     * sincronización logra escribir tasas otra vez.
     */
    fun observeCacheRebuild(): Flow<Boolean>

    suspend fun refreshRates(): Result<SyncSummary>

    suspend fun testProviders(): ProviderDiagnostics
}
