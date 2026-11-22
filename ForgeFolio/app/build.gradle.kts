plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.forgefolio.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.forgefolio.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        multiDexEnabled = true

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    base {
        archivesName.set("ForgeFolio")
    }

    packaging {
        resources {
            excludes += listOf(
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/LICENSE.md",
                "META-INF/LICENSE-notice.md",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/DEPENDENCIES",
                "META-INF/INDEX.LIST",
                "META-INF/FastDoubleParser-LICENSE",
                "META-INF/FastDoubleParser-NOTICE",
                "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
            )
        }
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")

    // Jetpack Compose & Material 3
    implementation(platform("androidx.compose:compose-bom:2024.11.00"))
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    // AndroidX Utilities & SAF DocumentFile
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.exifinterface:exifinterface:1.4.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Offline Document Engines
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
    implementation("org.apache.poi:poi-ooxml:5.2.5")

    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.apache.poi:poi-ooxml:5.2.5")
}
