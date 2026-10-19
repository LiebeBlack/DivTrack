# Decisiones de arquitectura (ADR)

Cada entrada responde a la misma pregunta: **qué se eligió, qué se descartó y qué cuesta
esa elección**. Las decisiones discutibles se marcan como tales.

---

## 1. AGP 9.4 con Kotlin integrado (built-in Kotlin)

**Decisión.** Toolchain de 2026: AGP 9.4.0, Gradle 9.6.0, Kotlin 2.4.20 sin el plugin
`org.jetbrains.kotlin.android`.

**Contexto.** AGP 9 aporta el compilador de Kotlin integrado y el DSL nuevo
(`android.newDsl`). El plugin `kotlin-android` ya no se aplica y `kotlinOptions {}` no
existe: se usa `kotlin { compilerOptions { jvmTarget } }`.

**Alternativa descartada.** Quedarse en AGP 8.13 + Kotlin 2.2.20 (camino conservador).
Se descartó porque el objetivo era el stack más reciente, y porque con AGP 8 el wiring de
KSP/Kotlin es el clásico que ya nadie va a mejorar.

**Coste y mitigación.** AGP 9 es joven y su combinación con KSP/Hilt/Nav3 puede tener
aristas. Por eso:
- Todas las versiones están fijadas a artefactos que existen en Google Maven / Maven Central.
- `gradle.properties` documenta la **válvula de escape**: `android.builtInKotlin=false` y
  `android.newDsl=false` desactivan el modo nuevo sin bajar de AGP 9.
- El compilador de Compose se aplica con `org.jetbrains.kotlin.plugin.compose`, en la misma
  versión que Kotlin (2.4.20), que es lo que documenta la guía oficial del compilador para
  proyectos con AGP 9.

---

## 2. Navigation 3 en lugar de Navigation Compose

**Decisión.** Nav3 1.2.0 con `NavDisplay`, `rememberNavBackStack` y claves `@Serializable`.

**Motivo.** El back stack es explícito y propiedad de la app (nada de rutas por cadena ni
`NavHost`), y el grafo se resuelve con `entryProvider` tipado.

**Coste.** Hay que añadir `androidx.lifecycle:lifecycle-viewmodel-navigation3` para que cada
entrada tenga su propio `ViewModelStoreOwner`; sin ese decorador, los ViewModel quedarían
atados a la Activity y no se limpiarían al salir de una pestaña.

**Nota de diseño.** Tres pestañas planas (Tasas, Calculadora, Ajustes): el back stack se
mantiene acotado a `[raíz, pestaña actual]`, de modo que "atrás" vuelve al panel de tasas y
desde ahí sale de la app. No se registra un `BackHandler` propio: `NavDisplay` ya gestiona el
gesto y añadir otro provocaría una doble pulsación (pila vacía y contenido en blanco, un bug
visible).

**Rendimiento.** El `entryProvider` se construye dentro de un `remember`: si se creara en
cada recomposición, `NavDisplay` recibiría un grafo nuevo cada vez y volvería a resolver la
entrada activa (scroll perdido, pestaña reconstruida). Es un detalle pequeño con un efecto
visible en gama baja.

---

## 3. Room como única fuente de verdad (Online-First)

**Decisión.** Toda lectura de la UI viene de Room. La red solo escribe.

**Alternativas descartadas.**
- *Repository con caché en memoria*: el usuario perdería los datos al cerrar la app y
  habría que inventar estados "cargando" que no aportan nada.
- *Mostrar directamente la respuesta de la red*: si la API cambia un campo, la pantalla se
  queda vacía; y sin conexión no habría nada que ver.

**Consecuencia.** Hay un pequeño coste de escritura en cada sincronización y, a cambio, la
app abre con datos, funciona sin conexión y **nunca** pierde lo que ya tenía por un fallo
de red.

---

## 4. Multi-proveedor con resolución por tasa

**Decisión.** `ProviderRegistry` resuelve **cada tasa** con el primer proveedor (por
prioridad) que la publique, y no exige que un mismo proveedor responda todo.

**Evidencia recogida antes de decidir** (probado en vivo, 2026-09-30):

| Fuente | Resultado | Decisión |
|---|---|---|
| `ve.dolarapi.com/v1/dolares` | 200 · oficial 860,17 · paralelo 955,35 (reverificado 2026-10-01) | proveedor primario |
| `api.yadio.io/exrates/USD` | 200 · `USD.VES` presente en el mapa de divisas | respaldo del paralelo (es la fuente upstream) |
| `pydolarve.org` | no resuelve por DNS | descartada |
| `api.dolarvzla.com` | 401, requiere clave | descartada |

**Consecuencia.** Que una API caiga degrada una tasa, no la app. Los fallos se registran como
`ProviderFailure` y la sincronización se marca como parcial (aviso suave).

---

## 5. (Retirada) Semántica del IGTF documentada y probada

> Retirada por la ADR 42: la calculadora ya no aplica IGTF. Se conserva como registro
> histórico de la decisión que estuvo vigente hasta 2026-10-19.

**Decisión.** En `USD → Bs` el IGTF se **suma** al neto (`totalBs = netBs × 1,03`); en
`Bs → USD` el monto tecleado **es** el total y el impuesto se **descuenta** para hallar el
neto (`netUsd = totalUsd / 1,03`).

**Por qué.** Es la confusión más habitual en apps de este tipo y el punto donde el usuario
detecta un error de inmediato. Está documentado en el KDoc de `Conversion` y cubierto por
tests con el ejemplo exacto del pliego: 10 USD a 36,5 Bs con IGTF → netBs 365,00,
IGTF 10,95, total **375,95 Bs**.

---

## 6. Parser de importes tolerante, no máscara de texto

**Decisión.** El campo acepta `1234,56`, `1.234,56`, `1,234.56` y `1234.56`; el saneado
ocurre en el ViewModel (`NumberParsing.sanitizeAmountInput`) mientras el texto del campo se
deja intacto.

**Alternativa descartada.** Reformatear el texto en cada tecla desde la UI: el cursor salta
y la selección se rompe. Se prefirió un parser listo para la realidad venezolana y un campo
que se comporta como el usuario espera.

**Consecuencia.** `looksLikeDecimal` documenta la ambigüedad real (`1.234` se lee como mil
doscientos treinta y cuatro) y hay tests para cada caso.

---

## 7. Sin color dinámico (Material You)

**Decisión.** La paleta es fija; `dynamicColor` no se usa.

**Motivo.** La identidad visual (menta/ámbar, superficies planas, moderado) es parte del
producto: una app financiera que cambia de color con el fondo de pantalla del usuario
pierde jerarquía justo donde más importa (verde/rojo de tendencia, acento por tasa).

---

## 8. Manrope con dígitos tabulares

**Decisión.** Manrope variable (OFL) con `fontFeatureSettings = "tnum"` en todo lo que son
cifras.

**Motivo.** Los dígitos tabulares impiden que el valor "baile" cuando una tasa se actualiza
en vivo: es la diferencia visual entre una app cuidada y una que no lo está.

**Licencia.** `licenses/Manrope-OFL.txt`; el archivo OFL **no** se empaqueta en `res/`
(AAPT rechaza un `.txt` en una carpeta de recursos: fue un error de empaquetado real que se
corrigió moviéndolo a `licenses/`).

---

## 9. Iconos: `material-icons-core` + `extended` solo para pestañas

**Decisión.** La UI usa los iconos del paquete *core* (Refresh, Clear, Share, Warning, Info)
y se añade *extended* únicamente para las tres pestañas (Calculate, TrendingUp, Settings).

**Motivo.** Los iconos de pestaña son la primera impresión de la app y merecen el icono
correcto; el resto del proyecto no necesita 10 000 vectores. R8 elimina en release todo lo
que no se usa, así que el coste real en el APK final son tres rutas.

---

## 10. DataStore provisto por DI, no por delegado

**Decisión.** `DataStore<Preferences>` se crea con `PreferenceDataStoreFactory` y se provee
como `@Singleton`, en lugar de usar `by preferencesDataStore` en un `Context`.

**Motivo.** Dos instancias sobre el mismo archivo hacen que DataStore lance
`IllegalStateException`. Proveerlo por DI garantiza una sola por proceso y permite
sustituirlo en tests.

---

## 11. Fuera la sección de Histórico; el cierre diario se queda

**Decisión.** Se elimina el Histórico como funcionalidad **completa**: pantalla, gráfico
(`RateLineChart`), selector de rangos (1M/3M/YTD/1A), tarjetas de estadísticas, pestaña en la
barra inferior, casos de uso (`ObserveHistoryUseCase`, `SyncHistoryUseCase`), modelos de
dominio (`HistoryRange`, `RateHistoryPoint`), el endpoint `v1/historicos/dolares/{fuente}`,
su DTO y toda la cadena de importación con TTL.

**Lo que NO se elimina, y por qué.** La tabla `rate_history` (dos filas por día, una por
fuente) sigue existiendo y el repositorio sigue escribiendo en ella el cierre del día: es la
única fuente del "cierre anterior" que alimenta la flecha de tendencia y la variación
porcentual del panel. Borrarla dejaría la tendencia plana **para siempre**, que es peor que
no tener gráfico. Lo que se eliminó es la pantalla y su importación; el dato diario se quedó
porque sostiene otra funcionalidad que el usuario sí usa.

