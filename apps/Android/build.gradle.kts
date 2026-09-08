plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}
android {
    namespace = "io.github.recrivenvi.connectorx"
    compileSdk = 37
    defaultConfig {
        applicationId = "io.github.recrivenvi.connectorx"
        minSdk = 33
        targetSdk = 36
        versionCode = 25
        versionName = "0.7.0-media-options"
    }
    buildFeatures { compose = true; buildConfig = true }
    if (providers.gradleProperty("connectorxDiagnostics").orNull == "true") {
        sourceSets.getByName("debug") {
            java.srcDir(rootProject.file("tools/diagnostics/android/app/java"))
            kotlin.srcDir(rootProject.file("tools/diagnostics/android/app/java"))
            manifest.srcFile(rootProject.file("tools/diagnostics/android/app/AndroidManifest.xml"))
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_21; targetCompatibility = JavaVersion.VERSION_21 }
}
dependencies {
    implementation(project(":ui"))
    testImplementation(libs.junit)
    implementation(project(":runtime"))
    implementation(project(":protocol"))
    implementation(libs.activity.compose)
    implementation(libs.miuix.ui)
    implementation(libs.miuix.preference)
    implementation(libs.miuix.blur)
    implementation(libs.miuix.icons)
    implementation(libs.miuix.nav)
    implementation(libs.hiddenapibypass)
    implementation(compose.runtime)
    implementation(compose.foundation)
    implementation(compose.ui)
    implementation(compose.animation)
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)
}
