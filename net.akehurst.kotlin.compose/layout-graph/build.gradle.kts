plugins {
    alias(libs.plugins.compose)
    alias(libs.plugins.kotlin.compose)
}

kotlin {

    sourceSets {
        commonMain {
            dependencies {
                api(libs.nak.kotlinx.collections)

                implementation(compose.ui)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(libs.material.icons.core)
            }
        }
        jvmMain {
            dependencies {
                implementation(compose.desktop.currentOs)
            }
        }
    }
}