**Nombre conservado a propósito.** La clase sigue siendo `RateHistoryEntity` y la tabla
`rate_history` aunque el concepto ya no sea "histórico": renombrarlos obligaría a una
migración de Room y la ADR 16 ya decidió que este proyecto no cambia el esquema sin migración
escrita. Los métodos que quedan sí hablan claro: `upsertDailyCloses` / `pruneDailyCloses`, y
la retención (2 años) viaja con la sincronización en lugar de vivir en un trabajo aparte, así
que no existe ninguna ruta que escriba cierres sin aplicarles el límite.

**Efecto colateral bueno.** Desaparecen del contrato público `observeHistory` y `syncHistory`,
y con ellos un `Flow` de Room que nadie consumía, un TTL de 24 h y una poda separada.

---

## 12. Copia de seguridad: fuera la base de datos

**Decisión.** Auto-backup y transferencia entre dispositivos **excluyen** la base de Room y
la caché HTTP; las preferencias sí se respaldan.

**Motivo.** Restaurar en otro dispositivo una base de tasas vieja mostraría datos caducados
como si fueran actuales. Las tasas se vuelven a descargar en segundos; el tema y la
frecuencia de sincronización son del usuario y sí viajan.

---

## 13. Hora de Venezuela inyectada, no la del dispositivo

**Decisión.** Todo cálculo de "día" y "cierre anterior" usa `America/Caracas` a través de un
`TimeProvider` inyectable.

**Motivo.** Un usuario de viaje (o con la zona del teléfono mal configurada) no debe ver el
cierre de otro día ni una flecha de tendencia equivocada. Además, los tests fijan el reloj y
dejan de depender del día en que se ejecutan.

---

## 14. `minSdk 31`

**Decisión.** API 31 (Android 12) como mínimo.

**Motivo.** El proyecto apunta a una experiencia moderna y sin ramas de compatibilidad:
Android 12 es el primer nivel con el arranque y el theming del sistema ya unificados, incluye
`java.time` sin *desugaring* y cubre el parque de dispositivos que puede ejecutar la app con
las APIs de rendimiento que usa (splashes nativos, edge-to-edge, blur y animaciones del
sistema). Subir el mínimo permite, además, reducir el APK al no arrastrar código de
compatibilidad para versiones que ya no reciben soporte.

---

## 15. `UiText` en lugar de `Context` en los ViewModel

**Decisión.** Los eventos one-shot llevan `UiText.Res(id)`, no texto ya resuelto.

**Motivo.** Un ViewModel que toca `Context` deja de ser testeable en JVM pura. Con `UiText`,
el snackbar de "Sin conexión. Mostrando última actualización" se prueba con un simple
`assertEquals` sobre el id de recurso.

---

## 16. Room con esquema exportado y sin migración destructiva

**Decisión.** `exportSchema = true` con el esquema versionado en `core/database/schemas`, y
**sin** `fallbackToDestructiveMigration`.

**Motivo.** En una app financiera, perder datos en silencio no es una opción. Tener el JSON
de la versión 1 commiteado permite escribir la migración real cuando haya versión 2.

---

## 17. Tests de flujos sin Turbine

**Decisión.** Los tests de `SharedFlow`/`StateFlow` recolectan con un
`UnconfinedTestDispatcher` y una lista propia; se eliminó la dependencia de Turbine.

**Motivo.** Un `SharedFlow` con `replay = 0` **descarta** lo emitido antes de que alguien se
suscriba: suscribirse primero y avanzar el reloj después es determinista y no depende de
tiempos. Menos dependencias y menos sorpresas intermitentes en CI.

---

## 18. Consultar la conectividad antes de sincronizar

**Decisión.** Si `ConnectivityObserver` informa que no hay red, el repositorio devuelve el
error de inmediato y no llama a ningún proveedor.

**Motivo.** Con solo el timeout de OkHttp (10 s), el usuario mira un spinner inútil y el
móvil gasta radio antes de enterarse de algo que el sistema ya sabía. Con esta comprobación
el aviso "Sin conexión. Mostrando última actualización" aparece al instante.

**Consecuencia.** El repositorio gana una dependencia más, así que los tests inyectan un
observador de conectividad simulado (y hay un test que verifica que **no** se consulta a los
proveedores cuando no hay red).

---

## 19. Sin código muerto

**Decisión.** Se eliminaron las APIs que no usaba nadie: `observeRate`, `observeLastSyncAt`,
`latestHistoryEpochDay`, `clearCurrentRates`, `enqueueImmediate`, las extensiones de flujo
`asResultFlow`/`mapCatchingAsResult` y la constante `DEFAULT_HISTORY_RANGE_MONTHS`.

**Motivo.** Una API pública no usada no es "por si acaso": es superficie que hay que
mantener, documentar y migrar. Si vuelve a hacer falta, se añade en una línea.

**Ampliación (revisión estricta posterior).** La regla se aplicó otra vez, leyendo el código sin
ejecutar nada, y cayeron: `EmptyState` y las claves `empty_rates_*` (el estado que pintaban es
inalcanzable: la app nunca queda sin datos y sin error), `ConnectivityObserver.observe()` y su
`callbackFlow` (nadie consumía el `Flow`; la app solo consulta `isOnline()`),
`UserPreferences.lastSyncAtMillis` / `setLastSyncAt` / la clave `last_sync_at` (no los escribía
ni los leía nadie), los colores `Sky`/`SkyDeep` y los campos `netUsdText`/`netBsText` del estado
de la calculadora (se calculaban y ningún composable los pintaba; el estado ahora expone
exactamente lo que la pantalla muestra) y `Spacing.xxl` (se quedó sin consumidor al eliminar
`EmptyState`: código muerto de segundo orden, que es la razón por la que cada borrado se verifica
con una búsqueda en lugar de darlo por hecho).

**Verificación.** Se hizo con búsquedas mecánicas (referencias colgantes = 0) y no a ojo.

---

## 20. El selector de la calculadora refleja lo que existe

**Decisión.** El selector de tasa se construye con `state.rateOptions` (lo que hay de verdad
en Room) y no con la lista completa de `RateSource`.

**El bug que corrige.** Antes, si un proveedor no había publicado el oficial todavía, el
usuario podía pulsar "Oficial" y la pantalla respondía "Sin tasas disponibles": la app le
offrecía algo que no tenía. Ahora esa opción simplemente no está, y la tasa por defecto cae
en una disponible.

**Por qué se detectó tarde.** El ViewModel ya calculaba `rateOptions`; era la pantalla la que
lo ignoraba y volvía a inventarse la lista. Es el motivo por el que el estado de UI expone
los datos "ya resueltos": si la pantalla puede recalcular una decisión, acaba
desincronizándose.

**Residuo de la misma clase.** Aun después de aquel arreglo, la pantalla conservaba un
`ifEmpty { RateSource.ordered() }` para el caso de Room vacío: con cero tasas volvía a ofrecer
las dos, y pulsar una dejaba el mismo "sin tasas disponibles". Se eliminó en la revisión
estricta posterior: sin tasas no hay chips que pulsar y el texto de ayuda explica qué hacer.

---

## 21. El leak de `SystemJobService` es de Android, no nuestro

**El informe.** LeakCanary 2.14, SDK 33, proceso `com.liebeblack.divtrack.debug`:

```
GC Root: System class
├─ android.content.res.ResourcesImpl class
│    Leaking: NO (a class is never leaking)
│    Library leak match: static field android.content.res.ResourcesImpl#mAppContext
│    ↓ static ResourcesImpl.mAppContext
├─ android.app.ContextImpl instance
│    mOuterContext instance of androidx.work.impl.background.systemjob.SystemJobService
│    ↓ ContextImpl.mOuterContext
╰→ androidx.work.impl.background.systemjob.SystemJobService instance
     Leaking: YES (ObjectWatcher was watching this because ... received Service#onDestroy()
     callback and Service not held by ActivityThread)
```

**Lectura de la traza.** En toda la cadena **no aparece ni una clase de DivTrack**: la raíz es
una clase del framework, el nodo intermedio es un `ContextImpl` del framework y el objeto
vigilado es un `Service` de WorkManager que el sistema creó para ejecutar el trabajo
periódico. Además, LeakCanary **lo marca él mismo** como `Library leak match` en el borde
`ResourcesImpl.mAppContext`.

**Mecanismo real.** `ResourcesManager` (estático, en el proceso) cachea un `ResourcesImpl` por
`ResourcesKey`, y `ResourcesImpl.mAppContext` guarda una **referencia fuerte** al `ContextImpl`
que lo creó. Si el último `ResourcesImpl` de esa clave lo creó el contexto del servicio de
JobScheduler, el framework retiene ese `ContextImpl` —y con él el `Service`— hasta que se cree
otro `Resources` de la misma clave. Por eso el aviso aparece a veces y luego desaparece solo:
se autorepara. Es un comportamiento del framework (AOSP), no un patrón de la app.

**Qué se hizo y qué no.**

- Lo que **no** se hizo: silenciarlo llamando a `LeakCanary.config` desde el `Application`.
  La receta oficial (`referenceMatchers = AndroidReferenceMatchers.appDefaults + …`) exige
  una clase `Application` propia en `src/debug` registrada en el manifiesto, y esta app tiene
  su `Application` anotada con `@HiltAndroidApp`: Hilt genera el padre real de esa clase y
  meter otra en medio para *apagar un aviso* es cambiar la inicialización del grafo entero
  por ruido. No compensa el riesgo.
- Lo que **sí** se hizo: quitar de en medio el coste real del diagnóstico. LeakCanary está
  apagado por defecto y se enciende cuando se depura una fuga de verdad
  (`-Pdivtrack.leakcanary=true`); entonces analiza en un proceso aparte, de modo que el
  volcado y el análisis (medido: **73 s** en un gama baja) dejan de congelar la app. Ver
  ADR 23.

