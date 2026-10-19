// :domain — Clean Architecture pura (Kotlin/JVM). No conoce Android ni ninguna
// librería de UI/red/persistencia: solo modelos, contratos y casos de uso.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

dependencies {
    // api: los contratos de repositorio exponen Result/DataError de :core:common,
    // por lo que :data y (a través suyo) :presentation deben verlo.
    api(project(":core:common"))

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.javax.inject) // los casos de uso son @Inject constructor(...)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
