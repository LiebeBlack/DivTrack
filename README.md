# DivTrack

App Android nativa para rastrear en tiempo real las dos tasas que importan en Venezuela —
**dólar oficial (BCV)** y **dólar paralelo** — y convertir montos entre bolívares y dólares
en ambos sentidos, con el IGTF (3 %) opcional.

- **Kotlin 100 %**, Jetpack Compose con Material 3, Clean Architecture modular, MVI estricto.
- **Online-First**: Room es la única fuente de verdad de la UI; la red solo escribe.
- **Multi-API real**: si un proveedor cae, el otro completa las tasas que falten.
- Pensada para gama baja: superficies planas, sin animaciones infinitas, `derivedStateOf`
  donde de verdad importa y ViewModels acotados por pantalla.

---

## Estado verificado del proyecto

Lo que sigue se comprobó **ejecutando** cosas, no asumiendo:

| Comprobación | Resultado |
|---|---|
| `GET https://ve.dolarapi.com/v1/dolares` | 200 · `oficial` **866,56** Bs (dato del **2 oct**, >24 h: la app lo marca rancio y sigue preguntando) · `paralelo` **974,34** Bs (reverificado 2026-10-03) |
| `GET https://api.yadio.io/exrates/USD` | 200 · `USD["VES"]` **972,52** (fuente upstream del paralelo) |
| `GET https://open.er-api.com/v6/latest/USD` | 200 · `rates["VES"]` **871,37** (el valor vigente del BCV: el registro se lo queda por ser el más fresco; reverificado 2026-10-03) |
| `pydolarve.org` | **descartada**: el dominio no resuelve (DNS) |
| `api.dolarvzla.com` | **descartada**: responde 401, requiere clave privada |
| `criptoya.com/api/USD/VES` | **descartada**: responde 422 en las variantes probadas |
| `:app:assembleDebug` | **BUILD SUCCESSFUL** · APK de depuración de **21,9 MB** |
| `:app:assembleRelease` | **BUILD SUCCESSFUL** con R8, `shrinkResources` y `lintVitalRelease` · APK de **2,40 MB** |
| Tests JVM de los 4 módulos | **88 casos, 0 fallos** (`testDebugUnitTest :domain:test :core:common:test`, 2026-10-03) |
| `:app:lintDebug` | 1 error pendiente: ruta de Windows sin escapar en `local.properties` (archivo local, fuera de git) |

> Toolchain real montado y usado para compilar: JDK 21 (Temurin), `cmdline-tools` de 2026 con
> el CLI nuevo, **plataforma `android-37.0`** (ojo: `platforms;android-37` no existe) y
> `build-tools 37.0.0`.
>
> **Alcance de esta cifra.** Todo lo de la tabla se midió el **2026-10-03** sobre el código que
> hay hoy en el repo: APK de depuración, APK release con R8 y la corrida completa de tests
> (88 casos, 0 fallos). Las pasadas anteriores se hicieron sin compilar, a petición explícita;
> esta las recompiló y las pasó por tests. Lo único que sigue sin re-ejecutarse aquí es
> `:app:lintDebug`, que en CI corre con `continue-on-error`.

---

## Stack (2026)

| Pieza | Versión | Nota |
|---|---|---|
| Android Gradle Plugin | 9.4.0 | Kotlin integrado + DSL nuevo (`android.newDsl`) |
| Gradle | 9.6.0 | wrapper incluido y verificado por checksum |
| Kotlin | 2.4.20 | sin plugin `kotlin-android` (lo aporta AGP 9) |
| KSP | 2.3.12 | KSP2, ya desacoplado de la versión de Kotlin |
| Compose BOM | 2026.09.00 | Material 3 |
| Hilt | 2.60.1 | ≥ 2.59 es requisito para AGP 9 |
| Room | 2.8.5 | esquema exportado y versionado |
| Retrofit / OkHttp | 3.0.0 / 5.5.0 | conversor oficial de kotlinx.serialization |
| Navigation 3 | 1.2.0 | + `lifecycle-viewmodel-navigation3` para ViewModel por entrada |
| WorkManager | 2.12.0 | sincronización silenciosa |
| DataStore Preferences | 1.2.0 | preferencias |
| compileSdk / targetSdk / minSdk | 37 / 37 / 26 | edge-to-edge nativo |

---

## Estructura

