plugins { alias(libs.plugins.android.library) }

val scrcpySource = rootProject.file("scrcpy/server/src/main")
check(scrcpySource.resolve("java/com/genymobile/scrcpy/Server.java").isFile &&
    scrcpySource.resolve("java/io/github/recrivenvi/connectorx/runtime/VirtualAppHooks.java").isFile) {
    "scrcpy fork 源码或 ConnectorX 修改不完整。请初始化 scrcpy 子模块到父仓库固定的集成提交。"
}

android {
    namespace = "com.genymobile.scrcpy"
    compileSdk = 36
    defaultConfig {
        minSdk = 29
        buildConfigField("String", "VERSION_NAME", "\"4.1\"")
    }
    buildFeatures { aidl = true; buildConfig = true }
    sourceSets {
        getByName("main") {
            java.srcDir(scrcpySource.resolve("java"))
            aidl.srcDir(scrcpySource.resolve("aidl"))
        }
        if (providers.gradleProperty("connectorxDiagnostics").orNull == "true") {
            getByName("debug").java.srcDir(rootProject.file("tools/diagnostics/android/runtime/java"))
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}

dependencies {
    implementation(project(":protocol"))
    testImplementation(libs.junit)
}
