package com.liebeblack.divtrack.domain.repository

import com.liebeblack.divtrack.core.common.result.Result
import com.liebeblack.divtrack.domain.model.UpdateDownload
import com.liebeblack.divtrack.domain.model.UpdateStatus
import kotlinx.coroutines.flow.Flow

/**
 * Contrato del sistema de actualizaciones.
 *
 * **Qué no hace.** No instala nada y no depende de ninguna tienda: la app publica su APK en
 * GitHub Releases (ver `.github/workflows/android-ci.yml`) y este contrato solo resuelve
 * «¿hay una versión más nueva?» y «trae el APK». La instalación la confirma siempre Android
 * con su propia pantalla; una app no puede (ni debe) sustituirse a sí misma en silencio.
 *
 * **Qué sí hace, y por qué así.**
 * - [observeStatus] emite **siempre** lo último conocido, venga de una respuesta o de lo
 *   guardado: la pantalla de Ajustes se pinta al instante y sin red, igual que las tasas.
 * - [checkForUpdates] es la única llamada de red. Con `force = false` respeta la ventana de
 *   frescura (no gasta cuota de la API por volver a Ajustes); con `force = true` la ignora,
 *   porque es el usuario quien pidió la respuesta. Si falla, **no** escribe nada: un fallo de
 *   red no puede convertirse en un «no hay actualizaciones» de seis horas.
 * - [downloadUpdate] encola la descarga en el gestor del sistema (sigue viva con la app
 *   cerrada) y [pollDownload] pregunta su estado en un momento dado: es un sondeo explícito,
 *   no un flujo infinito, para que nadie observando la pantalla no haya trabajo de fondo.
 */
interface UpdateRepository {

    fun observeStatus(): Flow<UpdateStatus>

    /** `force = true` salta la ventana de frescura (acción explícita del usuario). */
    suspend fun checkForUpdates(force: Boolean): Result<UpdateStatus>

    /** Encola la descarga del APK del release conocido. Devuelve el id del sistema. */
    suspend fun downloadUpdate(): Result<Long>

    /** Estado actual de la descarga en curso (o de la última, si ya terminó). */
    suspend fun pollDownload(): UpdateDownload
}
