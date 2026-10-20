plugins { alias(libs.plugins.android.library) }

val scrcpyRoot = rootProject.file("third_party/scrcpy")
val scrcpySource = scrcpyRoot.resolve("server/src/main")
check(scrcpySource.resolve("java/com/genymobile/scrcpy/Server.java").isFile &&
    scrcpySource.resolve("java/io/github/recrivenvi/connectorx/runtime/VirtualAppHooks.java").isFile) {
    "scrcpy fork 源码或 ConnectorX 修改不完整。请初始化 scrcpy 子模块到父仓库固定的集成提交。"
}

val scrcpyVersion = Regex("version:\\s*'([^']+)'").find(scrcpyRoot.resolve("meson.build").readText())!!.groupValues[1]
val bytecode = rootProject.extra["runtimeBytecode"] as JavaVersion
java { toolchain.languageVersion.set(JavaLanguageVersion.of(rootProject.extra["compilerJdk"] as Int)) }

android {
    namespace = "com.genymobile.scrcpy"
    compileSdk = libs.versions.privilege.compile.get().toInt()
    defaultConfig {
        minSdk = libs.versions.privilege.min.get().toInt()
        buildConfigField("String", "VERSION_NAME", "\"$scrcpyVersion\"")
    }
    buildFeatures { aidl = true; buildConfig = true }
    sourceSets {
        getByName("main") {
            java.srcDir(scrcpySource.resolve("java"))
            aidl.srcDir(scrcpySource.resolve("aidl"))
        }
    }
    compileOptions { sourceCompatibility = bytecode; targetCompatibility = bytecode }
}

dependencies {
    implementation(project(":core:protocol"))
    testImplementation(libs.junit)
}
