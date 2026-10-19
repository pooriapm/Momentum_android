import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// Server endpoints and store keys come from momentum.properties (not in git).
// Copy momentum.properties.example and fill it in before building.
// Read through providers so the configuration cache (on in gradle.properties) notices edits;
// a plain File read was cached and gave Android Studio builds empty endpoints.
val momentum = Properties().apply {
    providers.fileContents(rootProject.layout.projectDirectory.file("momentum.properties"))
        .asText.orNull?.let { load(it.reader()) }
}
if (momentum.getProperty("supabaseUrl").isNullOrBlank()) {
    logger.warn("momentum.properties is missing or has no supabaseUrl; the app will show a setup error instead of connecting.")
}
fun momentumValue(key: String) = "\"${momentum.getProperty(key, "")}\""

android {
    namespace = "com.pooriapm.momentum"
    compileSdk = 37

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
        // Where exercise animations and the library are served: Supabase storage, unless a debug
        // build points at a local test server (exerciseMediaUrl in momentum.properties).
        buildConfigField("String", "EXERCISE_MEDIA_URL", "\"\"")
    }

    buildTypes {
        debug {
            buildConfigField("String", "EXERCISE_MEDIA_URL", momentumValue("exerciseMediaUrl"))
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        // Release-speed build for testing on the emulator next to the debug app (own id and data,
        // debug signing). Debug builds run unoptimized and are several times slower to start.
        create("perf") {
            initWith(getByName("release"))
            applicationIdSuffix = ".perf"
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
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
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.1")
    implementation("androidx.core:core-ktx:1.16.0")
    // Liquid Glass (blur, lens refraction, highlights) for the floating tab bar: Kyant0/AndroidLiquidGlass.
    implementation("io.github.kyant0:backdrop:2.0.1")
    implementation("io.github.kyant0:shapes:1.2.1")
    // Installs the startup profile (ours and Compose's) so the code is compiled ahead of time.
    // Google Play does this from the cloud; Bazaar does not, so without it every install runs slow at first.
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
    // Exercise animations (small Lottie zips downloaded per exercise from the server).
    implementation("com.airbnb.android:lottie-compose:6.7.1")
    // Home-screen widget (next meal + one-tap tick).
    implementation("androidx.glance:glance-appwidget:1.2.0")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

    // Cafe Bazaar in-app billing.
    implementation("com.github.cafebazaar.Poolakey:poolakey:2.2.0")
}

// Alias task for compatibility with standard Java task invocations like compileJava
tasks.register("compileJava") {
    description = "Alias for compiling sources in the debug variant."
    group = "build"
    dependsOn("compileDebugJavaWithJavac")
}

