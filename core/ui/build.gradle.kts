plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.multiplatform.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}
kotlin {
    jvmToolchain(rootProject.extra["compilerJdk"] as Int)
    androidLibrary {
        namespace = "io.github.recrivenvi.connectorx.shared.ui"
        compileSdk = libs.versions.android.compile.get().toInt()
        minSdk = libs.versions.android.min.get().toInt()
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.fromTarget(
                (rootProject.extra["uiBytecode"] as JavaVersion).majorVersion
            ))
        }
        androidResources { enable = true }
    }
    sourceSets {
        commonMain.dependencies {
            api(compose.runtime)
            api(compose.foundation)
            api(compose.ui)
            implementation(compose.animation)
            api(compose.components.resources)
            api(libs.miuix.ui.common)
            implementation(libs.miuix.preference.common)
            implementation(libs.miuix.blur.common)
            implementation(libs.miuix.icons.common)
            implementation(libs.miuix.nav.common)
        }
    }
}
compose.resources {
    packageOfResClass = "io.github.recrivenvi.connectorx.shared.resources"
    publicResClass = true
}
