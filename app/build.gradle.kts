plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.example.driveup"
    compileSdk = 34  // Mantenemos 34 por ahora

    defaultConfig {
        applicationId = "com.example.driveup"
        minSdk = 26
        targetSdk = 34  // Actualizado de 33 a 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        buildConfig = true
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    // ✅ VERSIONES COMPATIBLES CON COMPILESDK 34
    implementation("androidx.core:core-ktx:1.12.0")        // Compatible con SDK 34
    implementation("androidx.appcompat:appcompat:1.6.1")    // Versión estable
    implementation("com.google.android.material:material:1.11.0")  // Material Design

    // ✅ GOOGLE PLAY SERVICES - ESENCIAL PARA GPS
    implementation("com.google.android.gms:play-services-location:21.0.1")

    // ✅ RETROFIT & HTTP
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // ✅ COROUTINES
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // ✅ TESTING
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")

    // Para manejo moderno de Activity Results (permisos)
    implementation("androidx.activity:activity-ktx:1.8.0")
    implementation("androidx.fragment:fragment-ktx:1.6.2")

    // Ya tienes esto:
    implementation("org.maplibre.gl:android-sdk:11.8.0")

    implementation("com.google.android.material:material:1.11.0")


}
