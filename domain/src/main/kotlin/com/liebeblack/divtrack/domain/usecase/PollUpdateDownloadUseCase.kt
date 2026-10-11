package com.liebeblack.divtrack.domain.usecase

import com.liebeblack.divtrack.domain.model.UpdateDownload
import com.liebeblack.divtrack.domain.repository.UpdateRepository
import javax.inject.Inject

/**
 * Consulta puntual del estado de la descarga.
 *
 * Sondea explícitamente (la pantalla pregunta mientras la descarga está viva) en lugar de
 * exponer un flujo infinito desde el gestor del sistema. El motivo es de coste: un
 * `ContentResolver` consultado en cada emisión mantendría trabajo de fondo mientras alguien
 * mire la pantalla, y aquí basta con una pregunta por segundo, solo mientras hace falta.
 */
class PollUpdateDownloadUseCase @Inject constructor(
    private val repository: UpdateRepository,
) {
    suspend operator fun invoke(): UpdateDownload = repository.pollDownload()
}
