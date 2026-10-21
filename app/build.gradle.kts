plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}
val productId = "io.github.recrivenvi.connectorx"
val productName = "ConnectorX"
val bytecode = rootProject.extra["uiBytecode"] as JavaVersion

java { toolchain.languageVersion.set(JavaLanguageVersion.of(rootProject.extra["compilerJdk"] as Int)) }

// AGP's variant API owns the generated asset directory and its task dependency.
abstract class GenerateLicenseAssets @javax.inject.Inject constructor(
    private val fileSystemOperations: FileSystemOperations
) : DefaultTask() {
    @get:InputDirectory
    abstract val licenses: DirectoryProperty
    @get:InputFile
    abstract val inventory: RegularFileProperty
    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        fileSystemOperations.sync {
            from(licenses) { into("licenses") }
            from(inventory) { into("licenses") }
            into(outputDirectory)
        }
    }
}

val generateLicenseAssets = tasks.register<GenerateLicenseAssets>("generateLicenseAssets") {
    licenses.set(rootProject.layout.projectDirectory.dir("third_party/licenses"))
    inventory.set(rootProject.layout.projectDirectory.file("documents/reference/upstream-mishka.md"))
    outputDirectory.set(layout.buildDirectory.dir("generated/licenseAssets"))
}

androidComponents.onVariants { variant ->
    variant.sources.assets?.addGeneratedSourceDirectory(
        generateLicenseAssets,
        GenerateLicenseAssets::outputDirectory
    )
}

android {
    namespace = productId
    compileSdk = libs.versions.android.compile.get().toInt()
    defaultConfig {
        applicationId = productId
        minSdk = libs.versions.android.min.get().toInt()
        targetSdk = 36
        versionCode = 57
        versionName = "1.0.25"
        resValue("string", "app_name", productName)
    }
    buildFeatures { compose = true; buildConfig = true; resValues = true }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.jvmArgs(
                "--add-opens=java.base/java.lang=ALL-UNNAMED",
                "--add-opens=java.base/java.util=ALL-UNNAMED",
                "--add-opens=java.base/java.io=ALL-UNNAMED",
                "--add-opens=java.base/java.net=ALL-UNNAMED",
                "--add-opens=java.base/java.security=ALL-UNNAMED",
                "--add-opens=java.base/java.text=ALL-UNNAMED",
                "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED",
                "--add-opens=java.desktop/java.awt.font=ALL-UNNAMED",
                "--add-opens=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED"
            )
        }
    }
    compileOptions { sourceCompatibility = bytecode; targetCompatibility = bytecode }
}
dependencies {
    implementation(project(":core:ui"))
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.compose.ui.test)
    implementation(project(":core:privilege"))
    implementation(project(":core:protocol"))
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
