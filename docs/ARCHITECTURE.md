# Arquitectura de DivTrack

## 1. Módulos y por qué están separados así

Ocho módulos Gradle. La separación no es decorativa: **Gradle impone las reglas**, así que
una violación de Clean Architecture no es un comentario en una revisión de código, es un
error de compilación.

```
:core:common      (Kotlin/JVM puro)  Result<T>, DataError, formateo, parser de importes, reloj
:core:network     (Android library)  Retrofit ×5 (4 proveedores + GitHub), interceptores, RateProvider + registro
:core:database    (Android library)  Room: tasa vigente + cierre diario
:core:datastore   (Android library)  DataStore: preferencias del usuario
:domain           (Kotlin/JVM puro)  modelos, contratos, 9 casos de uso
:data             (Android library)  repositorios, orquestación multi-proveedor, WorkManager
:presentation     (Android library)  Compose + MVI + tema + Navigation 3
:app              (Android app)      Application, MainActivity, recursos, R8
```

Además, `lite/` es un **subproyecto Gradle independiente** (Java + XML, API 14+) que publica la
tasa BCV y que CI compila con `./gradlew -p lite :app:assembleRelease`. No forma parte de estos
ocho módulos: tiene su propio `settings.gradle.kts` y no comparte código con el grafo principal
(ADR 27).

```
:app ─▶ :presentation ─▶ :domain ◀─ :data ─▶ :core:network
                                      │      ├─ :core:database
                                      │      └─ :core:datastore
                                      └──────▶ :core:common
```

Lo que esto garantiza, en la práctica:

| Regla | Cómo se cumple |
|---|---|
| El dominio no conoce Android | `:domain` usa el plugin `kotlin-jvm`: `android.*` no está en su classpath |
| El dominio no conoce red ni base de datos | `:domain` no depende de `:core:network` ni `:core:database` |
| La UI no conoce Retrofit ni Room | `:presentation` depende solo de `:domain` y `:core:common` |
| El núcleo no se contamina | `:core:common` también es `kotlin-jvm` |

`androidx.room` y `androidx.datastore` exponen contratos *puros* (`RateLocalDataSource`,
`UserPreferencesDataSource`) con la implementación al lado. Por eso `:data` sabe persistir
sin depender de las clases de la plataforma y sus tests corren en JVM sin emulador.

---

## 2. Patrón Online-First

**Room es la única fuente de verdad para la UI. La red nunca lee: solo escribe.**

```
UI ──observe──▶ Room ──emite──▶ ViewModel ──state──▶ Compose
                 ▲
        upsert   │
Proveedores ─────┘   (DolarAPI VE, Yadio, ExchangeRate-API)
```

Consecuencias reales:

1. **Nunca hay estado "cargando" que borre la pantalla.** La UI ya tiene datos locales.
2. **Un fallo de red no modifica nada.** El repositorio devuelve `Result.Error` y no toca
   Room; la pantalla conserva sus tarjetas y aparece el aviso **con la causa real** (sin
   conexión, timeout o el código HTTP del proveedor), no siempre "sin conexión".
3. **La sincronización de fondo actualiza las tres pantallas sin código extra:** escribe en
   Room, Room re-emite, Compose repinta.

### Resolución por tasa, no por proveedor

`ProviderRegistry` recorre los proveedores en orden de prioridad y guarda, para cada fuente,
**el dato más fresco**: si un proveedor de cola publica una marca de tiempo posterior a la ya
resuelta, la reemplaza. Después de cada proveedor comprueba si ya tiene las dos tasas **y
frescas** (un dato viejo del banco que dejó de publicar no corta la pasada) y, si es así, para.
Esto significa que:

- DolarAPI caído → el paralelo sigue llegando por Yadio y solo se degrada el oficial.
- DolarAPI "sano" pero sirviendo el cierre de ayer → ExchangeRate-API trae el del día y gana
  el más fresco, no el de mejor prioridad (ADR 26).
- Un proveedor responde en vivo **sin marca de tiempo** (Yadio hoy sí manda su `timestamp` y
  la app lo usa; esto queda como red de seguridad si algún día dejara de mandarlo) → si el dato
  resuelto ya está rancio, su lectura reemplaza al cierre viejo en lugar de descartarse. Sin
  hora, en cambio, nunca pisa a un dato fresco (ADR 36).
