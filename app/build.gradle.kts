// :app — Punto de entrada, grafo Hilt raíz, WorkManager y recursos finales.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.compose.compiler)
}

/**
 * LeakCanary está **apagado por defecto** y se enciende a petición:
 *
 * ```
 * ./gradlew :app:assembleDebug -Pdivtrack.leakcanary=true
 * ```
 *
 * Por qué no se deja encendido siempre en debug:
 *
 * 1. **Coste real en el APK de uso diario.** LeakCanary vigila todos los objetos con
 *    `ObjectWatcher`, y cuando algo se retiene hace un volcado de heap y lo analiza. En un
 *    gama baja ese análisis tarda más de un minuto (medido: 73 s) y congela la app. Es una
 *    herramienta de diagnóstico, no algo que deba estar vigilando mientras se usa la app.
 * 2. **Permisos que no son nuestros.** Su manifiesto aporta READ_EXTERNAL_STORAGE,
 *    WRITE_EXTERNAL_STORAGE y POST_NOTIFICATIONS, así que el APK acababa mostrando permisos
 *    que DivTrack no usa. Apagándolo, el build declara exactamente lo que necesita.
 * 3. **Falsos positivos del framework.** Ver la ADR 21: el caso clásico aquí es
 *    `SystemJobService` retenido por `ResourcesImpl.mAppContext`, que es de Android, no
 *    nuestro.
 */
val leakCanaryEnabled: Boolean = (findProperty("divtrack.leakcanary") as String?) == "true"

/**
 * Firma de release desde el entorno, nunca desde el repositorio.
 *
 * CI decodifica el keystore en un archivo temporal y exporta estas cuatro variables antes
 * de invocar Gradle; AGP firma entonces cada APK por ABI durante `assembleRelease`. Así la
 * clave privada no viaja en el código ni en los artefactos y el build local sigue
 * produciendo APKs sin firmar si no hay secretos configurados.
 */
val releaseKeystoreFile = providers.environmentVariable("ANDROID_KEYSTORE_FILE")
    .orNull
    ?.let { path -> file(path) }
    ?.takeIf { it.isFile }
val releaseStorePassword = providers.environmentVariable("ANDROID_KEYSTORE_PASSWORD")
val releaseKeyAlias = providers.environmentVariable("ANDROID_KEY_ALIAS")
val releaseKeyPassword = providers.environmentVariable("ANDROID_KEY_PASSWORD")
val releaseSigningReady = releaseKeystoreFile != null &&
    !releaseStorePassword.orNull.isNullOrBlank() &&
    !releaseKeyAlias.orNull.isNullOrBlank() &&
    !releaseKeyPassword.orNull.isNullOrBlank()

android {
    namespace = "com.liebeblack.divtrack"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.liebeblack.divtrack"
        minSdk = 31
        targetSdk = 37
        versionCode = 471
        versionName = "4.7.1"

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        releaseKeystoreFile?.let { keystore ->
            if (releaseSigningReady) {
                create("release") {
                    storeFile = keystore
                    storePassword = releaseStorePassword.get()
                    keyAlias = releaseKeyAlias.get()
                    keyPassword = releaseKeyPassword.get()
                }
            }
        }
    }

    /**
     * Un APK por arquitectura, más el universal para instalaciones manuales.
     *
     * `arm64-v8a` es también el ABI de los dispositivos ARMv9: no existe un ABI `armv9`
     * separado, ARMv9 ejecuta binarios ARM64 y Android sirve esa variante. `armeabi-v7a`
     * cubre ARM 32-bit y `x86`/`x86_64` los emuladores y Chromebooks.
     *
     * Con R8 y `shrinkResources` el contenido útil cabe en poco más de 2 MB; separar por
     * ABI evita arrastrar código nativo ajeno a la arquitectura del dispositivo. El APK
     * universal se conserva porque GitHub Releases se descarga a mano y no todos los
     * usuarios saben qué ABI tiene su teléfono.
     */
    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            isUniversalApk = true
        }
    }

    buildFeatures {
        compose = true
        // DivTrackApplication usa BuildConfig.DEBUG para el nivel de log de WorkManager.
        // AGP 8+ ya no genera BuildConfig por defecto: hay que pedirlo explícitamente.
        buildConfig = true
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isMinifyEnabled = false
        }
        release {
            // R8 + shrink de recursos: CI valida que las reglas de ProGuard son correctas
            // compilando el release en cada push.
            isMinifyEnabled = true
            isShrinkResources = true
            // Firma por ABI en el propio build; CI verifica cada archivo antes de publicarlo.
            // Sin secretos, el bloque no se toca y AGP deja el APK sin firmar (build local).
            if (releaseSigningReady) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":presentation"))
    implementation(project(":data"))
    implementation(project(":domain"))
    implementation(project(":core:common"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // Solo en debug, y solo cuando se pide explícitamente (ver `leakCanaryEnabled`).
    if (leakCanaryEnabled) {
        debugImplementation(libs.leakcanary)
        // El análisis del heap en un proceso aparte: cuando se depura una fuga de verdad, la
        // app se queda con el volcado y el análisis deja de robarle fotogramas al hilo
        // principal. Es la forma documentada de que LeakCanary no afecte al rendimiento.
        debugImplementation(libs.leakcanary.process)
    }
}
