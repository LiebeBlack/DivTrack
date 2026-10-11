package com.liebeblack.divtrack.domain.scheduler

/**
 * Programación de la comprobación silenciosa de versiones.
 *
 * Es un contrato aparte de [SyncScheduler] a propósito: son dos trabajos con propósitos y
 * ritmos distintos (tasas cada pocos minutos, versión una vez al día), y mezclarlos
 * obligaría a que apagar la sincronización de tasas apagara también la comprobación de
 * versión —o al revés—, que no es lo que el usuario pidió.
 *
 * No hay notificaciones de sistema: el resultado se guarda y la app lo cuenta en Ajustes
 * cuando el usuario entra. Así el módulo no necesita el permiso de notificaciones, que este
 * proyecto no declara ni quiere declarar.
 *
 * La programación no se puede cancelar porque no hay nada que la apague: comprobar la
 * versión una vez al día es gratis en cuota (una petición de la API pública de GitHub, con
 * revalidación por `ETag`) y no despierta el proceso sin red. Si algún día existe una
 * preferencia para desactivarla, este contrato es el sitio donde añadirla.
 */
interface UpdateCheckScheduler {

    /** Deja programada la comprobación periódica. Idempotente. */
    suspend fun ensureScheduled()
}