**Consecuencia.** Si al reactivar LeakCanary vuelve a aparecer **esta misma traza**, ya está
diagnosticada: es el framework reteniendo el último `Context` que creó `Resources`, y no hay
nada que arreglar en DivTrack. Cualquier traza nueva que **sí** mencione una clase propia
(ViewModel, repositorio, `Context` de la Activity) es otra historia y no se tapa con esta
conclusión.

---

## 22. Un error de red no es siempre "sin conexión"

**El síntoma.** Cualquier fallo acababa en el mismo texto: *"Sin conexión. Mostrando última
actualización"*. Un 404 del proveedor, un 503, un timeout o un cuerpo ilegible se contaban
como si el teléfono estuviera desconectado, así que el usuario revisaba su wifi mientras el
problema estaba en la API.

**Decisión.** El error viaja tipado ([DataError]) y se traduce a un texto por causa
(`DataError.toUiText()`), con el código HTTP incluido cuando lo hay. La app solo dice "sin
conexión" cuando el fallo es de conectividad de verdad.

**Y el fallo rápido se volvió prudente.** `ConnectivityObserver` se usa para no esperar un
timeout cuando no hay red, pero **no puede** dejar la app muda: ahora solo responde `false`
cuando el sistema no reporta **ninguna** red activa, y acepta como "con red" cualquier red con
transporte wifi/celular/ethernet/VPN aunque la ROM no marque `NET_CAPABILITY_INTERNET` (pasa
con VPN y portales cautivos). Un permiso revocado o una excepción al consultar el
`ConnectivityManager` tampoco bloquean la petición: se intenta y se muestra el error real.

**Tiempos acotados.** Se añadió `callTimeout` al `OkHttpClient`: la petición completa
(conexión + reintentos + lectura) tiene un techo de 20 s. Antes, tres intentos con timeouts de
10/15 s podían tener la pantalla girando casi un minuto. Además, `RetryInterceptor` ya no
reintenta fallos deterministas: un DNS que no resuelve o un problema de TLS devuelven el mismo
error 350 ms después, y el único resultado era esperar más y gastar más radio.

**La misma regla en segundo plano.** `RateSyncWorker` devolvía `retry()` para cualquier
`Result.Error`, así que un 404 (endpoint cambiado) o una respuesta ilegible se reintentaban con
backoff sin posibilidad de mejorar. Ahora solo se reintenta lo transitorio (red, timeout, HTTP
408/429/5xx); el resto marca el intento como fallido y deja que el siguiente ciclo periódico lo
intente de nuevo. Es la frontera que ya describía `docs/ARCHITECTURE.md`.

**Por qué importa el orden.** `CacheFallbackInterceptor` sirve de la caché HTTP cuando la red
falla, pero si no hay nada cacheado ahora lanza el **error original** en lugar de un 504
sintético: el 504 haría que la app dijera "la fuente está fallando" cuando el problema era la
red del teléfono.

---

## 23. Rendimiento: fuera lo que sobra, y a propósito

**Contexto.** La app se percibía "pesada, con lag, como sin aceleración por hardware". La
aceleración estaba activa (y ahora queda escrita en el manifiesto), así que el problema no era
la GPU: era trabajo evitable en el hilo principal y en el de composición.

**Medidas tomadas.**

| Qué | Antes | Ahora |
|---|---|---|
| LeakCanary en debug | siempre activo: vigilaba cada objeto y analizaba el heap en la app (73 s de análisis medidos) | apagado por defecto; se enciende con `-Pdivtrack.leakcanary=true` y analiza en otro proceso |
| Grafo de navegación | `entryProvider` reconstruido en cada recomposición | construido dentro de un `remember` |
| Formateo de cifras | un `DecimalFormat` nuevo **por llamada** (y uno por tecla de la calculadora) | uno por hilo y por formato; los `DateTimeFormatter` (inmutables) se crean una vez |
| Pestañas | `TopLevelDestination.entries.toList()` en cada pasada de la barra | lista construida una vez |
| Histórico | gráfico `Canvas` con la serie YTD y una pantalla entera que descargaba datos | funcionalidad eliminada (ADR 11) |
| Latencia de red | sin techo global: reintentos encadenados | `callTimeout` de 20 s y sin reintentos de fallos deterministas |
| Lambdas de intención | `viewModel::onIntent` nuevo por recomposición | referencia recordada en la ruta |

**Nota honesta sobre medir.** Un build `debug` de Compose es más lento por definición (sin R8,
sin optimizaciones de release) y LeakCanary añadía su parte. Para juzgar el rendimiento de
verdad hay que mirar el APK de release; el debug ya no lleva encima la vigilancia de objetos.

---

## 24. Permisos: dos, normales, y ni uno más

**Decisión.** La app declara exactamente `INTERNET` y `ACCESS_NETWORK_STATE`. Los dos son
permisos **normales**: no hay diálogo de runtime, no hay `ActivityResultContracts` y no hay
estado de permiso que gestionar porque nunca se pide nada al usuario.

**Por qué no hace falta pedir nada.** Consultar dos APIs HTTPS solo necesita `INTERNET`;
saber si hay red para no esperar un timeout necesita `ACCESS_NETWORK_STATE`. Ni ubicación, ni
contactos, ni almacenamiento, ni cámara, ni notificaciones (el worker de WorkManager no
publica ninguna notificación, así que `POST_NOTIFICATIONS` no aplica).

**El problema real que había.** El APK de debug declaraba `READ_EXTERNAL_STORAGE`,
`WRITE_EXTERNAL_STORAGE` y `POST_NOTIFICATIONS`: los tres venían del manifiesto fusionado de
LeakCanary, no de DivTrack. Un usuario que mira "Permisos" en los ajustes veía una app de
tasas pidiendo almacenamiento y notificaciones. Al apagar LeakCanary por defecto (ADR 23)
desaparecen solos, y lo que queda está documentado en el manifiesto: lo nuestro y lo que
aporta WorkManager (`WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED`, `FOREGROUND_SERVICE`), que se dejan
porque son de la librería y el trabajo periódico los necesita.

**Se cierra el tráfico en claro.** `usesCleartextTraffic="false"` + `network_security_config`
con `cleartextTrafficPermitted="false"`: si algún día un `baseUrl` pasa a `http://`, el fallo
aparece en el primer intento en lugar de mandar tasas sin cifrar.

**Y se le da salida al usuario.** Cuando el fallo es de conectividad, el estado de error ofrece
"Abrir ajustes de red" (una `Intent` del sistema, sin permisos). Un aviso de "sin conexión" sin
nada que pulsar deja al usuario atascado.

---

## 25. Tres fuentes con dialectos distintos y preferencia de orden

**Contexto.** DolarAPI y Yadio comparten upstream del mercado paralelo y los cortes de ruta
hacia Venezuela los han tumbado a ambos a la vez. Además, el sistema no distinguía el
"estilo de lectura" de cada fuente: cada proveedor habla su propio dialecto JSON y ese
conocimiento tiene que vivir encapsulado en su provider.

**Decisión.**

1. **Tercera fuente independiente.** ExchangeRate-API (`open.er-api.com`) entra como
   fallback del oficial: no comparte ruta ni upstream con las dos primeras. Los candidatos
   muertos en vivo se descartaron con evidencia: pyDolarVE (DNS sin resolver) y CriptoYa
   (HTTP 422). El endpoint quedó verificado devolviendo `rates["VES"]` y
   `time_last_update_unix` en **segundos** (la app trabaja en ms).

2. **Tres estilos de lectura, un contrato.** DolarAPI publica una *lista* por fuente
   (`promedio → venta → compra`); Yadio un *mapa* dentro de `USD`; ExchangeRate-API un
   *envoltorio con estado* (`result`) y mapa plano. Cada DTO + provider encapsula el suyo;
   `RateProvider` sigue siendo una interfaz de un método.

3. **El orden lo manda el usuario.** Sin preferencia: DolarAPI → Yadio → ExchangeRateAPI.
   Con preferencia: el elegido primero y el resto por prioridad; la pasada se corta cuando
   las dos tasas están resueltas. La preferencia vive en DataStore (`preferred_provider_id`,
   cadena vacía = automático) y el dominio solo ve `null` o un id de `ProviderIds`.

4. **Diagnóstico real en Ajustes.** `testProviders()` consulta las tres APIs de verdad, sin
   escribir nada: una respuesta HTTP "sana" sin pares utilizables cuenta como caída. La UI
   muestra qué tasa trajo cada fuente y "Fuentes OK: X de Y".

5. **Solo-wifi.** El trabajo periódico acepta `wifiOnly` y usa `NetworkType.UNMETERED`.

**Por qué no reordenar el mapeo en el registro.** Cada provider ya devuelve `RemoteRate`
con la clave canónica; el registro solo resuelve *quién primero*. Meter dialectos en el
registro acoplaría todas las fuentes entre sí: añadir la cuarta fuente tocaría un archivo
del registro en lugar de uno nuevo + una línea de DI.

---

## 26. El banco que dejó de publicar: frescura, breaker y diagnóstico

**Contexto.** DolarAPI puede responder 200 con JSON perfecto sirviendo el cierre de ayer,
porque el BCV dejó de publicar. Ningún código HTTP lo delata: la única señal es la marca
de tiempo del propio dato. A eso se suma el problema opuesto: un proveedor caído retrasa
cada pasada esperando su timeout una y otra vez.

