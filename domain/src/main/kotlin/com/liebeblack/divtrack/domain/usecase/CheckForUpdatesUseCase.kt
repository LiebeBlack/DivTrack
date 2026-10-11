package com.liebeblack.divtrack.domain.usecase

import com.liebeblack.divtrack.core.common.result.Result
import com.liebeblack.divtrack.domain.model.UpdateStatus
import com.liebeblack.divtrack.domain.repository.UpdateRepository
import javax.inject.Inject

/**
 * Comprueba la última versión publicada.
 *
 * @param force `true` cuando lo pidió el usuario (botón «Buscar actualización»): salta la
 *   ventana de frescura y siempre pregunta. `false` en la comprobación de fondo, que respeta
 *   la ventana y no gasta cuota de la API por repetir una pregunta recién hecha.
 */
class CheckForUpdatesUseCase @Inject constructor(
    private val repository: UpdateRepository,
) {
    suspend operator fun invoke(force: Boolean = false): Result<UpdateStatus> =
        repository.checkForUpdates(force)
}
