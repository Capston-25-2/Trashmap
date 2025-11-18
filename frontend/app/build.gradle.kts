// 1. 🌟 'Properties' 클래스를 사용하기 위해 import 구문을 파일 맨 위에 추가합니다.
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose.compiler)
}

// 'Properties()'의 빨간 줄이 이제 사라져야 합니다.
val properties = Properties()
val localPropertiesFile = rootProject.file("local.properties")

if (localPropertiesFile.exists()) {
    properties.load(localPropertiesFile.inputStream())
} else {
    // 2. 🌟 'logger.warn' 대신 'println'을 사용해 경고를 출력합니다.
    println("WARN: local.properties file not found. Using default values.")
}

val kakaoAppKey = properties.getProperty("KAKAO_NATIVE_APP_KEY", "DEFAULT_KEY_IF_NOT_FOUND")
val serverUrl = properties.getProperty("SERVER_BASE_URL", "\"http://127.0.0.1:8000\"")
android {
    namespace = "com.example.trashmapv2"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.trashmapv2"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        buildConfigField(
            "String",
            "KAKAO_NATIVE_APP_KEY",
            "\"$kakaoAppKey\""
        )

        buildConfigField(
            "String",
            "SERVER_BASE_URL",
            "\"$serverUrl\""
        )

        manifestPlaceholders["KAKAO_NATIVE_APP_KEY"] = kakaoAppKey

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // (기존의 implementation(libs.androidx...) 등은 그대로 둡니다)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation("androidx.compose.material:material-icons-extended")
    implementation("io.coil-kt:coil-compose:2.5.0")
    // ... (기존 카카오, 레트로핏 등) ...
    implementation("com.google.android.material:material:1.12.0")
    implementation("com.google.android.gms:play-services-location:21.2.0")
    implementation("com.kakao.sdk:v2-all:2.22.0")
    implementation("com.kakao.maps.open:android:2.12.18")
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")

    // --- ⬇️ 6. Jetpack Compose 핵심 의존성 추가 ⬇️ ---
    implementation("androidx.activity:activity-compose:1.8.0") // (최신 버전 확인)
    implementation(platform("androidx.compose:compose-bom:2023.08.00")) // BOM
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3") // Material 3 (Scaffold, NavigationBar 등)
    implementation("androidx.compose.ui:ui-tooling-preview") // 미리보기용
    debugImplementation("androidx.compose.ui:ui-tooling")
    // --- ⬆️ Jetpack Compose 핵심 의존성 추가 ⬆️ ---

    // (기존 테스트 의존성)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    val cameraxVersion = "1.3.1"
    implementation("androidx.camera:camera-core:${cameraxVersion}")
    implementation("androidx.camera:camera-camera2:${cameraxVersion}")
    implementation("androidx.camera:camera-lifecycle:${cameraxVersion}")
    implementation("androidx.camera:camera-view:${cameraxVersion}")
}