- Los fallos no se propagan: se acumulan en `ProviderFailure` y la sincronización se marca
  como *parcial*, que es un aviso suave y no un error.

### Single-flight

`RateRepositoryImpl` serializa las sincronizaciones con un `Mutex`. El arranque, el
pull-to-refresh y WorkManager no pueden disparar tres pasadas simultáneas contra las APIs.

---

## 3. MVI por pantalla

Cada pantalla tiene el mismo contrato, sin excepciones:

```
XxxContract.kt    intent (lo que el usuario hizo) + effect (evento de un solo uso)
XxxUiState.kt     estado inmutable (@Immutable) ya formateado para pintar
XxxViewModel.kt   reduce: intent + fuentes de datos -> estado; estado -> SharedFlow de efectos
XxxScreen.kt      XxxRoute (única función que toca el ViewModel) + XxxScreen (sin estado)
```

- **`StateFlow<UiState>`** para el estado. `@Immutable` + campos primitivos/enums para que
  Compose pueda saltarse recomposiciones.
- **`SharedFlow<Effect>`** con `extraBufferCapacity = 1` y `DROP_OLDEST` para snackbars y
  portapapeles: son eventos, no estado, y no deben repetirse al rotar.
- Los avisos breves de dashboard, calculadora y ajustes se muestran centrados en la parte
  superior, debajo de la barra de la pantalla, mediante un host compartido; no requieren
  permisos de notificación del sistema.
- La bienvenida usa insets seguros y contenido desplazable: se centra cuando cabe en el
  alto disponible y conserva el botón accesible al desplazarse en pantallas compactas o con
  escala de fuente grande. La navegación inferior mantiene etiquetas visibles, estados
  seleccionados contrastados y tipografía de la escala común.
- **El ViewModel nunca toca `Context`.** Los textos viajan como `UiText.Res(id)` y se
  resuelven en Compose: así los ViewModel se testean en JVM pura.
- **Los composables de UI no reciben ViewModel.** Solo estado y lambdas; `key` estable en
  las listas.

### Estados y cálculos

- Panel: `spread` derivado una sola vez por emisión de Room.
- Calculadora: `combine` de 5 flujos + `stateIn(WhileSubscribed(5 s))`. Nadie observa =
  nadie calcula. Las opciones del selector se recuerdan mientras las tasas no cambian y las
  rutas estabilizan sus callbacks; `derivedStateOf` no se usa para envolver el estado entero,
  porque no evita recomponer el padre cuando cambia el importe.
- Ajustes: reflejo directo de DataStore (`map` + `stateIn`); no hay estado local duplicado,
  así que la preferencia y la pantalla no pueden desincronizarse.
- Errores: el fallo viaja tipado (`DataError`) y se traduce a un texto por causa
  (`DataError.toUiText()`). El estado de UI guarda el texto ya resuelto y si el problema fue
  de conectividad, que es lo que decide si se ofrece "Abrir ajustes de red".
- Cada ViewModel de tasas reutiliza la sincronización que ya tiene activa, en vez de encolar
  otro refresco por cada toque. El `Mutex` del repositorio serializa además las escrituras de
  sincronizaciones que llegan desde pantallas distintas o WorkManager. Los indicadores se
  limpian también ante cancelación o excepción.
- El worker periódico devuelve `retry()` ante errores recuperables y excepciones de
  sincronización, dejando que el backoff exponencial de WorkManager limite los reintentos
  sin desactivar permanentemente la tarea tras un número fijo de fallos. La cancelación
  estructurada se propaga. La reconciliación del scheduler lee preferencias y aplica el
  cambio dentro de una sección exclusiva para evitar que una programación antigua gane una
  carrera con la preferencia más reciente.
- El intervalo predeterminado de WorkManager es de cuatro horas y sigue siendo configurable
  (15 min, 30 min, 1 h, 2 h o 4 h). El trabajo periódico es persistente y sobrevive a la
  muerte normal del proceso; Android decide la ventana efectiva según red, batería y
  restricciones del fabricante. No se ejecuta mientras el usuario haya forzado la detención
  de la app, hasta que vuelva a abrirla.
