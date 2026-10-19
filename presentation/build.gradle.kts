// :presentation — UI Compose (Material 3), MVI por pantalla, Navigation 3 y tema premium.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.liebeblack.divtrack.presentation"
    compileSdk = 37

    defaultConfig {
        minSdk = 31
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

// Métricas del compilador de Compose: CI las genera con -PcomposeMetrics=true y las publica
// como artefacto. Son la prueba objetiva de estabilidad (0 parámetros inestables = menos
// recomposiciones en gama baja).
composeCompiler {
    if (providers.gradleProperty("composeMetrics").isPresent) {
        metricsDestination.set(layout.buildDirectory.dir("compose-metrics"))
        reportsDestination.set(layout.buildDirectory.dir("compose-reports"))
    }
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":core:common"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    // MD3 nativo en Compose. `com.google.android.material:material` es la implementación
    // para Views: añadirla aquí duplicaría el design system y el tamaño del APK sin
    // aportar ni un componente, porque toda la UI es Compose.
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    // De todos los iconos de la app, solo dos son de `extended` (Calculate y TrendingUp, las
    // pestañas); Refresh, Clear, Share, Warning, Info y Settings salen de `core`. R8 elimina en
    // release todo lo que no se usa, así que el coste real en el APK final son dos vectores.
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    // Da a cada NavEntry su propio ViewModelStoreOwner: al salir de una pestaña su ViewModel
    // se libera (onCleared), que es exactamente donde se pierden los datos si se usa el
    // owner de la Activity para todo.
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
