plugins {
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

dependencies {
    commonMainImplementation(compose.ui)
    commonMainImplementation(compose.foundation)
    commonMainImplementation(compose.material3)

    commonMainImplementation(project(":code-editor-api"))
}

compose {
    //because compose v1.6.0-alpha01 is not compatible with kotlin 1.9.22
   // kotlinCompilerPlugin.set("1.5.7") // same as "org.jetbrains.compose.compiler:compiler:1.5.7"
}