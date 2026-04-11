import java.util.Properties
import java.io.FileInputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.photouploaderapp"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.photouploaderapp"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.2"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    signingConfigs {
        create("release") {val props = Properties()
            val localPropertiesFile = rootProject.file("local.properties")

            if (localPropertiesFile.exists()) {
                FileInputStream(localPropertiesFile).use { stream ->
                    props.load(stream)
                }
            }

            val keystorePath = props.getProperty("signing.storeFile")
                ?: System.getenv("SIGNING_STORE_FILE")

            if (keystorePath != null) {
                storeFile = file(keystorePath)
                storePassword = props.getProperty("signing.storePassword") ?: System.getenv("SIGNING_STORE_PASSWORD")
                keyAlias = props.getProperty("signing.keyAlias") ?: System.getenv("SIGNING_KEY_ALIAS")
                keyPassword = props.getProperty("signing.keyPassword") ?: System.getenv("SIGNING_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a")
            isUniversalApk = true
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    @Suppress("DEPRECATION")
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.activity:activity-ktx:1.8.2")

    implementation("com.google.android.material:material:1.13.0-alpha04")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.drawerlayout:drawerlayout:1.2.0")
    implementation("androidx.coordinatorlayout:coordinatorlayout:1.2.0")

    implementation("androidx.documentfile:documentfile:1.0.1")

    implementation(platform("com.squareup.okhttp3:okhttp-bom:4.12.0"))
    implementation("com.squareup.okhttp3:okhttp")
    implementation("com.google.code.gson:gson:2.10.1")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    implementation("androidx.work:work-runtime-ktx:2.9.0")

    implementation("androidx.preference:preference-ktx:1.2.1")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}