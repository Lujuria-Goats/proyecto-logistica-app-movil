plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("kotlin-kapt") // Solo usamos KAPT
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
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)

    implementation("com.google.android.material:material:1.12.0")
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")

    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")

    // 1. Animaciones Lottie
    implementation("com.airbnb.android:lottie:6.1.0")

    // 2. Efecto de Carga Shimmer (Facebook)
    implementation("com.facebook.shimmer:shimmer:0.5.0")

    // 3. Botón Deslizar (Swipe to Confirm)
    implementation("com.ncorti:slidetoact:0.9.0")

    // Mapas y Ubicación
    implementation("com.mapbox.maps:android:11.0.0")
    implementation(libs.play.services.location)

    // Cámara
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    // Imágenes
    implementation(libs.glide)

    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    kapt("androidx.room:room-compiler:$roomVersion")

}