package com.liebeblack.divtrack.core.common.utils

/**
 * Comparación de versiones por componentes numéricos.
 *
 * Vive en el núcleo puro porque la usan dos capas que no se conocen entre sí: el
 * repositorio de actualizaciones de `:data` (para decidir si el release publicado es más
 * nuevo que el APK instalado) y los tests. Sin Android, sin red y sin dependencias.
 *
 * **Por qué no se compara como texto.** `"4.10" < "4.7"` es cierto comparando cadenas y
 * falso comparando versiones, que es justo el error que hace que una app «deje de ver»
 * actualizaciones a partir de la décima versión.
 *
 * **Tolerancia deliberada.** Los nombres reales no vienen limpios: el release rolling de
 * este proyecto se titula `DivTrack 4.7 (latest)` y su etiqueta es `latest` (sin números).
 * Por eso no se parsea el texto entero, se extrae el **primer bloque numérico con puntos**
 * (`4.7`, `v4.7.1`, `4.7.0-rc1`) y se comparan sus componentes. Un texto sin números
 * devuelve lista vacía, y una comparación en la que falte uno de los dos lados devuelve `0`
 * (indeciso) en lugar de inventar un orden.
 */
object VersionNames {

    /**
     * Componentes numéricos de [raw], o lista vacía si el texto no contiene ninguna versión.
     *
     * `"DivTrack 4.7 (latest)"` -> `[4, 7]` · `"v4.7.1"` -> `[4, 7, 1]` · `"latest"` -> `[]`.
     */
    fun componentsOf(raw: String?): List<Int> {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return emptyList()

        val match = VERSION_PATTERN.find(text) ?: return emptyList()
        return match.value
            .split('.')
            .mapNotNull { part -> part.toIntOrNull() }
    }

    /**
     * Primera versión legible de la lista de textos, ya normalizada ("4.7"), o `null` si
     * ninguno la contiene.
     *
     * El orden de la lista es la prioridad: el título del release manda sobre su cuerpo y
     * sobre su etiqueta, porque es el campo que el flujo de publicación escribe siempre.
     */
    fun firstVersionOf(vararg candidates: String?): String? = candidates
        .asSequence()
        .map(::componentsOf)
        .firstOrNull { it.isNotEmpty() }
        ?.joinToString(separator = ".")

    /**
     * Compara dos versiones componente a componente: negativo si [left] es anterior,
     * `0` si son iguales o si alguna de las dos no se puede leer, positivo si es posterior.
     *
     * Los componentes que falten cuentan como `0`, así que `4.7` y `4.7.0` son la misma
     * versión: es la convención de todo el mundo y evita ofrecer una actualización falsa
     * porque el release escriba un cero de más.
     */
    fun compare(left: String?, right: String?): Int {
        val leftComponents = componentsOf(left)
        val rightComponents = componentsOf(right)
        if (leftComponents.isEmpty() || rightComponents.isEmpty()) return 0

        for (index in 0 until maxOf(leftComponents.size, rightComponents.size)) {
            val difference =
                (leftComponents.getOrNull(index) ?: 0) - (rightComponents.getOrNull(index) ?: 0)
            if (difference != 0) return difference.compareTo(0)
        }
        return 0
    }

    /** `true` solo cuando [candidate] es estrictamente posterior a [installed]. */
    fun isNewer(candidate: String?, installed: String?): Boolean =
        compare(candidate, installed) > 0

    /** Primer bloque de dígitos separados por puntos (`4`, `4.7`, `4.7.1`). */
    private val VERSION_PATTERN = Regex("""\d+(?:\.\d+)*""")
}
