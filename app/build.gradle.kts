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
        versionCode = 1
        versionName = "1.0.0"
    }

    // API de Axzy Central (licencia de la taquería): -Ptaqueria.apiUrl=https://…/api/v1
    val apiUrl = project.findProperty("taqueria.apiUrl") as String?
    buildTypes {
        debug {
            // Emulador → API local de la Mac.
            buildConfigField("String", "LICENSE_API_URL", "\"${apiUrl ?: "http://10.0.2.2:4100/api/v1"}\"")
        }
        release {
            // Sin -Ptaqueria.apiUrl la cocina no se puede activar (lo dice en pantalla).
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
    testImplementation(libs.junit)
}
