plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.example.trashmapv2"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.trashmapv2"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "KAKAO_NATIVE_APP_KEY", "\"e5752654d616f86696b8c1eb1242e213\"")
        manifestPlaceholders["KAKAO_NATIVE_APP_KEY"] = "e5752654d616f86696b8c1eb1242e213"

        buildFeatures {
            buildConfig = true
        }

        ndk {
            // 에뮬레이터(x86) 및 실제 기기(arm)용 라이브러리를 모두 포함
            abiFilters.addAll(listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64"))
        }

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
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    implementation("com.google.android.material:material:1.12.0")

    implementation("com.kakao.sdk:v2-all:2.22.0")

    implementation("com.kakao.maps.open:android:2.12.18")
    // Retrofit (서버 통신용)
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    // Gson (JSON <-> 코틀린 객체 변환용)
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
}