- Compose delega el renderizado acelerado a la canalización gráfica de Android. No se fijan
  hilos a núcleos ni se selecciona GPU por fabricante (MTK/Qualcomm): esa asignación depende
  del sistema y forzarla desde la app perjudicaría compatibilidad y eficiencia energética.
- El tema claro usa tonos principales y de error con contraste suficiente para texto de
  botones; los estados de error y avisos se adaptan al ancho disponible y el refresco muestra
  progreso sin permitir toques que no pueden iniciar otra pasada.
- Dashboard, calculadora y ajustes centran el contenido con un ancho máximo en pantallas
  amplias. Los selectores fluyen a varias filas en lugar de comprimir sus opciones; los
  resultados de la calculadora cambian a disposición vertical en pantallas estrechas o con
  escala de texto grande. La tipografía sigue respetando el tamaño de fuente del sistema.
- En las tarjetas de tasas, el oficial es el foco visual, obligatorio y centrado, con
  tipografía de peso Black y acento verde; el paralelo usa tonos topo discretos cuando se habilita.
  Las tendencias se muestran en cápsulas de color y sin sombras ni animaciones costosas.
- La tasa paralela queda oculta por defecto y el usuario puede activarla desde Ajustes; el
  valor se persiste en DataStore. La tarjeta oficial permanece prioritaria y centrada; si el
  proveedor aún no la entrega, se mantiene su tarjeta con estado no disponible.
- El tema oscuro usa superficies verde bosque y el tema claro una paleta crema cálida; ambos
  mantienen el verde dólar como acento principal y contraste legible en textos. Se usan
  superficies tonales y bordes suaves en lugar de blur o transparencias animadas para no
  añadir coste de GPU en dispositivos modestos.
- El desglose de calculadora presenta totales y el IGTF estimado en bolívares, sin exponer
  subtotales netos en la tarjeta ni en el texto copiado. La cifra es informativa y no implica
  validación o aval del BCV/SENIAT; la aplicación del impuesto depende de la operación y la
  normativa vigente.
- Al abrir el panel, la UI mantiene un esqueleto estático con el texto de carga mientras
  intenta sincronizar con las APIs. Room se observa desde el inicio para que la caché y el
  estado offline estén disponibles; la UI retiene la carga inicial hasta concluir la primera
  sincronización para evitar destellos de datos antiguos. Si falla la red, se revela el último
  dato persistido; los refrescos manuales conservan visibles las tarjetas existentes.
- La bienvenida informativa se presenta solo si DataStore confirma que no se completó.
  La confirmación se persiste antes de abrir las pestañas; mientras se leen preferencias
  se muestra una carga breve para decidir con el estado persistido, sin parpadeos de navegación.

---

## 4. Capa de red

Un solo `OkHttpClient` compartido (pool de conexiones y caché de disco de 10 MB) y cinco
instancias de `Retrofit` que solo cambian de `baseUrl`: las de los cuatro proveedores de
(DolarAPI, Yadio, ExchangeRate-API y DolarVZLA) y la de la API de GitHub que usa el módulo
de actualizaciones.

Orden de interceptores, de fuera hacia dentro:

1. **HeadersInterceptor** — `Accept`, `Accept-Language: es-VE`, `User-Agent`.
2. **CacheFallbackInterceptor** — si la red falla o recibe 408, 429 o 5xx, intenta servir
   una respuesta guardada en disco; si no hay una utilizable, conserva el error o respuesta
   original. Los 504 sintéticos de una caché vacía no activan reintentos de red.
3. **RetryInterceptor** — backoff exponencial con jitter ante `IOException`, 408, 429 y 5xx,
   respetando `Retry-After` (acotado a 3 s para no castigar al usuario).
4. **HttpLoggingInterceptor** — solo en debug, y solo cabeceras.

`NetworkErrorMapper` es el **único** sitio del proyecto que conoce `HttpException`,
`SerializationException`, etc. De ahí hacia arriba todo habla `Result<T>` + `DataError`.

Además, `RateRepositoryImpl` consulta `ConnectivityObserver` **antes** de lanzar la petición:
si no hay red, devuelve el error de inmediato en lugar de esperar al timeout. El usuario ve el
aviso instantáneo y el dispositivo no gasta radio ni batería intentándolo.

