plugins {
    alias(libs.plugins.compose)
    alias(libs.plugins.kotlin.compose)
}

dependencies {
    commonMainImplementation(compose.ui)
    commonMainImplementation(compose.foundation)
    commonMainImplementation(compose.material3)

    commonMainImplementation(project(":code-editor-api"))


    jvmTestImplementation(compose.desktop.currentOs)
}
