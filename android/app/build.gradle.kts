plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.bit128.bitsports"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.bit128.bitsports"
        minSdk = 26
        targetSdk = 34
        // CI passes 100 + run number so every build installs over the last one.
        versionCode = (System.getenv("BITSPORTS_VERSION_CODE") ?: "3").toInt()
        versionName = "0.7.0"
    }

    // Release builds are signed with the 128bit family key (the Bitcall release key), so the
    // family apps trust each other and updates install in place. CI decodes the FAMILY_* secrets;
    // without them a local release build falls back to the debug key.
    val keystore = System.getenv("FAMILY_KEYSTORE_PATH")
    signingConfigs {
        if (keystore != null) create("release") {
            storeFile = file(keystore)
            storePassword = System.getenv("FAMILY_KEYSTORE_PASSWORD")
            keyAlias = System.getenv("FAMILY_KEY_ALIAS")
            keyPassword = System.getenv("FAMILY_KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
