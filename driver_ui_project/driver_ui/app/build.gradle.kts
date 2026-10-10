import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.pfa.driverui"
    compileSdk = 37            // required by SceneView 4.x (AGP 8.13.2 is enough)

    defaultConfig {
        applicationId = "com.pfa.driverui"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.3.0"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")   // Mic icon (replaces icons-core)

    // Client réseau (REST + WebSocket) pour communiquer avec api_server.py
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // 3D (Filament) : voiture Camry dans app/src/main/assets/camry.glb
    implementation("io.github.sceneview:sceneview:4.54.0")
}