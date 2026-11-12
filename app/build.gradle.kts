plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.drivepet.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.drivepet.app"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    buildTypes {
        debug {
            buildConfigField("String", "DEFAULT_SERVER_URL", "\"http://10.0.2.2:8080/api/v1/\"")
            buildConfigField("boolean", "DEMO_TOOLS", "true")
            manifestPlaceholders["distractionOptimized"] = "true"
        }
        release {
            // 공모전 규칙에 서명 키 요건이 없어 개발용(debug) 키로 서명한다. 앱마켓에 올릴 때 정식 키로 바꾼다.
            signingConfig = signingConfigs.getByName("debug")
            // 클라우드 서버(Oracle VM, server/README.md "클라우드 배포"). demo는 initWith(release)로 같은 주소를 쓴다
            buildConfigField("String", "DEFAULT_SERVER_URL", "\"https://129-225-197-147.sslip.io/api/v1/\"")
            buildConfigField("boolean", "DEMO_TOOLS", "false")
            manifestPlaceholders["distractionOptimized"] = "false"
        }
        create("demo") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            buildConfigField("boolean", "DEMO_TOOLS", "true")
            manifestPlaceholders["distractionOptimized"] = "true"
            matchingFallbacks += "release"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

ksp { arg("room.schemaLocation", "$projectDir/schemas") }

dependencies {
    implementation(project(":vehicle"))
    implementation("ai.pleos.playground:NaviHelper:3.0.5")
    implementation("ai.pleos.playground:TextToSpeech:3.3.1")   // 깜빡이 목소리

    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-kotlinx-serialization:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.zxing:core:3.5.3")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")

    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
}
