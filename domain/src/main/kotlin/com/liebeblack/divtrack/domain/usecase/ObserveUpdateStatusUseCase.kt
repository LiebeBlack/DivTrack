package com.liebeblack.divtrack.domain.usecase

import com.liebeblack.divtrack.domain.model.UpdateStatus
import com.liebeblack.divtrack.domain.repository.UpdateRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/**
 * Estado de actualizaciones guardado (sin red).
 *
 * Emite al instante con lo que DataStore tenga, así que la sección de Ajustes se pinta sin
 * conexión y sin esperar a ninguna petición: es el mismo criterio Online-First que las
 * tasas, aplicado a la versión.
 */
class ObserveUpdateStatusUseCase @Inject constructor(
    private val repository: UpdateRepository,
) {
    operator fun invoke(): Flow<UpdateStatus> = repository.observeStatus()
}
