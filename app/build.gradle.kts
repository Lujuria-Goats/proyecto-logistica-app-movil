plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    // CAMBIO CRÍTICO: Usamos KSP en lugar de KAPT
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.apexvision.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.apexvision.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    // --- Android Core ---
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)

    // --- Tests ---
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // --- Lifecycle ---
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")

    // --- UI Avanzada ---
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")
    implementation("com.airbnb.android:lottie:6.1.0")
    implementation("com.facebook.shimmer:shimmer:0.5.0")
    implementation("com.ncorti:slidetoact:0.9.0")

    // --- Mapas (Mapbox) ---
    implementation(libs.mapbox.maps)

    // --- Ubicación ---
    implementation(libs.play.services.location)

    // --- Cámara ---
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    // --- Imágenes ---
    implementation(libs.glide)

    // --- Networking (Retrofit) - Lo necesitarás para los endpoints ---
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.11.0")

    // --- BASE DE DATOS ROOM (CON KSP) ---
    // Esta es la configuración limpia que no falla con Kotlin nuevo
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)

    // USAMOS KSP (Procesador de símbolos moderno)
    ksp(libs.androidx.room.compiler)
}