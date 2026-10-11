package com.liebeblack.divtrack.presentation.dashboard

import com.liebeblack.divtrack.presentation.common.UiText

/**
 * Intenciones del usuario. MVI estricto: la UI nunca muta estado, solo describe lo que pasó.
 */
sealed interface DashboardIntent {

    /** Pull-to-refresh o botón de la barra superior. */
    data object Refresh : DashboardIntent

    /** Reintento desde el estado de error (dato vacío + sin conexión). */
    data object Retry : DashboardIntent

    /**
     * La pantalla volvió al primer plano (arranque o regreso desde segundo plano).
     *
     * No es un refresco a ciegas: el ViewModel mira la edad real del dato que se está
     * mostrando y solo sale a la red si ya merece la pena. Volver a la app después de horas
     * no puede dejar la tasa vieja en pantalla, y volver a los dos minutos no debe gastar
     * radio para nada. La otra mitad de la regla es el último intento: si falló (no hay
     * conexión, el proveedor no respondió), volver a la pantalla **sí** reintenta aunque el
     * dato mostrado todavía esté dentro de la ventana de frescura, para que la app no siga
     * anunciando "sin conexión" cuando la conexión ya volvió.
     */
    data object OnResumed : DashboardIntent
}

/** Eventos de un solo uso (nunca estado persistente). */
sealed interface DashboardEffect {

    data class ShowMessage(val text: UiText) : DashboardEffect
}
