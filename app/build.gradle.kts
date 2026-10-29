import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Firebase sign-in is switched on as soon as app/google-services.json exists (see README).
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

// Optional settings read from local.properties (never committed): YOUTUBE_API_KEY, ADMOB_APP_ID, ADMOB_BANNER_ID
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun lp(k: String): String = (localProps.getProperty(k) ?: "").trim().replace("\\", "\\\\").replace("\"", "\\\"")

android {
    namespace = "com.studypulse.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.studypulse.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 13
        versionName = "6.7"
        buildConfigField("String", "ADMOB_BANNER_ID", "\"" + lp("ADMOB_BANNER_ID").ifEmpty { "ca-app-pub-3940256099942544/6300978111" } + "\"")
        manifestPlaceholders["admobAppId"] = lp("ADMOB_APP_ID").ifEmpty { "ca-app-pub-3940256099942544~3347511713" }
        buildConfigField("String", "GEMINI_API_KEY", "\"" + lp("GEMINI_API_KEY") + "\"")
        buildConfigField("String", "YOUTUBE_API_KEY", "\"" + lp("YOUTUBE_API_KEY") + "\"")
    }

    // Signing for the Google Play upload (App Bundle). Set STORE_FILE, STORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD in local.properties.
    val storePath = (localProps.getProperty("STORE_FILE") ?: "").trim()
    signingConfigs {
        if (storePath.isNotEmpty()) {
            create("release") {
                storeFile = file(storePath)
                storePassword = (localProps.getProperty("STORE_PASSWORD") ?: "").trim()
                keyAlias = (localProps.getProperty("KEY_ALIAS") ?: "").trim()
                keyPassword = (localProps.getProperty("KEY_PASSWORD") ?: "").trim()
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (storePath.isNotEmpty()) signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.webkit:webkit:1.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")

    // Accounts: Google sign-in and phone number codes
    implementation(platform("com.google.firebase:firebase-bom:33.5.1"))
    implementation("com.google.firebase:firebase-auth")
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")

    // Payments: Google Play Billing
    implementation("com.android.billingclient:billing-ktx:7.1.1")

    // Ads on the Free plan
    implementation("com.google.android.gms:play-services-ads:23.3.0")

    // Reading scanned PDFs and pictures on the phone
    implementation("com.google.mlkit:text-recognition:16.0.1")

    // Reading uploaded PDF plans
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
}