```
:core:common      Kotlin/JVM puro. Result<T>, DataError, formateo es-VE, parser de importes.
:core:network     Retrofit triple (DolarAPI + Yadio + ER-API), 4 interceptores, RateProvider + registro con circuit breaker y frescura.
:core:database    Room: tasa vigente + cierre diario (base de la flecha de tendencia).
:core:datastore   DataStore: tema, IGTF, fuente por defecto, frecuencia de sync.
:domain           Kotlin/JVM puro. Modelos, contratos de repositorio y 9 casos de uso.
:data             Implementaciones, orquestación multi-proveedor, WorkManager.
:presentation     Compose + MVI por pantalla + tema M3 + Nav3.
:app              Application (Hilt + WorkManager), MainActivity, recursos, R8.
```

Fuera de ese grafo hay un **subproyecto Gradle independiente**, con su propio
`settings.gradle.kts`: `lite/` (DivTrack Lite), una app Java + XML de una sola pantalla que
publica la tasa BCV sin Compose ni librerías de terceros. Se compila desde la raíz con
`./gradlew -p lite :app:assembleRelease` y CI la publica junto al APK principal.

Dirección de dependencias, **impuesta por Gradle** y no por convención:

```
:app ─▶ :presentation ─▶ :domain ◀─ :data ─▶ :core:{network, database, datastore}
                                        └──▶ :core:common (base pura para todos)
```

