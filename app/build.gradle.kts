import java.util.Properties

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}

android {
    namespace = "com.axzy.taqueria"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.axzy.taqueria"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        // Una sola fuente de versión: gradle.properties (la sube release.sh).
        versionCode = providers.gradleProperty("taqueria.versionCode").get().toInt()
        versionName = providers.gradleProperty("taqueria.versionName").get()
    }

    // API de Axzy Central (licencia de la taquería): -Ptaqueria.apiUrl=https://…/api/v1
    // También se puede fijar en local.properties (no va a git) para que toda compilación de
    // esta máquina —incluidas las del IDE, que no pasan -P— apunte a la misma Central.
    val localProps = Properties().apply {
        rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
    }
    val apiUrl = providers.gradleProperty("taqueria.apiUrl").orNull
        ?: localProps.getProperty("taqueria.apiUrl")?.takeIf { it.isNotBlank() }
    // Recurso y no buildConfigField: Kotlin copia las constantes de BuildConfig dentro de quien
    // las usa y la compilación incremental no lo recompila al cambiar la URL, así que un APK
    // podía salir apuntando a la Central de la compilación anterior.
    buildTypes {
        debug {
            // Emulador → API local de la Mac.
            resValue("string", "license_api_url", apiUrl ?: "http://10.0.2.2:4100/api/v1")
        }
        release {
            // Sin -Ptaqueria.apiUrl solo se puede activar escaneando el QR de la Central (el QR trae el servidor).
            resValue("string", "license_api_url", apiUrl.orEmpty())
            isMinifyEnabled = false
            // Firma de depuración para poder instalar el APK directo en los teléfonos del local.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        resValues = true
    }
    // android.util.Log en pruebas JVM devuelve valores por defecto en vez de fallar.
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(libs.androidx.activity.compose)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    // Escáner del QR de activación (CameraX + ZXing core, sin Google Play Services).
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.zxing.core)
    testImplementation(libs.junit)
}
