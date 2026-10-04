package com.liebeblack.divtrack.domain.usecase

import com.liebeblack.divtrack.domain.repository.RateRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/**
 * Observa si la caché local tuvo que reconstruirse tras detectar un archivo dañado.
 * El panel lo muestra como aviso persistente hasta que una sincronización vuelva a llenarla.
 */
class ObserveCacheRebuildUseCase @Inject constructor(
    private val repository: RateRepository,
) {
    operator fun invoke(): Flow<Boolean> = repository.observeCacheRebuild()
}
