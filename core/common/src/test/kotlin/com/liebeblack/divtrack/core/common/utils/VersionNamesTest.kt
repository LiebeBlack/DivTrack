package com.liebeblack.divtrack.core.common.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Comparación de versiones: es la única pieza de decisión del módulo de actualizaciones que
 * se puede fijar sin Android ni red, y la que decide si al usuario se le ofrece algo.
 *
 * Los casos no son inventados: el título real del release rolling es
 * `DivTrack 4.7 (latest)` y su etiqueta es `latest`, sin números.
 */
class VersionNamesTest {

    @Test
    fun `extrae los componentes de los nombres reales`() {
        assertEquals(listOf(4, 7), VersionNames.componentsOf("DivTrack 4.7 (latest)"))
        assertEquals(listOf(4, 7, 1), VersionNames.componentsOf("v4.7.1"))
        assertEquals(listOf(10, 0, 3), VersionNames.componentsOf("10.0.3"))
    }

    @Test
    fun `un nombre sin numeros no declara ninguna version`() {
        assertEquals(emptyList<Int>(), VersionNames.componentsOf("latest"))
        assertEquals(emptyList<Int>(), VersionNames.componentsOf(""))
        assertEquals(emptyList<Int>(), VersionNames.componentsOf(null))
    }

    @Test
    fun `compara por componentes y no como texto`() {
        // El error clásico: "4.10" < "4.7" comparando cadenas. Como versiones, 4.10 es mayor.
        assertTrue(VersionNames.isNewer("4.10", "4.7"))
        assertFalse(VersionNames.isNewer("4.7", "4.10"))
        assertTrue(VersionNames.isNewer("4.7.1", "4.7"))
    }

    @Test
    fun `los componentes que faltan cuentan como cero`() {
        assertEquals(0, VersionNames.compare("4.7", "4.7.0"))
        assertFalse(VersionNames.isNewer("4.7.0", "4.7"))
    }

    @Test
    fun `sin dato no se inventa un orden`() {
        assertEquals(0, VersionNames.compare("latest", "4.7"))
        assertEquals(0, VersionNames.compare("4.7", null))
        assertFalse(VersionNames.isNewer(null, "4.7"))
    }

    @Test
    fun `la primera version legible gana en el orden de los candidatos`() {
        assertEquals("4.7", VersionNames.firstVersionOf("DivTrack 4.7 (latest)", "## DivTrack 4.7"))
        assertEquals("5.0", VersionNames.firstVersionOf(null, "## DivTrack 5.0"))
        assertEquals(null, VersionNames.firstVersionOf("latest", null))
    }
}
