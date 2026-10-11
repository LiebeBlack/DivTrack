package com.liebeblack.divtrack.domain.usecase

import com.liebeblack.divtrack.core.common.result.Result
import com.liebeblack.divtrack.domain.repository.UpdateRepository
import javax.inject.Inject

/**
 * Encola la descarga del APK del release ya comprobado.
 *
 * Devuelve el id con el que el gestor del sistema identifica la descarga; a partir de ahí el
 * progreso se consulta con [PollUpdateDownloadUseCase]. La descarga no la hace la app: la
 * hace el sistema, así que sigue viva aunque el usuario cierre DivTrack.
 */
class DownloadUpdateUseCase @Inject constructor(
    private val repository: UpdateRepository,
) {
    suspend operator fun invoke(): Result<Long> = repository.downloadUpdate()
}
