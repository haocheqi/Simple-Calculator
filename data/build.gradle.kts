plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.hpcalc.core.data"
    compileSdk = 35

    defaultConfig {
        minSdk = 29
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    api(project(":core:model"))
    // :app 需要看到 RoomDatabase / Preferences 这些超类型，所以用 api 而非 implementation。
    api(libs.bundles.room)
    api(libs.androidx.datastore)
    ksp(libs.androidx.room.compiler)
}