plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.hpcalc.core.mathui"
    compileSdk = 35

    defaultConfig {
        minSdk = 29
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures { compose = true }
}

dependencies {
    // designsystem 以 api 暴露 Compose，这里借它拿到排版所需的 Compose 依赖。
    api(project(":core:model"))
    api(project(":core:designsystem"))
    testImplementation(libs.junit)
}