/**
 * Copyright (C) 2024 Dr. David H. Akehurst (http://dr.david.h.akehurst.net)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import com.github.gmazzo.buildconfig.BuildConfigExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import java.io.File

plugins {
    alias(libs.plugins.kotlin) apply false
    alias(libs.plugins.compose) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.dokka) apply false
    alias(libs.plugins.buildconfig) apply false
    alias(libs.plugins.credentials) apply true
    alias(libs.plugins.exportPublic) apply false
}
val kotlin_languageVersion = org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_1
val kotlin_apiVersion = org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_1
val jvmTargetVersion = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11

allprojects {

    repositories {
        mavenLocal {
            content {
                includeGroupByRegex("net\\.akehurst.+")
            }
        }
        mavenCentral()
        google()
    }

    val version_project: String by project
    val group_project = rootProject.name

    group = rootProject.name
    version = rootProject.libs.versions.project.get()

    project.layout.buildDirectory = File(rootProject.projectDir, ".gradle-build/${project.name}")

}

fun getProjectProperty(s: String) = project.findProperty(s) as String?

subprojects {

    apply(plugin = "maven-publish")
    apply(plugin = "signing")
    apply(plugin = "org.jetbrains.dokka")
    apply(plugin = "com.github.gmazzo.buildconfig")
    apply(plugin = "org.jetbrains.kotlin.multiplatform")


    configure<BuildConfigExtension> {
        val now = java.time.Instant.now()
        fun fBbuildStamp(): String = java.time.format.DateTimeFormatter.ISO_DATE_TIME.withZone(java.time.ZoneId.of("UTC")).format(now)
        fun fBuildDate(): String = java.time.format.DateTimeFormatter.ofPattern("yyyy-MMM-dd").withZone(java.time.ZoneId.of("UTC")).format(now)
        fun fBuildTime(): String = java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss z").withZone(java.time.ZoneId.of("UTC")).format(now)

        buildConfigField("String", "version", "\"${project.version}\"")
        buildConfigField("String", "buildStamp", "\"${fBbuildStamp()}\"")
        buildConfigField("String", "buildDate", "\"${fBuildDate()}\"")
        buildConfigField("String", "buildTime", "\"${fBuildTime()}\"")
    }

    configure<KotlinMultiplatformExtension> {
        jvm {
            val main by compilations.getting {
                compilerOptions.configure {
                    languageVersion.set(kotlin_languageVersion)
                    apiVersion.set(kotlin_apiVersion)
                    jvmTarget.set(jvmTargetVersion)
                }
            }
            val test by compilations.getting {
                compilerOptions.configure {
                    languageVersion.set(kotlin_languageVersion)
                    apiVersion.set(kotlin_apiVersion)
                    jvmTarget.set(jvmTargetVersion)
                }
            }
        }
        js {
            binaries.library()
            nodejs()
            browser()
        }

        // compose does not support native targets !

        @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
        wasmJs() {
            binaries.library()
        }
    }

    dependencies {
        "commonTestImplementation"(kotlin("test"))
        "commonTestImplementation"(kotlin("test-annotations-common"))
    }

    configure<SigningExtension> {
        useGpgCmd()
        val publishing = project.properties["publishing"] as PublishingExtension
        sign(publishing.publications)
    }


    configurations.all {
        // Check for updates every build
        resolutionStrategy.cacheChangingModulesFor(0, "seconds")
    }
}