package com.liebeblack.divtrack.core.network.provider

import com.liebeblack.divtrack.core.network.model.RemoteRateSet

/**
 * Contrato de un proveedor de tasas.
 *
 * Se descubren por multibinding de Hilt y se ordenan por [priority] (menor = preferido).
 * Añadir una API nueva es implementar esta interfaz y registrarla en `NetworkModule`:
 * ni el dominio ni la UI cambian.
 *
 * El contrato es intencionadamente mínimo: una sola llamada que devuelve las tasas
 * vigentes. Cualquier método extra (series históricas, por ejemplo) obliga a implementarlo
 * en todos los proveedores aunque solo uno lo use, y acaba siendo código muerto.
 */
interface RateProvider {

    /** Identificador estable, se persiste junto a cada tasa para mostrar su procedencia. */
    val id: String

    val priority: Int

    /**
     * Fuentes para las que este proveedor es la **referencia** (ADR 40).
     *
     * Si publica un valor válido de una de estas fuentes, el registro no deja que otro
     * proveedor lo reemplace —ni aunque traiga una fecha más reciente— y deja de buscar
     * respaldo para esa tasa. Es la regla que pidió el producto para el dólar oficial:
     * `ve.dolarapi.com/v1/dolares/oficial` manda siempre que responda, y los demás
     * proveedores solo aportan oficial cuando ese falle.
     *
     * Vacío por defecto: solo quien realmente sea la referencia se declara. El paralelo no
     * cabe aquí: se mueve varias veces al día y su upstream es Yadio.
     */
    val authoritativeSources: Set<String>
        get() = emptySet()

    /** Lanza excepción si el proveedor falla; el registro aísla el fallo por proveedor. */
    suspend fun fetchLatest(): RemoteRateSet
}
