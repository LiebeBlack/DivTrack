package com.liebeblack.divtrack.core.database

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Señal de una sola dirección: «la caché local se recreó porque el archivo no era legible».
 *
 * La enciende [RebuildingOpenHelperFactory] justo antes de borrar una base dañada y la apaga
 * `RateRepositoryImpl` en cuanto una sincronización vuelve a escribir tasas. La UI la lee a
 * través de `RateRepository.observeCacheRebuild()`.
 *
 * Existe por ADR 16: perder los datos guardados sin avisar no es una opción. La pérdida en sí
 * es deliberada (un archivo corrupto no se puede recuperar), pero tiene que ser **visible**;
 * este estado es lo que la convierte en el aviso persistente del panel.
 */
@Singleton
class DatabaseRebuildTracker @Inject constructor() {

    private val rebuiltState = MutableStateFlow(false)

    val rebuilt: StateFlow<Boolean> = rebuiltState.asStateFlow()

    fun markRebuilt() {
        rebuiltState.value = true
    }

    /** La caché volvió a llenarse: el aviso deja de ser cierto y desaparece. */
    fun markRecovered() {
        rebuiltState.value = false
    }
}
