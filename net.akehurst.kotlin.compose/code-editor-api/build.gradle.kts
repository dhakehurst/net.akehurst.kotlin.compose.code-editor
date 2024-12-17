plugins {
    alias(libs.plugins.compose)
    alias(libs.plugins.kotlin.compose)
}

dependencies {
    commonMainApi(compose.ui)
}