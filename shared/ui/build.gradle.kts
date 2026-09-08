plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.multiplatform.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}
kotlin {
    androidLibrary {
        namespace = "io.github.recrivenvi.connectorx.shared.ui"
        compileSdk = 37
        minSdk = 33
        compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21) }
        androidResources { enable = true }
    }
    jvm { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21) } }
    sourceSets {
        commonMain.dependencies {
            api(compose.runtime)
            api(compose.foundation)
            api(compose.ui)
            implementation(compose.animation)
            implementation(compose.components.resources)
            api("top.yukonga.miuix.kmp:miuix-ui:${libs.versions.miuix.get()}")
            implementation("top.yukonga.miuix.kmp:miuix-preference:${libs.versions.miuix.get()}")
            implementation("top.yukonga.miuix.kmp:miuix-blur:${libs.versions.miuix.get()}")
            implementation("top.yukonga.miuix.kmp:miuix-icons:${libs.versions.miuix.get()}")
            implementation("top.yukonga.miuix.kmp:miuix-nav:${libs.versions.miuix.get()}")
        }
    }
}
compose.resources { packageOfResClass = "io.github.recrivenvi.connectorx.shared.resources" }
