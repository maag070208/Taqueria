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
    // providers.gradleProperty (no findProperty) para que la caché de configuración note el -P;
    // si no, un APK puede salir con la URL de la compilación anterior.
    val apiUrl = providers.gradleProperty("taqueria.apiUrl").orNull
    buildTypes {
        debug {
            // Emulador → API local de la Mac.
            buildConfigField("String", "LICENSE_API_URL", "\"${apiUrl ?: "http://10.0.2.2:4100/api/v1"}\"")
        }
        release {
            // Sin -Ptaqueria.apiUrl solo se puede activar escaneando el QR de la Central (el QR trae el servidor).
            buildConfigField("String", "LICENSE_API_URL", "\"${apiUrl.orEmpty()}\"")
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
        buildConfig = true
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
