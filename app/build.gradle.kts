import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// Server endpoints and store keys come from momentum.properties (not in git).
// Copy momentum.properties.example and fill it in before building.
val momentum = Properties().apply {
    val file = rootProject.file("momentum.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
fun momentumValue(key: String) = "\"${momentum.getProperty(key, "")}\""

android {
    namespace = "com.pooriapm.momentum"
    compileSdk = 36

    defaultConfig {
        // Permanent once published on Cafe Bazaar.
        applicationId = "com.pooriapm.momentum"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "0.9.0"

        buildConfigField("String", "SITE_URL", momentumValue("siteUrl"))
        buildConfigField("String", "SUPABASE_URL", momentumValue("supabaseUrl"))
        buildConfigField("String", "SUPABASE_ANON_KEY", momentumValue("supabaseAnonKey"))
        buildConfigField("String", "BAZAAR_RSA_KEY", momentumValue("bazaarRsaKey"))
        buildConfigField("String", "BAZAAR_PRODUCT_ID", momentumValue("bazaarProductId"))
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.1")
    implementation("androidx.core:core-ktx:1.16.0")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    // Cafe Bazaar in-app billing.
    implementation("com.github.cafebazaar.Poolakey:poolakey:2.2.0")
}
