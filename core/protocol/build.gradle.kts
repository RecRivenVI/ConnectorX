plugins { `java-library` }
val bytecode = rootProject.extra["runtimeBytecode"] as JavaVersion
java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(rootProject.extra["compilerJdk"] as Int))
    sourceCompatibility = bytecode
    targetCompatibility = bytecode
}
dependencies { testImplementation(libs.junit) }
