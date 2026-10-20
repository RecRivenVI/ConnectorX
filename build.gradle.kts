plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.android.multiplatform.library) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.compose.multiplatform) apply false
}

// One compiler JDK; runtime/protocol bytecode remains compatible with the shell runtime.
extra["compilerJdk"] = 25
extra["uiBytecode"] = JavaVersion.VERSION_21
extra["runtimeBytecode"] = JavaVersion.VERSION_17
