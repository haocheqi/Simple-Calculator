plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.hpcalc.feature.calculator"
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
    api(project(":core:model"))
    api(project(":core:math"))
    api(project(":core:mathui"))
    api(project(":core:designsystem"))
    api(project(":data"))

    implementation(libs.androidx.activity.compose)
    implementation("androidx.compose.material:material-icons-extended")

    testImplementation(libs.junit)
}