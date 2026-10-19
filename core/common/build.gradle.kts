// :core:common — base PURA (Kotlin/JVM). Sin Android, sin Compose, sin Retrofit.
// Es lo único que :domain puede consumir, por eso aquí viven Result/DataError,
// constantes, formateo de moneda y utilidades de tiempo.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

kotlin {
    compilerOptions {
        // AGP 9 ya no admite kotlinOptions{}; el DSL canónico es kotlin.compilerOptions{}.
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