Piezas que hacen que ese atajo no se vuelva en contra:

- `ConnectivityObserver` solo dice "no hay red" cuando **no hay ninguna red activa**. En
  cualquier duda responde "sí" y deja decidir a OkHttp: un falso negativo aquí dejaría la app
  sin datos con wifi perfecta, que es exactamente el fallo que había.
- `callTimeout` de 20 s en el `OkHttpClient`: la petición completa tiene techo, así que ni los
  reintentos ni la caché pueden alargar el spinner más de la cuenta.
- `CacheFallbackInterceptor` propaga el **error original** cuando no hay nada que cachear, en
  lugar de un 504 sintético (que haría decir "el proveedor está fallando" cuando el problema
  era la red del teléfono).

---

## 5. Rendimiento (gama baja, 60 fps)

| Decisión | Motivo |
|---|---|
| Sin sombras: superficies planas con borde de 1 dp | La elevación se dibuja en cada fotograma |
| Esqueleto de carga estático, sin shimmer | Una animación infinita consume presupuesto de fotogramas para nada |
| Flechas de tendencia dibujadas con `Path` | Más barato que medir y componer un `ImageVector` |
| `entryProvider` dentro de un `remember` | Un grafo nuevo por recomposición obliga a `NavDisplay` a re-resolver la entrada activa |
| `DecimalFormat` reutilizado por hilo y formato | Se construía uno por cada cifra formateada (decenas por segundo al teclear) |
| LeakCanary apagado por defecto y análisis en otro proceso | Vigilaba cada objeto en el APK de uso diario; su análisis tardaba 73 s en un gama baja |
| `callTimeout` de 20 s | Sin techo global, tres intentos podían dejar la pantalla girando casi un minuto |
| `@Immutable` en los modelos de estado | Saltos de recomposición reales |
| `key` por fuente en las listas | Actualizar una tasa no recompone la otra tarjeta |
| `keyboardType = Decimal` y saneado en el ViewModel | El campo no pelea con el cursor y el cálculo no bloquea el hilo de UI |
| Caché HTTP + WorkManager con restricción de red | Cero despertares inútiles del proceso |
| ViewModels acotados por `NavEntry` | Al salir de una pestaña, su ViewModel se limpia |

Las métricas del compilador de Compose se generan en CI (`-PcomposeMetrics=true`) y se
publican como artefacto: son la prueba objetiva de estabilidad (clases inestables, etc.).

---

## 6. Navegación (Navigation 3)

Tres pestañas planas sin jerarquía. `NavDisplay` con un back stack persistente
(`rememberNavBackStack`), decoradores de estado guardado y de `ViewModelStoreOwner` por
entrada, y `entryProvider` tipado con claves `@Serializable` (`data object : NavKey`).

Cambiar de pestaña mantiene la pila acotada a `[raíz, pestaña]`: "atrás" vuelve al panel de
tasas y, desde ahí, sale de la app — el comportamiento que Android espera de una barra de
navegación, sin pilas que crezcan sin límite.

El tema se observa **por encima** de la navegación (`AppViewModel` con `SharingStarted.Eagerly`),
así que cambiarlo en Ajustes repinta toda la app al instante.

---

## 7. Inyección de dependencias

Hilt en todo el proyecto. Módulos relevantes:

| Módulo Hilt | Provee |
|---|---|
| `NetworkModule` / `NetworkBindingsModule` | `Json`, `Cache`, `OkHttpClient`, 3 `Retrofit`, servicios, `RateProvider` en `Set` (`@IntoSet`), `ConnectivityObserver`, `Logger` |
| `DatabaseModule` + bindings | `DivTrackDatabase`, `RateLocalDataSource` |
| `DataStoreModule` + bindings | `DataStore<Preferences>` (una sola instancia por proceso), `UserPreferencesDataSource` |
| `DispatchersModule` | `@IoDispatcher`, `@DefaultDispatcher` (sustituibles en tests) |
| `DataModule` | `TimeProvider` (reloj en hora de Venezuela) |
| `RepositoryModule` | contratos de `:domain` → implementaciones de `:data` |

Añadir un proveedor de tasas nuevo = implementar `RateProvider` y registrarlo en
`NetworkModule`. Ni el dominio ni la UI cambian.
