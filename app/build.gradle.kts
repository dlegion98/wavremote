import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Signing: GitHub Actions passes the key through environment variables; on the Mac it's read
// from signing/keystore.properties (never committed). Without either, release falls back to the debug key.
val keyProps = Properties().apply {
    val f = rootProject.file("signing/keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun secret(prop: String, env: String): String? = System.getenv(env)?.takeIf { it.isNotBlank() } ?: keyProps.getProperty(prop)

val storePath = secret("storeFile", "KEYSTORE_FILE")
// CI sets -PversionCode to the workflow run number so every release is newer than the last.
val appVersionCode = (project.findProperty("versionCode") as String?)?.toIntOrNull() ?: 3
// Which GitHub repo the in-app updater checks ("owner/name"). CI passes it automatically.
val updateRepo = (project.findProperty("updateRepo") as String?) ?: keyProps.getProperty("updateRepo") ?: ""

android {
    namespace = "com.dayo.wavremote"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.dayo.wavremote"
        minSdk = 26
        targetSdk = 35
        versionCode = appVersionCode
        versionName = "1.3.$appVersionCode"
        buildConfigField("String", "UPDATE_REPO", "\"$updateRepo\"")
        resValue("string", "app_name", "WavRemote")
    }

    signingConfigs {
        create("release") {
            if (storePath != null) {
                storeFile = rootProject.file(storePath)
                storePassword = secret("storePassword", "KEYSTORE_PASSWORD")
                keyAlias = secret("keyAlias", "KEY_ALIAS")
                keyPassword = secret("keyPassword", "KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (storePath != null) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
        debug {
            // Android Studio builds install alongside the GitHub version instead of clashing with it.
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
            resValue("string", "app_name", "WavRemote Dev")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.google.zxing:core:3.5.3")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
}