**Decisión.**

1. **Frescura por fuente.** El oficial (publicación en días bancarios) se considera rancio
   pasadas 96 h —revisado en el ADR 37: con 24 h saltaba la alarma todos los fines de
   semana—; el paralelo (por horas), pasadas 12 h. Sin marca de tiempo no se supone rancio:
   el aviso falso destruye la confianza en el aviso verdadero.

2. **Gana el dato más fresco.** En una pasada, una tasa resuelta puede ser reemplazada si
   un proveedor posterior trae marca de tiempo posterior. Y la pasada no se corta cuando
   las dos tasas "están" sino cuando están **frescas**: con el oficial rancio se sigue
   preguntando a las fuentes de cola, que es exactamente cómo el dato del día llega aunque
   el primero sirva el cierre repetido de ayer.

3. **Circuit breaker con auto-recuperación.** Un proveedor que falla queda abierto 10 min:
   se salta en las pasadas siguientes (nadie espera su timeout) y se reintenta solo al
   cumplirse el periodo. Si todos estuvieran abiertos, se reintenta el orden completo:
   el breaker nunca puede dejar la app sin pasada. El estado vive en memoria a propósito:
   un reinicio de proceso reabre los circuitos, y el fallo más común es de red, no del
   proveedor.

4. **UI honesta.** El dashboard muestra un banner distinto del de conexión cuando alguna
   tasa es rancia, y la tarjeta señala su edad ("el banco lleva más de un día sin
   publicar"). El diagnóstico de Ajustes muestra el último dato de cada fuente: "OK con un
   dato de ayer" deja de parecer contradictorio y se lee como lo que es.

**Por qué no “usar siempre el proveedor preferido” o “el primero que responde”.** Con el
banco caído, el primero que responde es precisamente el que repite el cierre de ayer. La
frescura comparada entre fuentes es la única regla que resuelve el caso sin que nadie
tenga que mirar la hora en la pantalla.

---

## 27. DivTrack Lite: un subproyecto aparte, no un sabor del principal

**Decisión.** `lite/` es un proyecto Gradle **independiente** (su propio
`settings.gradle.kts`), con Java + XML, `minSdk 14` y una sola pantalla que publica la tasa
USD/VES del BCV con respaldos identificados (DolarAPI y ER-API). No comparte código con el
grafo principal y se compila con `./gradlew -p lite :app:assembleRelease` desde la raíz.

**Motivo.** El objetivo de Lite es otro: equipos antiguos donde Compose, Room y las librerías
de AndroidX modernas no son una opción. Meterlo como *flavor* del módulo `:app` habría obligado
a que todo el proyecto principal adoptara su `minSdk` y su estilo de UI; separarlo permite que
desaparezca sin tocar una línea del principal (y viceversa).

**Consecuencia honesta.** Los dos APK se publican juntos desde el mismo CI, con nombres de
paquete distintos (`com.liebeblack.divtrack` y `com.liebeblack.divtrack.lite`), y el flujo de
release **verifica la firma y el `applicationId` de cada uno** antes de subirlos. La
contrapartida es que `lite/` no participa del wrapper ni del catálogo de versiones del
principal: sus versiones de AGP y Kotlin viven en su propio `build.gradle.kts` y hay que
subirlas a mano si se quiere ir al día.

**Por qué no se documentó antes.** El subproyecto existía y CI lo compilaba, pero ni el README
ni `docs/index.html` lo mencionaban; quien llegara al repositorio solo veía la app principal.
Queda documentado aquí, en el README y en la web del proyecto.

---

## 28. Lite: no culpar a la fuente, frescura y circuito prestados del principal

**Contexto.** El ADR 26 dejó tres lecciones para el principal: comparar frescura entre
fuentes, apartar al proveedor caído en vez de esperar su tiempo de espera en cada pasada, y
no alarmar al usuario. El Lite no tenía ninguna. Cada pasada repetía los *timeouts* de las
tres fuentes (12 s de conexión + 12 s de lectura cada una: hasta ~70 s de espera con la
pantalla quieta), el respaldo se anunciaba con un mensaje que empezaba por «No se pudo
obtener la tasa del BCV…», y una rotación de pantalla descartaba la cotización recién
llegada porque solo se guardaba después de comprobar que la actividad seguía viva.

**Decisión.**

1. La pantalla identifica la fuente junto a la fecha («Fuente: DolarAPI (respaldo) · dato:
   …») y ya no publica un mensaje de error por la fuente que no respondió; el estado solo
   describe la causa (sin conexión, tiempo agotado, datos no válidos).
2. Un `ProviderPlan` con circuito por fuente aparta 10 minutos a la que falla. Si todas
   estuvieran apartadas se reintenta el orden completo, así que el circuito nunca deja la
   aplicación sin pasada. El estado vive en memoria a propósito, igual que en el principal.
3. Ventana de frescura: con la tasa comprobada hace menos de 10 minutos no se toca la red al
   abrir la pantalla; con la pantalla visible se comprueba cada 30 minutos y al volver a la
   aplicación si el dato está viejo. No se añade ningún permiso: sigue bastando `INTERNET`.
4. La cifra no retrocede: si la fuente trae una fecha de dato anterior a la guardada, se
   conserva la más reciente y se dice que ya era la más reciente.
5. El dato se guarda en el hilo de red en cuanto llega, antes de tocar la interfaz.
6. La fecha del dato (en el BCV, su «Fecha Valor») y el momento de la comprobación son datos
   distintos y van en líneas separadas; un dato con más de un día se señala en ámbar.
7. Se deja de enviar `Accept-Encoding: identity` para que la plataforma negocie gzip: la
   página del BCV pasa de ~150 KB a ~30 KB.

**Coste y mitigación.** La ventana de frescura cede hasta 10 minutos de novedad a cambio de
no gastar red al reabrir la pantalla; el botón «Actualizar» siempre fuerza la consulta, y el
principal usa 240 minutos para su sincronización más corta. El circuito puede mantener
apartada una fuente que ya se recuperó; el reintento completo cuando todas están apartadas
evita que eso deje la pantalla sin consulta.

**Por qué no un *race* en paralelo de las tres fuentes.** Sería más rápido en el peor caso,
pero la cifra mostrada dependería de quién conteste primero y la fuente podría cambiar entre
pasadas idénticas. Manteniendo el BCV primero, la preferencia se conserva y la espera del peor
caso deja de repetirse gracias al circuito.

---

## 29. El panel se refresca al volver, con ventana de frescura

**Contexto.** El ViewModel del panel vive mientras su pestaña siga en el back stack (el panel
es la raíz de la pila, así que nunca sale de ella). Su `init` —el único punto donde se lanzaba
la sincronización— no se repite: con la app en segundo plano durante horas, o con la pestaña
Tasas abierta toda la tarde, en pantalla se quedaba la última pasada hasta que el usuario
tirara de pull-to-refresh o hasta que WorkManager despertara (240 min por defecto). La tarjeta
mostraba la edad del dato, pero saberlo no lo arregla.

**Decisión.** La pantalla escucha el ciclo de vida (`LifecycleEventEffect(ON_START)`) y emite
`DashboardIntent.OnResumed`. El ViewModel compara la edad real de la comprobación del oficial
con una ventana de 15 minutos y **solo entonces** sale a la red, en modo silencioso (sin
snackbar). El refresco automático recorre el mismo camino que el manual: mutex del repositorio
(single-flight), circuit breaker por fuente y reintentos acotados, así que un regreso no puede
duplicar una pasada en vuelo ni martillear a un proveedor caído.

**Por qué con ventana y no en cada regreso.** Un refresco incondicional en cada `ON_START`
gastaría radio al rotar la pantalla, al volver de un diálogo del sistema o al alternar
pestañas, donde el dato es de hace segundos. Quince minutos es el mínimo que admite WorkManager
y cubre de sobra una publicación diaria; el pull-to-refresh sigue estando para quien quiera
forzarlo en el momento.

**Consecuencia honesta.** La ventana deja hasta 15 minutos de antigüedad sin refrescar al
volver al primer plano. A cambio no hay latido de red en cada regreso, y la edad del dato sigue
escrita en la tarjeta («Actualizado …»), que es lo que el usuario necesita para decidir si
refresca a mano. La misma regla explica por qué la fecha de comprobación (`fetchedAtMillis`) es
un dato aparte de la fecha publicada por el proveedor: una fuente puede no publicar nada nuevo
y aun así haber que comprobarlo. El ADR 36 añadió después un segundo motivo para salir a la red
(un último intento fallido reintenta aunque la ventana siga vigente), movió la ventana a
`AppConstants` y aplicó el mismo criterio a la calculadora, que no tenía ninguno.

---

## 30. La fuente variable lleva sus pesos en `variationSettings`

**Contexto.** `presentation/res/font/manrope_variable.ttf` es una fuente **variable**: un solo
eje `wght`, con mínimo 200 (ExtraLight) y techo 800. La familia se declaraba con cinco entradas
del mismo archivo (`Font(R.font.manrope_variable, FontWeight.Normal)`, `…Medium`, `…SemiBold`,
`…Bold`, `…Black`) creyendo que el `FontWeight` elegía el trazo. No lo hace: Compose carga el
recurso sin estilo —`ResourcesCompat.getFont(context, resId)`, visto en el bytecode de
`AndroidFontLoader` de la versión que usa el proyecto— y el único camino que mueve los ejes es
`setFontVariationSettings`, alimentado por `Font.variationSettings`. Sin esos ejes, las cinco
entradas renderizaban la instancia por defecto: **ExtraLight (200)**. La app entera salía con
letra fina y ni el compilador ni ningún test lo notaban.

**Decisión.** Cada entrada declara su eje: `FontVariation.Settings(FontVariation.weight(n))`
para 400, 500, 600, 700 y 800. Se deja de pedir Black (900), que Manrope no tiene: el peso más
grueso de la interfaz (cifra héroe del panel y resultado de la calculadora) es `ExtraBold`
(800). El cuerpo sube de Normal a Medium y `labelSmall` a SemiBold: con el eje ya correcto, eso
es lo que da la letra «definida y bien visible» sin cambiar de familia ni de tamaños.

**Alternativa descartada: cinco TTF estáticos** (Regular, Medium, SemiBold, Bold, ExtraBold).
Funcionaría sin depender del eje, pero son ~0,5 MB más de APK (la variable pesa 165 KB) y el
proyecto cuida el tamaño en gama baja. El soporte de ejes arranca en API 26 —
`Paint.setFontVariationSettings`, la técnica que usa `TypefaceCompatApi26` de Compose—, que es
exactamente el `minSdk` del proyecto.

**Coste y mitigación.** Si alguien vuelve a añadir un peso sin `variationSettings`, el síntoma
es mudo (la app «se ve floja» y nada falla). Por eso `ManropeFontFamilyTest` fija las dos
invariantes: toda entrada declara el eje de su peso y la escala no pide un peso que la fuente
no tenga. Comprobado que el test **falla** al quitar los ejes (reproduce el defecto) y **pasa**
con ellos.

---

## 31. La base dañada se recrea, pero nunca en silencio

**Contexto.** La ADR 16 decidió no usar migración destructiva: perder datos guardados sin
avisar no es una opción en una app financiera. El caso que faltaba no eran las migraciones,
sino la **corrupción**: si el archivo SQLite no se puede abrir (apagón a mitad de escritura,
disco lleno, una restauración mal hecha), la primera lectura lanzaba dentro del `Flow` que
observan los ViewModel y la app caía al abrir. De un archivo ilegible no hay nada que
«conservar».

**Decisión.** La base entra siempre por `RebuildingOpenHelperFactory`, que hace dos cosas:

1. **Verificación previa.** Si el archivo existe y `PRAGMA quick_check` no lo da por sano, se
   borra antes de que Room lo abra y se enciende `DatabaseRebuildTracker`. El panel muestra un
   aviso persistente («la base estaba dañada y se reconstruyó; las tasas volverán con la
   próxima sincronización») que desaparece solo cuando una sincronización vuelve a escribir.
2. **Red de seguridad.** La configuración que se entrega al helper lleva
   `allowDataLossOnRecovery = true` de AndroidX: si la verificación previa pasó pero la
   apertura falla, AndroidX borra el archivo y reintenta con una base nueva. Esa ruta no avisa
   —no hay gancho público para saber que ocurrió— pero evita la caída.

Un fallo que **no** sea corrupción (disco lleno, permisos) no borra nada: se propaga y la UI lo
cuenta con sus mensajes de siempre.

**Alternativas descartadas.**

- **Mostrar solo el error y no tocar la base.** Es lo que pasaba hasta hoy: la lectura lanzaba
  antes de que existiera una UI donde mostrarlo; con el archivo ilegible no hay pantalla que lo
  cuente.
- **`fallbackToDestructiveMigration`.** Solo cubre cambios de versión del esquema, no
  corrupción.
- **Envolver el `SupportSQLiteOpenHelper` a mano.** Detecta también la ruta de apertura y
  podría avisar de ella, pero añade una delegación completa sobre APIs internas móviles.
  `allowDataLossOnRecovery` es API pública de AndroidX y la ruta principal ya avisa.

**Coste.** En una base corrupta se pierden las tasas guardadas: son datos que repuebla la
próxima sincronización, no una decisión del usuario. Mientras vuelve la red, la app sigue
calculando con la última lectura de la sesión, que `RateRepositoryImpl` conserva en memoria; el
aviso explica la pérdida.

---

## 32. (Retirada) La calculadora reparte en cuotas sin modelar intereses (todavía)

> Retirada por la ADR 42: la calculadora ya no simula financiamiento. Se conserva como
> registro histórico.

**Contexto.** Pedido explícito: calcular el crédito en cuotas. Un crédito real se anuncia con
plazo (3, 6, 12, 24 cuotas) y a veces con interés; modelar el interés exige una tasa, su
periodicidad y decidir si es fija o variable. Estimar esa aritmética sin que nadie la haya
definido habría producido cifras con apariencia de exactitud.

**Decisión.** La tarjeta «Crédito en cuotas» divide el total ya calculado —tasa aplicada e
IGTF incluido, si está activo— entre el plazo elegido y muestra la cuota en bolívares y en
dólares. Sin intereses, y la pantalla lo dice: «Sin intereses: el monto entre el número de
cuotas, con la tasa aplicada».

**Por qué es correcto hacerlo así.** El IGTF es proporcional a cada pago, así que dividir el
total entre las cuotas da exactamente el impuesto de cada una. Y como la cuota parte del mismo
total que el desglose de la pantalla, también funciona con la última tasa guardada: es el
respaldo que la calculadora necesita cuando la base local se reconstruyó.

**Coste y siguiente paso.** Si hace falta interés, hay que añadir un campo de tasa y la fórmula
de amortización francesa; el estado admite ese cambio sin tocar el resto de la pantalla.
Mientras tanto, el interés queda fuera del cálculo y se advierte en la propia tarjeta: no se
aproxima en silencio.

---

## 33. El módulo de actualizaciones lee el release rolling de GitHub

**Contexto.** Pedido explícito: «verificación automática de versiones mediante consultas
eficientes a APIs externas» y «descarga en segundo plano». La app ya publica su APK en
Releases (el workflow crea un release rolling con etiqueta `latest` en cada push a `main` que
compila y pasa los tests), pero la app no sabía nada de eso: el usuario tenía que mirar el
repositorio a mano.

**Decisión.** Un módulo propio (`:domain` con modelos y puertos, `:data` con la
implementación, `:core:datastore` con el estado, `:core:network` con el servicio) que consulta
`GET /repos/{owner}/{repo}/releases/latest` y ofrece descargar el APK nuevo desde Ajustes.

Lo que hace que la consulta sea *eficiente* y no un latido de red:

- **Una sola petición y un solo JSON.** El endpoint devuelve el release vigente con sus
  adjuntos; no hay listado ni paginación.
- **Revalidación por `ETag`.** `HeadersInterceptor` manda `Cache-Control: no-cache` en todas
  las peticiones, así que OkHttp revalida y GitHub responde `304 Not Modified` (sin cuerpo)
  cuando nada cambió. La respuesta guardada se reutiliza; el coste entre versiones es de
  bytes, no de KB.
- **Ventana de frescura de 6 h** (`AppConstants.UPDATE_CHECK_MIN_INTERVAL_MILLIS`) para las
  comprobaciones automáticas, y **sin ventana** para el botón manual: el usuario que pulsa
  «Buscar actualización» pidió la respuesta, no una lectura de caché.
- **Fallo rápido sin red** (`ConnectivityObserver`), la misma regla que las tasas: no se
  espera un timeout y, sobre todo, **una comprobación sin red no escribe nada**. Si
  escribiera, un momento sin conexión se convertiría en un «no hay actualizaciones» durante
  seis horas.
- **Trabajo periódico propio** (`UpdateCheckWorker`, 24 h, restricción `CONNECTED`), separado
  del de tasas: apagar la sincronización de tasas no apaga la comprobación de versión, y una
  caída de los proveedores venezolanos no impide saber si hay APK nuevo (host distinto).

**De dónde sale la versión.** El release rolling se titula `DivTrack 4.7 (latest)` y su
etiqueta es `latest`, sin números. La versión se extrae del título —y, si faltara, del cuerpo
de las notas— con `VersionNames`, que compara **por componentes** y no como texto: `4.10` es
posterior a `4.7`, cosa que una comparación de cadenas niega. Un release sin ningún número
legible se cuenta como fallo, no se interpreta.

**Alternativas descartadas.**

- **Un endpoint propio de versiones** (un JSON en el repositorio o en un servicio). Habría
  que mantenerlo sincronizado con lo que publica CI, que es exactamente el trabajo que el
  release ya hace.
- **Preguntar por lista de releases y elegir la más nueva.** Más bytes y más lógica para el
  mismo dato.
- **Notificar con una notificación del sistema.** Habría exigido `POST_NOTIFICATIONS` (y
  pedirlo en tiempo de ejecución), y este proyecto declara dos permisos y ninguno peligroso.
  El resultado se guarda y se cuenta en Ajustes cuando el usuario entra.

**Coste.** La API pública de GitHub limita a 60 peticiones por hora sin autenticar. Con la
ventana de 6 h y un trabajo diario, el consumo es de una petición al día como mucho; aun así,
la ventana existe para que abrir y cerrar Ajustes no la gaste. El slug del repositorio es
configurable (`github.repository` en `local.properties`) y su valor por defecto es
`LiebeBlack/b`, que es el repositorio donde vive esta app, corregido en el ADR 37: en una copia
publicada en otro sitio hay que cambiarlo, o la
comprobación responderá 404 (la app lo dirá con su causa real, sin disfrazarlo de «sin
conexión»).

---

## 34. La descarga la hace el sistema; la instalación la confirma Android

**Contexto.** «Lógica de descarga en segundo plano con actualización automática». En Android,
una app no puede sustituirse a sí misma en silencio: instalar un APK requiere la confirmación
del usuario (o ser *device owner* con `PackageInstaller`, que es un escenario empresarial), y
declarar `REQUEST_INSTALL_PACKAGES` no cambia eso. Cualquier promesa de «actualización
automática» que ignore esto es falsa.

**Decisión.** Tres piezas, cada una en la capa que le corresponde:

1. **Descarga:** `DownloadManager` (gestor del sistema), a través del puerto
   `AppDownloader` de `:domain`, que `:data` implementa. Es el único mecanismo que sigue
   descargando con la app cerrada o matada por el fabricante, que reanuda si se corta la
   conexión y que publica progreso sin que la app tenga que estar viva. Un `CoroutineWorker`
   con OkHttp habría obligado a reimplementar reanudación, progreso y reintentos.
2. **Destino:** la carpeta privada de la app (`getExternalFilesDir(DIRECTORY_DOWNLOADS)`), no
   la carpeta pública de descargas. Sin permisos de almacenamiento y sin dejar APKs tirados
   en un directorio compartido. El nombre del archivo se sanea (se descartan separadores de
   ruta) porque viene de una URL remota.
3. **Instalación:** `FileProvider` (`:app`) + `Intent.ACTION_VIEW` con el MIME de APK. Android
   muestra su pantalla de confirmación y, si el usuario nunca autorizó la instalación desde
   esta app, la suya de «orígenes desconocidos». La app no se salta nada.

**Estado y reanudación.** El id de la descarga y el nombre del archivo se guardan en
DataStore. Al volver a Ajustes, el ViewModel sondea el gestor del sistema (cada 800 ms,
**solo mientras hay descarga viva**) y reengancha el progreso donde estaba; el sondeo se
detiene en la primera fase terminal. Nada de flujos infinitos observando el
`ContentResolver`.

**Permisos.** Ninguno nuevo: el APK sigue declarando `INTERNET` y `ACCESS_NETWORK_STATE`, y
la notificación de progreso la publica el gestor de descargas (no la app), así que tampoco
hace falta `POST_NOTIFICATIONS`.

**Coste y limitación honesta.** El usuario tiene que tocar «Instalar» (y confirmar en la
pantalla del sistema). Además, el APK que se ofrece es el del canal publicado, así que una
compilación de depuración **no** puede instalarse encima de la versión release ni al revés:
son aplicaciones distintas (`com.liebeblack.divtrack` y `…​.debug`) con firmas distintas. La
tarjeta muestra el nombre del archivo descargado para que eso se lea antes de intentarlo.

---

## 35. El dólar oficial es el producto: no se añade un cajón de herramientas ajeno

**Contexto.** La petición de esta pasada llegó con un catálogo de módulos genérico (gestor de
ROMs con indexación, conversiones de bases, cálculo de particiones y parámetros técnicos).
Este proyecto es **una app del dólar oficial en Venezuela y su calculadora**, y el propio
usuario lo confirmó por escrito.

**Decisión.** No se implementa nada de ese catálogo. En su lugar, cada requisito se traduce a
lo que la app realmente necesita, y donde ya estaba resuelto se documenta en vez de
duplicarse:

| Petición genérica | Lo que la app ya tenía | Lo que se añade en esta pasada |
|---|---|---|
| Indexación y gestión offline | Room como única fuente de verdad de la UI, caché HTTP de 10 MB, cierre diario podado, reconstrucción avisada de una base dañada | El módulo de actualizaciones funciona sin red (estado guardado) y no invade la caché |
| Estados de «sin conexión» | Aviso persistente con la causa real, atajo a los ajustes de red, fallo rápido sin conectividad, resolución multi-proveedor | El mismo criterio, aplicado a la comprobación de versión |
| Calculadora de alta precisión | Calculadora bidireccional en cada tecla, conversión directa sin cargos añadidos | Nada en la aritmética: los importes son dinero y ya están redondeados a dos decimales en una sola capa |
| Módulo de utilidades extensible | `RateProvider` en `Set` de Hilt, sección modular en Ajustes | Ajustes gana una sección independiente sin tocar las existentes |
| API reales y validadas | DolarAPI (oficial y paralelo), Yadio, ExchangeRate-API | API pública de GitHub, la única que publica el APK de esta app |

**Por qué se documenta y no se ignora.** Una pasada anterior de este proyecto dejó el registro
de decisiones con lo que se descartó y por qué. Dejar escrito que un gestor de ROMs o un
conversor de bases binarias no tienen sitio en una app de tasas evita que la próxima petición
genérica los vuelva a introducir como «código muerto pendiente de conectar» (ADR 19).

**Lo que sí se conserva del espíritu de la petición.** Todo lo nuevo es modular y aditivo:
modelo y puertos en `:domain`, implementación en `:data`, estado en `:core:datastore`, red en
`:core:network`, UI en `:presentation` y las dos únicas cosas que dependen del manifiesto en
`:app`. Ninguna pantalla, preferencia ni caso de uso existente cambió de sitio, y las tres
pantallas (Tasas, Calculadora, Ajustes) siguen siendo las mismas.

---

## 36. La pasada acepta la lectura en vivo de quien no publica hora, y las pantallas reintentan al volver

**Contexto.** Tres huecos dejaban la tasa mostrada desactualizada (o apagaban la app) sin que
nada lo explicara:

1. **El corte de pasada y la regla de aceptación se contradecían.** Cuando una tasa resuelta ya
   estaba rancia, `needsMoreData` seguía consultando proveedores —justo para reemplazarla—,
   pero `isNewer` exigía que el candidato trajera marca de tiempo. Yadio, que es la fuente
   upstream del paralelo, entregaba la cotización en vivo **sin que la app leyera el
   `timestamp` que sí publica** y su respuesta
   se descartaba: el panel se quedaba con el cierre de ayer de DolarAPI teniendo una fuente sana
   respondiendo en el mismo instante.
2. **El aviso de «sin conexión» se quedaba pegado.** El refresco automático al volver a la
   pantalla solo miraba la edad del dato: con la tasa de hace dos minutos y la última pasada
   fallida, la app no reintentaba hasta que el dato cumplía quince minutos, así que seguía
   anunciando que no había conexión con la conexión ya recuperada. La calculadora ni siquiera
   tenía ese refresco: sincronizaba en su `init` y nunca más, y su ViewModel vive mientras la
   pestaña siga en el back stack, así que volver a ella horas después calculaba con la tasa de
   la mañana.
3. **Ninguna frontera era total.** `RateRepositoryImpl` traducía los fallos de Room
   (`SQLiteException`) a `Result.Error`, pero cualquier otra excepción —base cerrada a mitad,
   `IllegalStateException` del motor, un archivo de preferencias ilegible— subía hasta el
   ViewModel, donde ya solo hay UI: la corrutina moría y con ella la app. El panel, la
   calculadora y Ajustes dependen de la promesa escrita en ese contrato («nunca lanza»), y la
   promesa no estaba garantizada.

**Decisión.**

- **Gana el dato más fresco, también sin hora.** `acceptsUnstamped` acepta un candidato sin
  marca de tiempo **solo cuando el titular está rancio** para su fuente: la lectura en vivo de la
  fuente upstream vale más que un cierre viejo, y sin hora no puede pisar nunca un dato que sí
  está fresco. El reloj se lee una vez por pasada, para que todas las comparaciones de frescura
  de esa pasada usen el mismo instante.
- **Al volver a una pantalla se reintenta por dos motivos:** el dato superó la ventana de
  frescura (15 min) **o** la última pasada falló. La ventana vive en `AppConstants`
  (`RATE_RESUME_REFRESH_AFTER_MILLIS`) para que el panel y la calculadora no puedan divergir
  aplicando la misma regla con números distintos, y dentro de una ventana aún vigente no se
  gasta radio. Un reintento silencioso que vuelve a fallar igual no repite el snackbar (el aviso
  permanente ya cuenta la causa): volver a la pestaña sin red no puede convertirse en una
  ristra de avisos.
- **La calculadora entra en la misma regla.** Gana un `OnResumed` que mira cuándo salió la
  última pasada y si esa pasada falló antes de consultar la red, porque la tasa que usa también
  puede ser de hace horas sin que nadie toque el importe.
- **La frontera del repositorio es total.** `refreshRates()` y `testProviders()` atrapan
  cualquier excepción y la devuelven clasificada como `DataError.Unknown`, y la cancelación
  estructurada se re-lanza. El diagnóstico de Ajustes, además, marca todas las fuentes como
  caídas en vez de lanzar, porque alimenta una pantalla. Una preferencia de proveedor ilegible
  degrada al orden automático: es una comodidad del usuario, no un requisito para traer el dólar
  del día.

**Alternativas descartadas.**

- **Que el candidato sin hora gane siempre.** Un proveedor que responde tarde y sin marca
  pisaría constantemente al que sí publica hora; la regla se limita al caso en que ya sabemos que
  el titular no sirve.
- **Confiar solo en WorkManager para el reintento.** El ciclo mínimo es de quince minutos y no
  reacciona a que el usuario vuelva: el aviso de «sin conexión» se quedaría en pantalla.
- **Atrapar la excepción en cada ViewModel.** Repetiría la red de seguridad en tres pantallas (y
  en la cuarta que llegue) en lugar de en la única capa cuyo contrato dice que no lanza.

**Coste.** El reintento al volver a la pantalla añade una pasada que falla rápido (sin
conectividad ni siquiera se consulta a los proveedores). La lectura del reloj pasa a ser una por
pasada. Y aceptar una cotización sin hora implica confiar en Yadio para el paralelo, que es
exactamente el papel que ya le da el multi-proveedor.

---

## 37. Las APIs, revisadas contra la realidad de Venezuela (y el slug del release estaba mal)

**Contexto.** Esta pasada llega con una instrucción explícita: las APIs deben ser las actuales,
deben responder y todo es para Venezuela. Se verificaron los tres proveedores y el repositorio de
publicación contra sus respuestas y su documentación reales, y aparecieron cuatro defectos que
ninguna prueba unitaria podía ver, porque los fixtures de los tests los escribimos nosotros:

1. **El slug del release no existía.** `github.repository` apuntaba por defecto a
   `liebeblack/divtrack`, y ese repositorio devuelve 404: el repositorio real de la app —lo dice
   el propio `origin` del clon— es `LiebeBlack/b`. La comprobación de versión fallaba siempre con
   «el proveedor cambió su dirección (HTTP 404)» y el botón de descarga no tenía de dónde bajar
   nada.
2. **Yadio sí publica la hora de su cotización.** Su respuesta trae `timestamp` (ms Unix) en la
   raíz y el DTO no lo modelaba: la app entraba la lectura del paralelo con
   `updatedAtMillis = null`, así que no competía por frescura con DolarAPI y los comentarios
   afirmaban lo contrario de lo que dice la API. Ahora el `timestamp` viaja al registro y
   `acceptsUnstamped` (ADR 36) queda como red de seguridad para una fuente que no publique hora.
3. **El umbral del oficial castigaba al BCV por trabajar de lunes a viernes.** El BCV publica en
   días bancarios, así que el cierre del viernes cumple 24 h a los pocos minutos del sábado: con
   el umbral en 24 h la app mostraba «la fuente no publica datos nuevos» todos los sábados y
   domingos —una falsa alarma en casi un tercio de los días— y seguía consultando proveedores de
   cola cuando no había nada mejor que traer. Ahora son 96 h: el aviso solo aparece cuando el
   oficial lleva cuatro días sin moverse, que sí es un banco caído o una caché rancia.
4. **El botón «Instalar» no podía funcionar.** Desde Android 8, una app que apunta a API > 25 y
   entrega un APK al instalador —con `ACTION_VIEW` o con `ACTION_INSTALL_PACKAGE`, da igual—
   debe declarar `REQUEST_INSTALL_PACKAGES`; sin él el sistema rechaza la instalación y el
   usuario ve un error del instalador sin explicación. Y además el ajuste «instalar apps
   desconocidas» de esta app es una decisión del usuario que puede estar apagada: eso no se
   arregla con un permiso.

**Decisión.** Los cuatro arreglos anteriores, más las decisiones de robustez que salieron de
leer los DTO contra las respuestas reales:

- `UpdateInstaller` comprueba `canRequestPackageInstalls()` **antes** de lanzar el intent. Si
  está apagado, no dispara algo que el sistema va a rechazar: lleva al usuario al ajuste exacto
  (`ACTION_MANAGE_UNKNOWN_APP_SOURCES` con `package:`) y se lo explica con un texto en pantalla.
  Si la consulta falla, se intenta igual: bloquear por un fallo de diagnóstico sería peor.
- El campo `fuente` de DolarAPI se modela **anulable**. Si faltara —o llegara `null`— y el DTO lo
  exigiera, kotlinx.serialization tumbaría **toda** la respuesta y la app se quedaría sin la tasa
  oficial por un elemento raro. Ahora ese elemento se descarta y los demás siguen.
- Los umbrales de frescura viven en `AppConstants` y los tests los **leen** de ahí en lugar de
  copiarlos: un cambio de número ya no puede dejar un test midiendo una regla que no existe.
- `YadioRatesDto` deja de modelar `BTC`: no lo leía nadie (ADR 19) y se sustituye por el
  `timestamp`, que sí se usa.

**Lo que no cambió, porque la revisión lo confirmó.**

- `ve.dolarapi.com/v1/dolares` (una sola llamada para oficial **y** paralelo) es el endpoint
  correcto, y el dominio raíz **no** sirve: `dolarapi.com/v1/dolares/oficial` responde con el
  esquema de Argentina (`casa`/`compra`/`venta`) y `/paralelo` da 404. Cambiar la base por el
  dominio raíz habría metido pesos argentinos en la tarjeta del dólar oficial.
- `promedio` sigue siendo el valor correcto del oficial (el BCV publica ahí; `compra` y `venta`
  vienen nulos) y el orden de degradación `promedio → venta → compra` no se toca.
- ExchangeRate-API (`open.er-api.com/v6/latest/USD`) no necesita clave, actualiza una vez al día
  y bloquea 20 minutos a quien se pase de peticiones: el corte de pasada (solo se consulta si
  falta el oficial) y el circuit breaker de 10 minutos ya lo respetan.
- Las fechas llegan en dos dialectos —`2026-10-09T00:00:00-04:00` para el oficial y
  `...T18:01:02.209Z` para el paralelo— y `IsoParsers` ya intenta las dos.

**Alternativas descartadas.**

- **Cambiar a `dolarapi.com` (dominio raíz).** Es lo que sugiere el nombre corto del servicio,
  pero sus respuestas para Venezuela no son las venezolanas: habría mostrado pesos argentinos con
  la etiqueta de dólar oficial.
- **Leer el paralelo de DolarAPI ignorando Yadio.** Yadio es el upstream de ese dato y, con su
  `timestamp` en mano, el registro ya elige el más fresco de los dos en lugar de elegir a ciegas.
- **Subir el umbral del oficial a 72 h.** No alcanza: el cierre del viernes cumple 72 h justo el
  lunes a medianoche, así que la falsa alarma volvería cada lunes por la mañana.

**Coste.** `REQUEST_INSTALL_PACKAGES` es un permiso más en la ficha de la app: no da acceso a
datos, no instala nada por su cuenta y sin él la función de actualizar era un botón que no podía
funcionar. El resto son correcciones de datos y umbrales, sin coste en tiempo de ejecución.

---

## 38. Una cuarta fuente para el oficial: el cierre del BCV por CDN estático

**Decisión.** El registro de proveedores acepta una cuarta implementación, `DolarVZLA`
(prioridad 30), que publica **solo** el oficial leyendo el cierre del Banco Central de
Venezuela desde `rates.dolarvzla.com/bcv/current.json`, un archivo JSON estático en CDN.

**Evidencia recogida antes de decidir** (probado en vivo, 2026-10-11):

| Fuente | Resultado | Decisión |
|---|---|---|
| `rates.dolarvzla.com/bcv/current.json` | 200 · oficial 876,7976 del **2026-10-13** (anterior 875,6505 del 2026-10-09) · sin clave, sin rate limit, CORS abierto | **adoptada** como 4ª fuente |
| `api.dolarvzla.com` (API del mismo sitio) | 401: requiere clave propia (`x-dolarvzla-key`) y solo sirve el USDT de Binance | descartada |
| `pydolarve.org` | DNS no resuelve; el repositorio del proyecto fue borrado (reportes desde oct-2025) | descartada |
| `allratestoday.com` (API BCV) | exige clave y plan de pago | descartada |

**Por qué esta fuente y no otra.** El oficial de DolarAPI depende de que su espejo de
bcv.org.ve siga refrescando; cuando deja de hacerlo, la app sirve un cierre viejo con
aspecto de actual (ADR 26). Este proveedor recorre el mismo dato por otra ruta — BCV →
generador del sitio → CDN, sin API intermedia—, así que basta con que traiga una fecha más
reciente para que la regla de frescura lo imponga sobre el cierre rancio. Y como es un
archivo estático, su respuesta no depende de que nadie esté ejecutando una API en ese
momento: cae menos y, cuando cae, vuelve solo.

**Cómo entra sin tocar el esquema.** `ProviderIds` crece a cuatro ids (`DOLARVZLA`, prioridad
30) y el registro la recorre igual que a las demás: la cola corta apenas el oficial está
completo, así que en el caso normal esta fuente no se consulta y no cuesta una petición. Solo
entra cuando los tres anteriores no trajeron un oficial utilizable, que es exactamente el
caso para el que existe. El `id` viaja como texto por Room y por las preferencias, así que
añadirlo no exige migración ninguna.

**La fecha sin hora.** El CDN publica el cierre como `YYYY-MM-DD`, sin marca de tiempo.
`IsoParsers` ya leía las fechas sin hora como medianoche de Venezuela (la corrección del
ADR 37 se reutiliza aquí tal cual), de modo que un cierre del «2026-10-13» compite con el
mismo «2026-10-13T00:00:00-04:00» de DolarAPI como iguales y gana el más reciente. Una fecha
ilegible viaja como `null` (edad desconocida), no como un valor de 1970.

**Alternativas descartadas.**

- *Sustituir a DolarAPI por esta fuente.* Aporta menos: no tiene paralelo ni hora exacta, y
  DolarAPI es además el upstream de la tasa paralelo vía Yadio.
- *Raspar bcv.org.ve directamente.* Es la fuente primaria, pero exige parsear HTML con
  selectores que cambian sin aviso, y el sitio no siempre responde desde la red móvil
  venezolana: una API intermedia ya hizo ese trabajo por nosotros.
- *Modelar `previous` y `changePercentage`.* Nadie los lee (ADR 19): la app no tiene pantalla
  de histórico y la tendencia se calcula con el cierre diario de Room.

**Coste.** Una petición más solo en el caso de cola, unos 40 líneas de red nuevas y un
segmento más en el selector de proveedor de Ajustes (etiqueta nueva en es-VE y en inglés).
Sin permisos, sin claves y sin cambios en Room ni en DataStore.

---

## 39. El oficial se le pide a DolarAPI por su endpoint dedicado

**Decisión.** El proveedor primario pide el dólar **oficial** a
`GET ve.dolarapi.com/v1/dolares/oficial` (la llamada principal) y el **paralelo** a
`GET ve.dolarapi.com/v1/dolares`. Las dos llamadas van en paralelo y se respaldan entre
sí: si una falla, la otra sigue entregando su tasa.

**Evidencia** (verificado en vivo, 2026-10-11): el endpoint dedicado responde 200 con
`promedio 875.6505` y `fechaActualizacion 2026-10-09T00:00:00-04:00` — el mismo dato que
traía la lista, en un solo objeto y sin el resto del payload.

**Por qué.** El producto de la app es el dólar oficial (ADR 35), y pedírselo al proveedor
en su endpoint dedicado es pedirlo como él lo publica: una tasa, estable y sin depender
de que la entrada del paralelo venga bien. El respaldo cruzado elimina el único punto
débil que tenía el «una sola llamada»: el dedicado puede fallar sin arrastrar al paralelo
(la lista sigue), la lista puede fallar sin arrastrar al oficial (el dedicado sigue), y
solo cuando fallan las dos se registra la caída del proveedor — con el error del oficial,
que es el más explicativo.

**Visto en vivo, el mismo día.** El dedicado servía `875.6505` del **9 de octubre** mientras
el BCV ya había publicado `876.7976` del **13** (lo traía DolarVZLA, ADR 38): la cola y la
resolución por frescura (ADR 26) hicieron exactamente su trabajo — el oficial de DolarAPI
entró primero, se marcó viejo y la pasada siguió hasta la fuente con el cierre más nuevo.

**Coste.** Una petición HTTP más por pasada (dos contra el mismo host, en paralelo: el
techo de tiempo del proveedor sigue siendo el de una sola) y unos 50 líneas de lógica con
tests propios de los dos respaldos.

---

## 40. El oficial tiene una sola voz: si DolarAPI responde, su valor manda

**Decisión.** El registro acepta fuentes **autoritativas**: cuando un proveedor publica un
valor válido de una fuente que declara como suya, ese valor no se deja pisar por nadie —ni
por uno más fresco—, la pasada deja de buscar respaldo para esa tasa y el respaldo solo
entra cuando ese proveedor **falla**. `DolarApiProvider` se declara autoritativo del
`oficial` (su endpoint dedicado `ve.dolarapi.com/v1/dolares/oficial`); el paralelo no lo
es de nadie. Además, cualquier dato fechado en el **futuro** se degrada a «sin hora»: no
compite por frescura ni pinta un día que aún no existe.

**Por qué.** La regla de frescura (ADR 26) tenía un costo que el producto no acepta: una
fuente de cola con una fecha más nueva —aunque su dato no sea el oficial del BCV— podía
desplazar al valor de referencia y encarecer el dólar mostrado. En vivo, el 2026-10-11
(lunes), el CDN de DolarVZLA servía un cierre fechado el **13 de octubre** (876,7976)
mientras el BCV no había publicado más allá del **9** (875,6505, que es lo que servía el
dedicado de DolarAPI): la regla anterior habría mostrado un precio mayor con la fecha de un
día que aún no llegaba. El oficial no «vence»: el BCV publica en días bancarios y un cierre
válido del viernes sigue siendo el oficial el lunes.

**Cómo se aplica.** `RateProvider.authoritativeSources` (vacío por defecto) es la única
declaración nueva; el registro la consulta por id. Un valor autoritativo se guarda sin
importar su edad, la pasada se corta en cuanto las dos tasas están resueltas y la UI sigue
enseñando de qué fuente vino cada valor. Los datos con fecha futura bajan a
`updatedAtMillis = null`: el valor se conserva (es lo mejor que hay), pero sin ventaja de
frescura; la tolerancia es de 2 h para relojes desfasados, no para fechas imposibles.

**Qué no cambia.** El diagnóstico de Ajustes sigue sondeando a todas las fuentes; el aviso
informativo de «la fuente dejó de publicar» sigue saliendo si el oficial lleva días quieto;
y la regla de frescura sigue viva para el paralelo (que sí se mueve varias veces al día y
tiene upstream en Yadio) y para cualquier fuente normal cuando la referencia no responde.

**Coste.** ~40 líneas en el registro, una propiedad en el contrato del proveedor, un
parámetro en el fake de tests y cinco tests nuevos (autoridad, respaldo por fallo, corte de
pasada y fecha futura). Sin llamadas de red nuevas.

---

## 41. Java 21, tres ABIs y la aritmética fuera del hilo principal

**Decisión.** Todo el grafo compila a **Java 21**: `sourceCompatibility` y
`targetCompatibility` en los módulos Android, y `jvmTarget = JVM_21` en los Android y en los
JVM puros (`:domain` y `:core:common`). El release se parte en **tres APKs independientes**
(`armeabi-v7a`, `arm64-v8a`, `x86_64`) y **no** se genera universal. La aritmética de la
calculadora se ejecuta en `Dispatchers.Default` mediante `flowOn` sobre el `combine` que
construye el estado; el dispatcher entra por constructor con el qualifier
`@CalculatorDispatcher` y Hilt lo provee.

**Por qué Java 21.** Es el JDK con el que ya corre CI y el que exige la generación actual de
Android Studio. Fijar el bytecode en 21 evita que el build dependa del JDK del portátil y
aprovecha las optimizaciones de R8 para class files modernos. `minSdk 31` no cambia: D8
desugara lo necesario y el lenguaje que usa el proyecto sigue siendo Kotlin.

**Por qué tres ABIs y sin universal.** `x86` de 32 bits ya no lo usa ningún emulador actual
ni ChromeOS; mantenerlo solo añadía un artefacto y tiempo de build. El universal era una red
de seguridad para instalaciones manuales, pero también el archivo que más se equivoca al
descargar: con tres APKs nombrados por ABI, el usuario y el release hablan del mismo
binario. El workflow verifica que los tres existan antes de publicar.

**Por qué `Dispatchers.Default` y no el hilo principal.** La operación es O(1), pero el
estado también formatea cifras en cada tecla. `flowOn` mueve todo ese
trabajo fuera de Main y el hilo principal solo recibe el `CalculatorUiState` ya construido.
El dispatcher inyectable no es ceremonia: con `Dispatchers.Default` fijo, los tests de
`CalculatorViewModel` dependerían de un hilo real y `advanceUntilIdle()` no esperaría por el
cálculo.

**Bordes y ripple.** La app es Compose, no Views: el equivalente a `ShapeAppearanceOverlay`
es `DivTrackShapes` en `presentation/theme/Shape.kt`, y los botones, chips e iconos ya usan
el ripple de Material 3. Añadir un `ShapeAppearanceOverlay` XML sin una sola View que lo
consuma sería código muerto.

**Fugas de memoria.** No se introduce `WeakReference` para callbacks: el proyecto ya ata el
trabajo a `viewModelScope` y a `collectAsStateWithLifecycle`. Un `WeakReference` no sustituye
a un scope cancelado y, en Compose, escondería el problema en vez de resolverlo.

**Coste.** Un salto de contexto por emisión del estado; despreciable frente al formateo que
se ahorra en Main. Sin dependencias nuevas: Hilt y coroutines ya estaban en `:presentation`.

**Verificación.** En este entorno no hay JDK ni Android SDK, así que la compilación y los
tests quedan pendientes del primer `./gradlew` real. Lo comprobado aquí es estático: las 8
configuraciones apuntan a 21, el split declara exactamente tres ABIs y el workflow rechaza
cualquier APK que no sea una de ellas.

---

## 42. La calculadora solo convierte; el inversor es un botón sólido

**Contexto.** El producto es una calculadora de conversión USD ↔ Bs. El recargo del 3 % y
la simulación de financiamiento añadían estado, textos, tests y superficie de error que no
pertenecen a ese alcance. Además, el inversor de dirección era un enlace de texto con poco
contraste y un área táctil menor que la del resto de acciones.

**Decisión.**

- Se eliminan del dominio, contrato, ViewModel, UI, textos, copiado y tests: el recargo, su
  interruptor, el selector de plazos y todos los campos derivados.
- `Conversion` queda con `usdAmount` y `bsAmount`, sin duplicar `net*`/`total*`.
- `CalculateConversionUseCase` recibe solo dirección, fuente, tasa y monto: multiplica o
  divide, sin pasos intermedios.
- El inversor es un `Button` de ancho completo, 56 dp mínimos, colores
  `secondaryContainer`/`onSecondaryContainer`, borde y padding; el icono es decorativo y el
  texto sigue siendo la etiqueta accesible. Un `TextButton` no daba el contraste ni el área
  táctil que pide una acción secundaria tan frecuente.
- Se mantiene el cálculo en `Dispatchers.Default` inyectado, ahora con menos trabajo por
  tecla.
- El split queda en tres APKs (`armeabi-v7a`, `arm64-v8a`, `x86_64`), sin universal; el
  workflow verifica que los tres existan antes de publicar.

**Por qué.** Menos estado es menos superficie de bug y una sola fórmula es auditable. El
botón sólido resuelve contraste y área táctil sin añadir un componente nuevo.

**Coste.** Se pierden dos funciones que existían; las ADR 5 y 32 quedan marcadas como
retiradas. Sin dependencias nuevas ni cambios de esquema.
