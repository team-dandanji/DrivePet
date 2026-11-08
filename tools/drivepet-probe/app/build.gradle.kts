plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.drivepet.probe"
    compileSdk = 35
    // AAOS Car API (기어·속도·주행 제한). 컴파일용 stub, 실행은 에뮬레이터의 car framework
    useLibrary("android.car")

    defaultConfig {
        applicationId = "com.drivepet.probe"
        minSdk = 28          // Pleos 요구사항
        targetSdk = 34       // Pleos Connect Emulator 2.1.0 = API 34
        versionCode = 1
        versionName = "0.1"
    }

    buildTypes {
        release { isMinifyEnabled = false }
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
    implementation("ai.pleos.playground:Vehicle:2.3.2")
    implementation("ai.pleos.playground:vehicle-data:1.1.11")
    implementation("androidx.core:core-ktx:1.13.1")
}
