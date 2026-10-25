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

    // 仅测试用：在 JVM 上跑真实的 SQLite，验证迁移 SQL 不会丢数据。
    // Room 的 MigrationTestHelper 需要设备，本地环境没有。
    testImplementation(libs.junit)
    testImplementation("org.xerial:sqlite-jdbc:3.45.1.0")
}