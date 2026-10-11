import java.util.Properties

// :core:network — Retrofit + OkHttp (multi-proveedor), interceptores y errores de red.
//
// SEGURIDAD: las URLs base NO están hardcodeadas en Kotlin. Se leen de local.properties
// (fuera del control de versiones) y se inyectan como campos de BuildConfig. Si el archivo
// no existe, se usan los endpoints públicos verificados para que CI compruebe sin secretos.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun configuredValue(key: String, fallback: String): String =
    localProperties.getProperty(key)?.takeIf { it.isNotBlank() } ?: fallback

android {
    namespace = "com.liebeblack.divtrack.core.network"
    compileSdk = 37

    defaultConfig {
        minSdk = 26

        buildConfigField(
            "String",
            "DOLARAPI_BASE_URL",
            "\"${configuredValue("dolarapi.baseUrl", "https://ve.dolarapi.com/")}\"",
        )
        buildConfigField(
            "String",
            "YADIO_BASE_URL",
            "\"${configuredValue("yadio.baseUrl", "https://api.yadio.io/")}\"",
        )
        buildConfigField(
            "String",
            "EXCHANGERATEAPI_BASE_URL",
            "\"${configuredValue("exchangerateapi.baseUrl", "https://open.er-api.com/")}\"",
        )
        buildConfigField(
            "String",
            "DOLARVZLA_BASE_URL",
            "\"${configuredValue("dolarvzla.baseUrl", "https://rates.dolarvzla.com/")}\"",
        )

        // API pública de GitHub (v3), usada por el módulo de actualizaciones para leer el
        // release rolling que publica CI en cada push a main. Se configura con
        // `github.apiBaseUrl` y `github.repository` en local.properties: el slug del
        // repositorio no está en el código Kotlin y se puede apuntar a un fork sin tocar
        // una línea de fuente.
        buildConfigField(
            "String",
            "GITHUB_API_BASE_URL",
            "\"${configuredValue("github.apiBaseUrl", "https://api.github.com/")}\"",
        )
        buildConfigField(
            "String",
            "GITHUB_REPOSITORY",
            "\"${configuredValue("github.repository", "LiebeBlack/b")}\"",
        )
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // api: NetworkErrorMapper devuelve DataError de :core:common en su contrato público.
    api(project(":core:common"))

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    implementation(platform(libs.okhttp.bom))
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // Deserialización de los fixtures JSON verificados de los cuatro proveedores.
    testImplementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}
