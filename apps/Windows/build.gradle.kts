plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21) } }
java { sourceCompatibility = JavaVersion.VERSION_21; targetCompatibility = JavaVersion.VERSION_21 }
dependencies {
    implementation(project(":ui"))
    implementation(project(":protocol"))
    implementation(compose.desktop.currentOs)
    implementation("top.yukonga.miuix.kmp:miuix-ui:${libs.versions.miuix.get()}")
    implementation("org.json:json:20250517")
    implementation("org.jmdns:jmdns:3.6.2")
    implementation("org.bouncycastle:bcpkix-jdk18on:1.81")
    implementation("net.java.dev.jna:jna-platform:5.17.0")
    testImplementation(libs.junit)
}
compose.desktop {
    application {
        mainClass = "io.github.recrivenvi.connectorx.windows.MainKt"
        nativeDistributions {
            packageName = "ConnectorX"
            packageVersion = "0.7.0"
            modules("java.naming", "jdk.crypto.ec", "jdk.crypto.cryptoki", "java.management")
        }
    }
}
