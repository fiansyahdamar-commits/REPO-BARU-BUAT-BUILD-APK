plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.zallrw.admin"
    compileSdk = 35
    defaultConfig { applicationId = "com.zallrw.admin.v3"; minSdk = 23; targetSdk = 35; versionCode = 3; versionName = "3.0.0" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
