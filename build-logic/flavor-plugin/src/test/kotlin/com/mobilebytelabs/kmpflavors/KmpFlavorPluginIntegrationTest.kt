/*
 * Copyright 2026 MobileByteLabs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mobilebytelabs.kmpflavors

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class KmpFlavorPluginIntegrationTest {

    @TempDir
    lateinit var testProjectDir: File

    private lateinit var buildFile: File
    private lateinit var settingsFile: File

    @BeforeEach
    fun setup() {
        settingsFile = File(testProjectDir, "settings.gradle.kts")
        buildFile = File(testProjectDir, "build.gradle.kts")

        settingsFile.writeText(
            """
            pluginManagement {
                repositories {
                    gradlePluginPortal()
                    mavenCentral()
                    google()
                }
            }

            dependencyResolutionManagement {
                repositories {
                    mavenCentral()
                    google()
                }
            }

            rootProject.name = "test-project"
            """.trimIndent(),
        )
    }

    @Test
    fun `plugin applies successfully to KMP project`() {
        buildFile.writeText(
            """
            plugins {
                kotlin("multiplatform") version "2.2.21"
                id("io.github.mobilebytelabs.kmp-product-flavors")
            }

            kotlin {
                jvm("desktop")
            }

            kmpFlavors {
                generateBuildConfig.set(false)

                flavors {
                    register("free")
                    register("paid")
                }
            }
            """.trimIndent(),
        )

        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments("tasks", "--stacktrace")
            .withPluginClasspath()
            .build()

        assertTrue(result.output.contains("listFlavors"))
        assertTrue(result.output.contains("validateFlavors"))
    }

    @Test
    fun `listFlavors task runs successfully`() {
        buildFile.writeText(
            """
            plugins {
                kotlin("multiplatform") version "2.2.21"
                id("io.github.mobilebytelabs.kmp-product-flavors")
            }

            kotlin {
                jvm("desktop")
            }

            kmpFlavors {
                generateBuildConfig.set(false)

                flavorDimensions {
                    register("tier") { priority.set(0) }
                }

                flavors {
                    register("free") {
                        dimension.set("tier")
                        isDefault.set(true)
                    }
                    register("paid") {
                        dimension.set("tier")
                    }
                }
            }
            """.trimIndent(),
        )

        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments("listFlavors", "--stacktrace")
            .withPluginClasspath()
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":listFlavors")?.outcome)
        assertTrue(result.output.contains("free"))
        assertTrue(result.output.contains("paid"))
    }

    @Test
    fun `validateFlavors task passes with valid config`() {
        buildFile.writeText(
            """
            plugins {
                kotlin("multiplatform") version "2.2.21"
                id("io.github.mobilebytelabs.kmp-product-flavors")
            }

            kotlin {
                jvm("desktop")
            }

            kmpFlavors {
                generateBuildConfig.set(false)

                flavorDimensions {
                    register("tier") { priority.set(0) }
                    register("environment") { priority.set(1) }
                }

                flavors {
                    register("free") {
                        dimension.set("tier")
                        isDefault.set(true)
                    }
                    register("paid") {
                        dimension.set("tier")
                    }
                    register("dev") {
                        dimension.set("environment")
                        isDefault.set(true)
                    }
                    register("prod") {
                        dimension.set("environment")
                    }
                }
            }
            """.trimIndent(),
        )

        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments("validateFlavors", "--stacktrace")
            .withPluginClasspath()
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":validateFlavors")?.outcome)
        assertTrue(result.output.contains("Validation passed"))
    }

    @Test
    fun `generateFlavorBuildConfig creates config file`() {
        buildFile.writeText(
            """
            plugins {
                kotlin("multiplatform") version "2.2.21"
                id("io.github.mobilebytelabs.kmp-product-flavors")
            }

            kotlin {
                jvm("desktop")
            }

            kmpFlavors {
                generateBuildConfig.set(true)
                buildConfigPackage.set("com.example.test")
                buildConfigClassName.set("TestConfig")

                flavors {
                    register("free") {
                        isDefault.set(true)
                        buildConfigField("Boolean", "IS_PREMIUM", "false")
                    }
                    register("paid") {
                        buildConfigField("Boolean", "IS_PREMIUM", "true")
                    }
                }
            }
            """.trimIndent(),
        )

        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments("generateFlavorBuildConfig", "--stacktrace")
            .withPluginClasspath()
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":generateFlavorBuildConfig")?.outcome)

        val generatedFile = File(
            testProjectDir,
            "build/generated/kmpFlavors/commonMain/kotlin/com/example/test/TestConfig.kt",
        )
        assertTrue(generatedFile.exists())

        val content = generatedFile.readText()
        assertTrue(content.contains("object TestConfig"))
        assertTrue(content.contains("const val VARIANT_NAME: String = \"free\""))
        assertTrue(content.contains("const val IS_PREMIUM: Boolean = false"))
    }

    @Test
    fun `active variant can be set via gradle property`() {
        buildFile.writeText(
            """
            plugins {
                kotlin("multiplatform") version "2.2.21"
                id("io.github.mobilebytelabs.kmp-product-flavors")
            }

            kotlin {
                jvm("desktop")
            }

            kmpFlavors {
                generateBuildConfig.set(true)
                buildConfigPackage.set("com.example.test")

                flavors {
                    register("free") {
                        isDefault.set(true)
                        buildConfigField("Boolean", "IS_PREMIUM", "false")
                    }
                    register("paid") {
                        buildConfigField("Boolean", "IS_PREMIUM", "true")
                    }
                }
            }
            """.trimIndent(),
        )

        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments("generateFlavorBuildConfig", "-PkmpFlavor=paid", "--stacktrace")
            .withPluginClasspath()
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":generateFlavorBuildConfig")?.outcome)

        val generatedFile = File(
            testProjectDir,
            "build/generated/kmpFlavors/commonMain/kotlin/com/example/test/FlavorConfig.kt",
        )
        val content = generatedFile.readText()
        assertTrue(content.contains("const val VARIANT_NAME: String = \"paid\""))
        assertTrue(content.contains("const val IS_PREMIUM: Boolean = true"))
    }

    @Test
    fun `plugin creates flavor source sets`() {
        buildFile.writeText(
            """
            plugins {
                kotlin("multiplatform") version "2.2.21"
                id("io.github.mobilebytelabs.kmp-product-flavors")
            }

            kotlin {
                jvm("desktop")
            }

            kmpFlavors {
                generateBuildConfig.set(false)

                flavors {
                    register("free") {
                        isDefault.set(true)
                    }
                    register("paid")
                }
            }

            tasks.register("printSourceSets") {
                doLast {
                    kotlin.sourceSets.forEach { sourceSet ->
                        println("SourceSet: ${'$'}{sourceSet.name}")
                    }
                }
            }
            """.trimIndent(),
        )

        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments("printSourceSets", "--stacktrace")
            .withPluginClasspath()
            .build()

        assertTrue(result.output.contains("SourceSet: commonFree"))
        assertTrue(result.output.contains("SourceSet: commonPaid"))
        assertTrue(result.output.contains("SourceSet: desktopFree"))
        assertTrue(result.output.contains("SourceSet: desktopPaid"))
    }

    @Test
    fun `kmpFlavorInit creates source directories`() {
        buildFile.writeText(
            """
            plugins {
                kotlin("multiplatform") version "2.2.21"
                id("io.github.mobilebytelabs.kmp-product-flavors")
            }

            kotlin {
                jvm("desktop")
            }

            kmpFlavors {
                generateBuildConfig.set(false)

                flavors {
                    register("free") {
                        isDefault.set(true)
                    }
                    register("paid")
                }
            }
            """.trimIndent(),
        )

        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments("kmpFlavorInit", "--stacktrace")
            .withPluginClasspath()
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":kmpFlavorInit")?.outcome)
        assertTrue(result.output.contains("Initializing source directories"))

        // Verify directories were created
        assertTrue(File(testProjectDir, "src/commonFree/kotlin").exists())
        assertTrue(File(testProjectDir, "src/commonPaid/kotlin").exists())
        assertTrue(File(testProjectDir, "src/desktopFree/kotlin").exists())
        assertTrue(File(testProjectDir, "src/desktopPaid/kotlin").exists())
    }

    @Test
    fun `variantFilter excludes specified variants`() {
        buildFile.writeText(
            """
            plugins {
                kotlin("multiplatform") version "2.2.21"
                id("io.github.mobilebytelabs.kmp-product-flavors")
            }

            kotlin {
                jvm("desktop")
            }

            kmpFlavors {
                generateBuildConfig.set(false)

                flavorDimensions {
                    register("tier") { priority.set(0) }
                    register("environment") { priority.set(1) }
                }

                flavors {
                    register("free") {
                        dimension.set("tier")
                        isDefault.set(true)
                    }
                    register("paid") {
                        dimension.set("tier")
                    }
                    register("dev") {
                        dimension.set("environment")
                        isDefault.set(true)
                    }
                    register("prod") {
                        dimension.set("environment")
                    }
                }

                // Exclude freeProd variant
                variantFilter {
                    if (flavorNames.containsAll(listOf("free", "prod"))) {
                        exclude()
                    }
                }
            }
            """.trimIndent(),
        )

        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments("listFlavors", "--stacktrace")
            .withPluginClasspath()
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":listFlavors")?.outcome)
        assertTrue(result.output.contains("freeDev"))
        assertTrue(result.output.contains("paidDev"))
        assertTrue(result.output.contains("paidProd"))
        // freeProd should be excluded
        assertFalse(result.output.contains("freeProd"))
    }

    @Test
    fun `printFlavorProperties shows suffix values`() {
        buildFile.writeText(
            """
            plugins {
                kotlin("multiplatform") version "2.2.21"
                id("io.github.mobilebytelabs.kmp-product-flavors")
            }

            kotlin {
                jvm("desktop")
            }

            kmpFlavors {
                generateBuildConfig.set(false)

                flavors {
                    register("free") {
                        isDefault.set(true)
                        applicationIdSuffix.set(".free")
                        bundleIdSuffix.set(".free")
                        versionNameSuffix.set("-free")
                    }
                    register("paid") {
                        applicationIdSuffix.set(".paid")
                    }
                }
            }
            """.trimIndent(),
        )

        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments("printFlavorProperties", "--stacktrace")
            .withPluginClasspath()
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":printFlavorProperties")?.outcome)
        assertTrue(result.output.contains("Properties for variant: free"))
        assertTrue(result.output.contains("applicationIdSuffix: .free"))
        assertTrue(result.output.contains("bundleIdSuffix: .free"))
        assertTrue(result.output.contains("versionNameSuffix: -free"))
    }

    @Test
    fun `platform properties are exposed as extra properties`() {
        buildFile.writeText(
            """
            plugins {
                kotlin("multiplatform") version "2.2.21"
                id("io.github.mobilebytelabs.kmp-product-flavors")
            }

            kotlin {
                jvm("desktop")
            }

            kmpFlavors {
                generateBuildConfig.set(false)

                flavors {
                    register("free") {
                        isDefault.set(true)
                        applicationIdSuffix.set(".free")
                    }
                }
            }

            tasks.register("checkProperties") {
                doLast {
                    val variantName = project.extra["kmpFlavor.variantName"] as String
                    val suffix = project.extra["kmpFlavor.applicationIdSuffix"] as String
                    println("Variant: ${'$'}variantName")
                    println("Suffix: ${'$'}suffix")
                }
            }
            """.trimIndent(),
        )

        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments("checkProperties", "--stacktrace")
            .withPluginClasspath()
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":checkProperties")?.outcome)
        assertTrue(result.output.contains("Variant: free"))
        assertTrue(result.output.contains("Suffix: .free"))
    }
}