`:domain` no ve Android ni Compose ni Retrofit ni Room. Detalle en
[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

---

## Cómo compilar

### Opción A — Android Studio (recomendado)

1. Abre la carpeta del proyecto (`File ▸ Open`).
2. Deja que sincronice: el wrapper fija Gradle 9.6.0 y el catálogo fija todo lo demás.
3. Ejecuta la configuración `app`. Requiere Android Studio de la generación que soporte
   AGP 9.4 (Otter 3 o posterior) y JDK 21.

### Opción B — línea de comandos

```bash
cp local.properties.example local.properties   # opcional: personaliza las URLs base
./gradlew :app:assembleDebug                   # APK de depuración
./gradlew testDebugUnitTest :domain:test :core:common:test   # tests JVM
./gradlew :app:assembleRelease -PcomposeMetrics=true         # R8 + métricas de Compose
./gradlew -p lite :app:assembleRelease                       # DivTrack Lite (subproyecto aparte)
```

Sin `local.properties` **también compila**: las URLs base caen a los endpoints públicos
verificados. Ninguna URL está escrita en el código Kotlin: entran por `BuildConfig` desde
`local.properties` (`dolarapi.baseUrl`, `yadio.baseUrl`, `exchangerateapi.baseUrl`).

### CI

`.github/workflows/android-ci.yml` se ejecuta en cada push y en cada PR: JDK 21, plataforma
`android-37.0` + `build-tools 37.0.0`, compila debug, corre los tests, compila release con R8,
compila **DivTrack Lite** (`./gradlew -p lite :app:assembleRelease`) y publica APK, reportes,
métricas del compilador de Compose y los dos APK (principal y Lite) como artefactos.

El APK para instalar se descarga desde **Releases**, no desde el artefacto de CI:
`app-release-unsigned.apk` no está firmado. El flujo de publicación verifica la firma y el
`applicationId` antes de subir el archivo. Sin secretos de firma configurados publica el APK
debug (`com.liebeblack.divtrack.debug`), firmado con una clave temporal del runner; para
actualizar ese canal puede ser necesario desinstalar la versión anterior. Las versiones
release (`com.liebeblack.divtrack`) deben firmarse siempre con la misma clave para instalarse
como actualización.

Ojo con el nombre de la plataforma en CI: la de API 37 se publica como **`platforms;android-37.0`**
(o `platforms/android-37.0` con el CLI nuevo). `platforms;android-37` **no existe**, y era el
motivo de que el paso de SDK no dejara nada instalado.

---

## Qué hace la app

### Panel de tasas
Valor en Bs. de cada tasa con acento de color por fuente, flecha de tendencia (verde/rojo)
comparada con el cierre anterior y pie con la procedencia del dato. Debajo, la **brecha**
paralelo/oficial en porcentaje y valor absoluto. Pull-to-refresh y botón de refresco.

La **tasa paralela (y con ella la brecha) es opcional**: se enciende en Ajustes y, mientras
esté apagada, el panel se concentra en el oficial sin mostrar un dato que el usuario no pidió.
Al volver a la app, si la última comprobación tiene más de 15 minutos, el panel **se refresca
solo y en silencio** (sin snackbar), y no toca la red si lo que hay en pantalla es reciente
(ADR 29).

### Calculadora bidireccional
Se escribe el monto y el resultado aparece **en cada tecla**: no hay botón "Calcular".
Selector segmentado Oficial/Paralelo, interruptor de IGTF (3 %) y botón para copiar el
desglose con el formato del pliego:

```
DivTrack · Monto: $10 | Tasa Oficial (BCV): 36,50 Bs | IGTF: Sí | Total: 375,95 Bs
Detalle — Oficial (BCV) | Neto: $10,00 | IGTF: 10,95 Bs | Total: $10,30
Calculado con DivTrack
```

Aritmética del IGTF (la parte fácil de equivocar, por eso está documentada y probada):

- **USD → Bs**: `netBs = usd × tasa`, `totalBs = netBs + netBs × 3 %`.
- **Bs → USD**: el monto tecleado **es** el total; `netUsd = totalUsd / 1,03`.

### Ajustes
Tema (Sistema / Claro / Oscuro, aplicado al instante), tasa por defecto de la calculadora,
IGTF por defecto, sincronización en segundo plano con su frecuencia (15/30/60/120/240 min),
versión instalada y atribución de proveedores.

---

## Comportamiento sin conexión

1. Al abrir, la UI pinta al instante lo que hay en Room.
2. En paralelo se lanza la sincronización.
3. Si la red responde, Room se actualiza y **las tres pantallas se repintan solas**.
4. Si falla, los datos locales **no se tocan** y aparece un aviso **con la causa real**:
   *"Sin conexión a internet…"* si es conectividad, *"La fuente está fallando (HTTP 503)"* si
   el que falla es el proveedor, *"El proveedor tardó demasiado en responder"* si es un
   timeout. Antes todo eso se contaba como "sin conexión" y el usuario revisaba su wifi sin
   motivo.
5. Si un proveedor responde y otro no, la sincronización es **parcial**: se avisa y se
   conservan las tasas que sí llegaron.
6. Si el dispositivo no tiene red, la app lo sabe **antes** de intentarlo: el aviso sale al
   instante, sin esperar al timeout. Y si no sabe seguro si hay red, **lo intenta igual**: un
   falso "no hay internet" es peor que 200 ms de espera.
7. Cuando el fallo es de conectividad, el propio aviso ofrece **"Abrir ajustes de red"**: un
   error sin salida deja al usuario atascado.

---

## Tests

93 casos JVM declarados en el repo (los 88 de la última corrida completa, más los 2 de la
corrección tipográfica y los 3 de la última pasada, estos tres sin ejecutar), centrados en lo
que puede romperse en silencio:

| Módulo | Qué se prueba |
|---|---|
| `:core:common` | Parser de importes es-VE (`1.234,56` / `1,234.56` / `1.234`), formateo de moneda, `Result` y cancelación de corrutinas |
| `:domain` | IGTF bidireccional (ejemplo del pliego: $10 a 36,5 → **375,95 Bs**) y brecha |
| `:data` | Online-First: la red escribe y no destruye caché, resolución **por tasa** entre proveedores, fallo inmediato sin conectividad, cierre diario para la tendencia |
| `:presentation` | Cálculo automático al teclear, saneado al pegar, cambio de tasa/dirección, brecha del panel, eventos one-shot, reprogramación de WorkManager |

---

## Esta pasada (2026-10-03): el panel no se queda atrás y las fechas no pierden días

Pasada de corrección **con compilación y tests de verdad** (esta vez sí: 88 casos, 0 fallos).

| Hallazgo | Corrección |
|---|---|
| **El panel se quedaba atrás**: el ViewModel vive en la raíz del back stack, así que su `init` no se repite; con la app en segundo plano durante horas (o la pestaña Tasas abierta toda la tarde) la tasa en pantalla era la de la última pasada hasta que el usuario tirara de pull-to-refresh o despertara WorkManager (240 min). | `LifecycleEventEffect(ON_START)` → `DashboardIntent.OnResumed`: el ViewModel mira la edad **real de la comprobación** (`fetchedAtMillis`, dato nuevo en el modelo de UI) y solo sincroniza si supera los 15 minutos, en modo silencioso. Dos tests nuevos fijan los dos lados: 5 minutos de antigüedad no gastan red; 40 minutos disparan una pasada sin snackbar. |
| **Una fecha sin hora se leía como UTC**: `IsoParsers` interpretaba `"2026-09-30"` como medianoche UTC y, como la UI pinta en hora de Venezuela, esa fecha se mostraba como **29 sep**. Hoy ningún proveedor publica solo la fecha, pero el camino era incorrecto y silencioso. | Se lee como medianoche de Venezuela (la misma zona con la que la app pinta). Cuatro tests nuevos, incluido el caso del día perdido. |
| El comentario del grafo de navegación afirmaba que «al salir de una pestaña, sus ViewModels se limpian»: cierto para las pestañas que salen de la pila, **falso para el panel**, que es la raíz y conserva el suyo (y por eso hacía falta el refresco al volver). | Comentario corregido y encadenado con el refresco; la web decía lo mismo en su sección de pantallas y también se corrigió. |

Lo verificado en esta pasada, ejecutando:

| Comprobación | Resultado |
|---|---|
| Tests JVM de los módulos | **88 casos, 0 fallos** (eran 82; +6 de esta pasada) |
| `:app:assembleDebug` y `:app:assembleRelease` | **BUILD SUCCESSFUL** · 21,9 MB y 2,40 MB con R8 y `lintVitalRelease` |
| APIs en vivo (2026-10-03) | DolarAPI responde 200 con el oficial del **2 oct** (más de 24 h) y el paralelo del día; ER-API trae el oficial vigente (**871,37**) y el registro **se lo queda por frescura**: la regla del ADR 26 vista funcionando con datos reales |
| `:app:lintDebug` | no re-ejecutado en esta pasada (CI lo corre con `continue-on-error`) |

---

## Esta pasada (2026-10-03, segunda): la letra ya pesa lo que dice pesar

**Hallazgo (comprobado contra el binario de la fuente y el bytecode de Compose, no supuesto).**
`manrope_variable.ttf` es una fuente **variable** de un solo eje (`wght`), con mínimo en
**200 = ExtraLight** y techo en 800. La familia declaraba cinco entradas del mismo archivo con
solo el `FontWeight`; `AndroidFontLoader` carga el recurso sin estilo
(`ResourcesCompat.getFont(context, resId)`) y solo mueve los ejes con `variationSettings`. Sin
ejes, **todas** las entradas pintaban ExtraLight: la app entera se veía de letra fina y ni el
compilador ni los tests lo veían.

| Qué | Antes | Ahora |
|---|---|---|
| Pesos de Manrope | Cinco entradas que renderizaban ExtraLight | Cada entrada fija su eje (`FontVariation.Settings(FontVariation.weight(n))`): 400, 500, 600, 700 y 800 |
| Peso máximo pedido | `FontWeight.Black` (900), que la fuente no tiene | `ExtraBold` (800), el techo real |
| Cuerpo de texto | Normal (400) | **Medium (500)**, y `labelSmall` a SemiBold |
| `LoadingState` y su `@Preview` | Código muerto: ninguna pantalla lo usaba | Eliminado (ADR 19) |

Lo que esta pasada dejó **verificado** —ejecutando— y lo que no:

| Comprobación | Resultado |
|---|---|
| `ManropeFontFamilyTest` (2 casos) | **2 casos, 0 fallos**; se comprobó además que **falla** al quitar los `variationSettings` (reproduce el defecto) |
| `:presentation:compileDebugUnitTestKotlin` | **BUILD SUCCESSFUL** |
| Resto de tests JVM y APK (`assembleDebug` / `assembleRelease`) | **no re-ejecutados**: la corrida se interrumpió a petición, así que esta pasada no reclama la tabla completa |
| `:app:lintDebug` | no ejecutado |

La decisión y su coste quedan en la ADR 30 ([docs/DECISIONS.md](docs/DECISIONS.md)).

---

## Revisión del almacenamiento incompleto (2026-10-03, tercera)

Revisión pedida: qué pinta la app **cuando no hay de todo** en Room (y sin red). El repaso del
camino completo —DAO, `RateLocalDataSource`, repositorio, los dos ViewModel y las pantallas—
dejó una buena noticia y un hueco:

| Caso | Antes | Ahora |
|---|---|---|
| Room vacío y sin conexión | Estado de error con la causa real, «Reintentar» y atajo a los ajustes de red; la tarjeta del oficial enseña «— / Sin datos». Correcto | Igual |
| Solo el oficial guardado | Las tasas guardadas se pintan; la brecha no se muestra (necesita las dos) y la calculadora solo ofrece la que existe | Igual |
| **Paralela encendida y nunca guardada** | La tarjeta no aparecía y **nada explicaba por qué**: el usuario la había activado y no veía ni dato ni motivo | Aviso persistente «La tasa paralela aún no tiene datos guardados…» (`msg_parallel_missing`, en es-VE y en inglés) |

Nada de esto se compiló ni se pasó por tests en esta pasada (instrucción explícita). Lo que sí
se comprobó leyendo: la clave nueva existe en los dos idiomas y se usa una sola vez, y el aviso
reutiliza el mismo componente que el de «dato rancio» (renombrado `DashboardNotice`, porque ya
no solo habla de conexión).

---

## Cuarta pasada (2026-10-03): base dañada, memoria y cuotas

Tres frentes pedidos explícitamente: que una base dañada se reconstruya, que el cálculo siga
funcionando con lo último guardado («calcular con caché») y que la calculadora reparta un monto
en cuotas de crédito.

| Qué | Antes | Ahora |
|---|---|---|
| **Base Room dañada** | La primera lectura lanzaba dentro del `Flow` y la app caía al abrir, sin forma de recuperarse | `RebuildingOpenHelperFactory`: verificación con `PRAGMA quick_check` antes de abrir, borrado de lo ilegible y aviso persistente en el panel (`msg_cache_rebuilt`); `allowDataLossOnRecovery` de AndroidX como segunda red para fallos de apertura |
| **Cálculo sin Room** | Si la base fallaba en caliente, el colector de tasas moría y la calculadora se quedaba sin nada | `RateRepositoryImpl` reintenta la lectura dos veces y, si no hay arreglo, sirve la **última lectura de la sesión**; una emisión vacía tras la reconstrucción no borra esa memoria |
| **Cuotas de crédito** | No existían | Tarjeta «Crédito en cuotas»: 3, 6, 12 o 24 cuotas, cuota mensual en bolívares y dólares sobre el total ya calculado (IGTF incluido), con la aclaratoria de que no modela intereses (ADR 32) |

Nada de esta pasada se compiló ni se ejecutó (instrucción explícita). Se revisó leyendo el
código, y las únicas APIs nuevas para el proyecto se verificaron contra el código fuente de
AndroidX y no contra el recuerdo: `Configuration.builder(...)`, `noBackupDirectory(...)` y
`allowDataLossOnRecovery(...)` existen en `androidx.sqlite`; `room-runtime` 2.8.5 publica
`:sqlite:sqlite` y `:sqlite:sqlite-framework` como `api`, así que esos tipos están en el
classpath de `:core:database`.

| Comprobación | Resultado |
|---|---|
| Test nuevo del panel (la caché reconstruida se expone en el estado) | escrito, **no ejecutado** |
| Tests nuevos de la calculadora (reparto en cuotas y mínimo de una cuota) | escritos, **no ejecutados** |
| Reconstrucción probada en dispositivo (archivo dañado de verdad) | no ejecutada |
| Compilación y resto de la suite | no ejecutados |

Las decisiones y sus alternativas quedan en la ADR 31 (reconstrucción de la base) y la ADR 32
(cuotas sin intereses).

---

## Correcciones de la revisión final

Una pasada de revisión completa (leyendo el código, no confiando en el recuerdo) encontró y
corrigió esto:

| Hallazgo | Corrección |
|---|---|
| **Bug**: la calculadora ofrecía siempre las dos tasas, aunque una no existiera todavía. Elegirla llevaba a "sin tasas disponibles". | El selector usa `state.rateOptions`: solo ofrece lo que hay en Room, y la tasa por defecto cae en una disponible. Con test propio. |
| La flecha de tendencia se dibuja en un `Canvas`: invisible para TalkBack. | `TrendArrow` publica su significado en la descripción de accesibilidad. |
| El estado `isOffline` existía pero no se veía en pantalla (solo el snackbar, que se va solo). | Aviso persistente bajo la barra mientras dure la falta de conexión. |
| El refresco automático al abrir mostraba el indicador de pull-to-refresh. | El indicador solo aparece en el refresco manual. |
| Código muerto: `findActivity`, `ThemeMode.next`, `cycleThemeMode`, `isDark`, `trendDescription`, una extensión de flujos sin usar y una constante sin usar. | Eliminado, junto con los 3 imports que quedaron huérfanos. |
| Referencias con nombre completo (`com.liebeblack...TrendDirection`) dentro de `when`. | Imports e imports de ayuda; sin rutas completas en el cuerpo. |

Una segunda pasada, esta vez **funcional** (siguiendo el recorrido del usuario en lugar de
leer archivo por archivo), encontró dos fallos que ningún test cubría porque solo se ven al
manipular la interfaz:

| Hallazgo | Corrección |
|---|---|
| **Bug funcional** *(Histórico, pantalla ya eliminada)*: el índice del crosshair vivía dentro de un `derivedStateOf` que capturaba `pointCount` por valor y quedaba congelado en la primera composición. | Se corrigió resolviendo el índice contra la serie real; la pantalla completa se eliminó después (ver «Esta pasada»). |
| **Bug visual** *(Histórico, pantalla ya eliminada)*: el margen interno del gráfico se restaba en píxeles crudos (`14f`), así que en densidad alta el trazado se pegaba al borde. | Se corrigió con una constante en `dp` convertida con `toPx()`; la pantalla completa se eliminó después. |

Una tercera pasada, esta vez **auditando la interfaz** (¿cada control hace algo? ¿cada estado
se pinta? ¿compila?), encontró **dos errores de compilación** que ninguna de las dos
revisiones anteriores vio porque no buscaban esto:

| Hallazgo | Corrección |
|---|---|
| **Error de compilación** *(Histórico, pantalla ya eliminada)*: `HistoryScreen` agrupaba las tarjetas de estadísticas con `key(...)` sin importar `androidx.compose.runtime.key`. El proyecto no compilaba. | Import añadido entonces; el archivo ya no existe. |
| **Error de compilación**: `SpreadChip` pasaba dos `String?` a `stringResource(id, vararg formatArgs: Any)`, que no acepta nulos, y el `if (hasData)` no hace *smart cast*. | Dos copias locales no nulas y comprobación directa, que sí lo hace. |
| *(Histórico, pantalla ya eliminada)* Las series del gráfico se recordaban solo por `state.series`: al cambiar de tema, el gráfico conservaba los colores del tema anterior. | `colors` entraba en las claves del `remember`. |
| Código muerto: `CalculatorUiState.selectedRate`, `SettingsUiState.isLoading`, `DashboardUiState.rate()`, `DivTrackColors.positive`, `ExchangeRate.changeAbsolute`, `SyncSummary.resolvedFromCacheOnly`, `CurrencyFormatters.dollars()`, `CurrencyFormatters.monthLabel()`, el endpoint `getDollar()` y un fallback redundante al resolver la tasa de la calculadora. | Eliminado. Nada de eso se leía ni se llamaba en ningún sitio, tests incluidos. |

Lo que esta pasada dejó **verificado**, no corregido:

| Comprobación | Resultado |
|---|---|
| Textos (recuento de entonces) | 84 claves en es-VE y en inglés con los mismos argumentos, y las 8 llamadas con formato pasan el número exacto de argumentos |
| Interactividad | Cada botón, selector, interruptor y gesto llega a una intención; 0 `onClick` vacíos, 0 `TODO`. El único `onRetry = {}` está en un `@Preview` |
| Estados de pantalla (recuento de entonces) | Panel pinta carga, aviso y error además de datos; Ajustes solo datos, porque su fuente es un DataStore ya en memoria |
| Grafo de inyección | 25 constructores `@Inject` y un campo: toda dependencia tiene `@Provides`, `@Binds` o `@IntoSet` |
| Estructura | 112/112 archivos con `package` = ruta, llaves y paréntesis balanceados, 0 imports sin uso, 0 declaraciones huérfanas |

---

## Pasada anterior: Histórico fuera, conexión, permisos, rendimiento y el leak

Cinco cosas, en el orden en que molestaban:

| Qué | Antes | Ahora |
|---|---|---|
| **Sección de Histórico** | pestaña propia con gráfico `Canvas`, rangos 1M/3M/YTD/1A, estadísticas y descarga de la serie diaria | **eliminada por completo**: pantalla, gráfico, rangos, casos de uso, modelos, DTO y endpoint. La tabla de cierre diario se queda porque alimenta la flecha de tendencia del panel (ADR 11) |
| **"Las API no funcionan / error de conexión"** | cualquier fallo se mostraba como "sin conexión"; el `ConnectivityManager` podía decir "no hay red" con wifi perfecta y entonces **ni se intentaba**; sin techo de tiempo global; se reintentaba hasta un DNS roto | conectividad que solo bloquea cuando de verdad no hay red, errores por causa real (con código HTTP), `callTimeout` de 20 s, sin reintentos de fallos deterministas (ADR 22). Las dos APIs responden 200 hoy, comprobado en vivo |
| **Lag** | LeakCanary vigilando todos los objetos en el APK de uso diario (73 s de análisis medidos), `entryProvider` reconstruido en cada recomposición, un `DecimalFormat` nuevo por cada cifra formateada | LeakCanary apagado por defecto (`-Pdivtrack.leakcanary=true` para encenderlo) y análisis en otro proceso, grafo recordado, formateadores reutilizados, lista de pestañas construida una vez (ADR 23) |
| **Permisos** | el APK de debug declaraba `READ_EXTERNAL_STORAGE`, `WRITE_EXTERNAL_STORAGE` y `POST_NOTIFICATIONS` (venían de LeakCanary, no de la app) | exactamente `INTERNET` y `ACCESS_NETWORK_STATE`, documentados; tráfico en claro cerrado; y el aviso de conectividad ofrece "Abrir ajustes de red" (ADR 24) |
| **Leak de `SystemJobService`** | se reportaba como fuga propia | diagnosticado como retención del framework (`ResourcesImpl.mAppContext`) y documentado en la ADR 21, con la traza y el porqué |

Nada de esto se volvió a compilar ni se pasó por tests en aquella pasada (fue a petición explícita).
El siguiente `./gradlew :app:assembleDebug` es lo que convierte esta tabla en verificada.

---

## Esta pasada: revisión estricta sin compilar (correcciones y optimizaciones)

Otra pasada de revisión completa, otra vez **leyendo el código y no ejecutando nada**
(cero comandos, cero tests, cero compilación: fue la instrucción explícita). El criterio fue el
que ya está escrito en las ADR 19, 20 y 22: nada de código muerto, el selector refleja lo que
existe y no se reintenta lo que no puede cambiar por esperar.

| Hallazgo | Corrección |
|---|---|
| **Bug**: `CalculatorScreen` volvía a ofrecer las dos tasas cuando Room estaba vacío (`ifEmpty { RateSource.ordered() }`), así que pulsar una opción inexistente volvía a dejar "sin tasas disponibles". Era el bug que corrigió la ADR 20, reintroducido como respaldo en la pantalla. | Las opciones salen **solo** de `state.rateOptions`; sin tasas no hay ningún chip que pulsar y el aviso explica qué hacer. |
| **Contradicción con la ADR 22**: `RateSyncWorker` devolvía `retry()` para **cualquier** `Result.Error`, incluido un 404 o una respuesta ilegible, que dan el mismo resultado 30 s después. | El worker reintenta solo lo transitorio (red, timeout, HTTP 408/429/5xx) y marca el intento como fallido en el resto; el siguiente ciclo periódico vuelve a intentarlo. Es lo que ya describía `ARCHITECTURE.md`. |
| `ProviderRegistry` construía su reloj por defecto con el nombre completo `com.liebeblack...SystemTimeProvider()` (la convención del proyecto prohíbe las rutas completas en el cuerpo). | El valor por defecto se elimina: Hilt inyecta el `TimeProvider` real y los tres tests que construyen el registro pasan un `FakeTimeProvider` explícito, así que **ningún test depende ya del reloj real**. |
| `DashboardUiState.toRateUiModel(nowMillis = System.currentTimeMillis())` se permitía leer el reloj del dispositivo en la capa de presentación, teniendo `TimeProvider` inyectado. | El parámetro es obligatorio. |
| Nombres completos en el cuerpo: `android.util.Log` (×2) en `DivTrackApplication` y `java.time.Instant` en `SettingsScreen`. | Importados y usados sin ruta. |
| Imports fuera de orden en `SettingsViewModel` (`java.io.IOException` entre `androidx` y `com`) y en `DashboardViewModel` (`utils` antes de `time`). | Reordenados. |
| `SyncSummary.kt` era un archivo con solo el `package`: el modelo vivía en `ProviderStatus.kt`. | La declaración se movió a su archivo. |
| **Código muerto**: `EmptyState` y las claves `empty_rates_*` pintaban un estado inalcanzable (la app nunca queda sin datos y sin error); su única referencia era un `@Preview`. | Eliminados el composable y las dos claves en ambos idiomas. |
| **Código muerto**: `ConnectivityObserver.observe()` y su `callbackFlow` de ~40 líneas no los consumía nadie; la app solo usa la consulta puntual `isOnline()`. | Contrato reducido a `isOnline()` (interfaz, implementación Android y fake). |
| **Código muerto**: `lastSyncAtMillis` / `setLastSyncAt` / la clave `last_sync_at` se escribían desde ningún sitio y se leían desde ninguno. | Eliminados del modelo, del contrato, de la implementación de DataStore y del fake (la clave `last_sync_at` desaparece de las preferencias). |
| **Código muerto**: los colores `Sky` y `SkyDeep` no se usaban (solo sobrevivían en un comentario). | Eliminados; el comentario de legibilidad habla de los acentos que existen (Mint/Amber). |
| **Estado que no se pintaba**: `CalculatorUiState.netUsdText` y `netBsText` se calculaban y ningún composable los leía (el propio `ARCHITECTURE.md` dice que los subtotales netos no se exponen). | Eliminados del estado; los tests que los usaban ahora comprueban los **totales** que sí se ven en pantalla. |
| **Código muerto de segundo orden**: al quitar `EmptyState`, su único consumidor, `Spacing.xxl` se quedaba sin usar. | Eliminado también, con la nota de que se añade en una línea si vuelve a hacer falta (ADR 19). |

Lo que esta pasada dejó **verificado leyendo**, sin ejecutar nada:

| Comprobación | Resultado |
|---|---|
| Textos | 116 claves en es-VE y en inglés, con el mismo juego en los dos archivos (eran 118; se retiraron las dos de estado vacío) |
| Tests | 82 funciones `@Test` en el repo (49 en la última corrida verificada) |
| Estructura | 124 archivos Kotlin con `package` = ruta (los de `lite/` son Java y viven en su subproyecto) |
| Referencias colgantes | 0 tras los borrados: `observe()`, `netUsdText`, `netBsText`, `EmptyState`, `lastSyncAtMillis`, `setLastSyncAt`, `Sky`, `SkyDeep` y `Spacing.xxl` no aparecen en ningún archivo |
| Documentación | `ARCHITECTURE.md` decía "Retrofit dual", "2 Retrofit" y "7 casos de uso" (son tres proveedores, tres Retrofit y ocho casos de uso); `docs/index.html` hablaba de 79 claves y 24 ADR; el README apuntaba a `web/index.html`, que no existe (la web es `docs/index.html`), y no mencionaba DivTrack Lite en ninguna parte |

Nada de esto se compiló ni se pasó por tests: fue a petición explícita. El siguiente
`./gradlew :app:assembleDebug` y `./gradlew testDebugUnitTest :domain:test :core:common:test`
es lo que convierte estas tablas en verificadas.

---

## DivTrack Lite (subproyecto aparte)

`lite/` es una app **independiente**, no un *flavor* de esta: Java + XML, `minSdk 14`, una sola
pantalla con la tasa USD/VES del BCV y respaldos que se identifican como tales (DolarAPI →
ER-API), caché del último valor en `SharedPreferences` y cero librerías de terceros. No comparte
código con el grafo principal y se compila con su propio `settings.gradle.kts`:

```bash
./gradlew -p lite :app:assembleRelease
```

CI la construye y publica junto al APK principal, verificando antes la firma y el
`applicationId` de cada uno (`com.liebeblack.divtrack` y `com.liebeblack.divtrack.lite`). El
porqué de que sea un subproyecto y su coste están en la ADR 27 y en [lite/README.md](lite/README.md).

---

## Web del proyecto

`docs/index.html` es una página **autocontenida** sobre el software: sin JavaScript, sin CDN,
una sola petición de red (ninguna). Explica el producto con maquetas CSS de las tres
pantallas, el flujo Online-First, el diagrama de módulos, el stack con versiones, los comandos
de compilación y una tabla honesta de **qué está comprobado y qué no**. Usa la misma paleta y
los mismos radios que `presentation/theme`, y respeta `prefers-color-scheme`.

Se abre con doble clic (no necesita servidor) o se publica tal cual en GitHub Pages:

```bash
# Settings → Pages → Deploy from a branch → /docs
```

---

## Publicar en GitHub

```bash
git init
git add -A
git commit -m "DivTrack: app de tasas oficial/paralelo con Clean Architecture y MVI"
git branch -M main
git remote add origin git@github.com:<tu-usuario>/divtrack.git
git push -u origin main
```

El primer push dispara el workflow: si algo del toolchain nuevo (AGP 9 + Kotlin 2.4 +
Nav3 1.2) cambia de forma, el error aparecerá ahí, con el log completo como artefacto.

---

## Licencia y aviso

El proyecto **no declara licencia todavía**: elegir una (MIT o Apache-2.0 son las habituales
para código abierto) es una decisión tuya, y por eso no hay un `LICENSE` inventado en el
repositorio. Añádelo antes de publicar el repo.

La tipografía **Manrope** sí se distribuye bajo SIL Open Font License 1.1
([licenses/Manrope-OFL.txt](licenses/Manrope-OFL.txt)) y se puede usar y redistribuir
libremente, incluso en apps comerciales.

DivTrack publica tasas de referencia de fuentes públicas. No es una casa de cambio ni un
ente oficial y no se hace responsable de decisiones tomadas con esos valores.
