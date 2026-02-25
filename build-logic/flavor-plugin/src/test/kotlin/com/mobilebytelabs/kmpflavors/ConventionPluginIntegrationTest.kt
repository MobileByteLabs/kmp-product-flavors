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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * Integration tests for convention plugin wrapper pattern.
 *
 * These tests verify that the kmp-product-flavors plugin can be
 * wrapped by a convention plugin and configured programmatically.
 */
class ConventionPluginIntegrationTest {

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

            rootProject.name = "convention-test"
            """.trimIndent(),
        )
    }

    @Test
    fun `plugin can be configured programmatically like a convention plugin`() {
        buildFile.writeText(
            """
            plugins {
                kotlin("multiplatform") version "2.2.21"
                id("io.github.mobilebytelabs.kmp-product-flavors")
            }

            kotlin {
                jvm("desktop")
            }

            // Simulate convention plugin configuration function
            fun configureProjectFlavors(ext: com.mobilebytelabs.kmpflavors.KmpFlavorExtension) {
                ext.apply {
                    generateBuildConfig.set(true)
                    buildConfigPackage.set("com.example.convention")

                    flavorDimensions {
                        register("content_type") {
                            priority.set(0)
                        }
                    }

                    flavors {
                        register("demo") {
                            dimension.set("content_type")
                            isDefault.set(true)
                            applicationIdSuffix.set(".demo")
                        }
                        register("prod") {
                            dimension.set("content_type")
                        }
                    }
                }
            }

            // Apply the configuration (like a convention plugin would)
            configureProjectFlavors(kmpFlavors)
            """.trimIndent(),
        )

        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments("listFlavors", "--stacktrace")
            .withPluginClasspath()
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":listFlavors")?.outcome)
        assertTrue(result.output.contains("demo"))
        assertTrue(result.output.contains("prod"))
    }

    @Test
    fun `centralized enum-based flavor configuration works`() {
        buildFile.writeText(
            """
            plugins {
                kotlin("multiplatform") version "2.2.21"
                id("io.github.mobilebytelabs.kmp-product-flavors")
            }

            kotlin {
                jvm("desktop")
            }

            // Simulate centralized KmpFlavors object from convention plugin
            object ProjectFlavors {
                enum class Dimension(val priority: Int) {
                    CONTENT_TYPE(0),
                    ENVIRONMENT(1)
                }

                enum class Flavor(
                    val dim: Dimension,
                    val isDefault: Boolean = false,
                    val appIdSuffix: String? = null
                ) {
                    DEMO(Dimension.CONTENT_TYPE, true, ".demo"),
                    PROD(Dimension.CONTENT_TYPE),
                    DEV(Dimension.ENVIRONMENT, true),
                    STAGING(Dimension.ENVIRONMENT),
                }

                val defaultDimensions = setOf(Dimension.CONTENT_TYPE)
                val defaultFlavors = Flavor.values().filter { it.dim in defaultDimensions }
            }

            // Apply configuration using enums
            kmpFlavors {
                generateBuildConfig.set(true)
                buildConfigPackage.set("com.example.app")

                flavorDimensions {
                    ProjectFlavors.defaultDimensions.forEach { dim ->
                        register(dim.name.lowercase()) {
                            priority.set(dim.priority)
                        }
                    }
                }

                flavors {
                    ProjectFlavors.defaultFlavors.forEach { flavor ->
                        register(flavor.name.lowercase()) {
                            dimension.set(flavor.dim.name.lowercase())
                            isDefault.set(flavor.isDefault)
                            flavor.appIdSuffix?.let { applicationIdSuffix.set(it) }
                        }
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
        assertTrue(result.output.contains("demo"))
        assertTrue(result.output.contains("prod"))
    }

    @Test
    fun `module can override convention defaults`() {
        buildFile.writeText(
            """
            plugins {
                kotlin("multiplatform") version "2.2.21"
                id("io.github.mobilebytelabs.kmp-product-flavors")
            }

            kotlin {
                jvm("desktop")
            }

            // Initial convention configuration
            kmpFlavors {
                generateBuildConfig.set(true)
                buildConfigPackage.set("com.example.base")

                flavors {
                    register("demo") {
                        isDefault.set(true)
                    }
                    register("prod")
                }
            }

            // Module-level override (like a module would do after convention)
            kmpFlavors {
                buildConfigPackage.set("com.example.module")

                flavors {
                    named("demo") {
                        buildConfigField("String", "MODULE_API", "\"https://demo.module.com\"")
                    }
                    named("prod") {
                        buildConfigField("String", "MODULE_API", "\"https://module.com\"")
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
            "build/generated/kmpFlavors/commonMain/kotlin/com/example/module/FlavorConfig.kt",
        )
        assertTrue(generatedFile.exists())

        val content = generatedFile.readText()
        assertTrue(content.contains("MODULE_API"))
        assertTrue(content.contains("demo.module.com"))
    }

    @Test
    fun `multiple modules can share flavor configuration`() {
        // Create a multi-module setup simulation
        buildFile.writeText(
            """
            plugins {
                kotlin("multiplatform") version "2.2.21"
                id("io.github.mobilebytelabs.kmp-product-flavors")
            }

            kotlin {
                jvm("desktop")
            }

            // Shared configuration helper (like in a convention plugin)
            fun applySharedFlavors(pkg: String) {
                kmpFlavors {
                    generateBuildConfig.set(true)
                    buildConfigPackage.set(pkg)

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
            }

            // Apply shared configuration
            applySharedFlavors("com.example.shared")
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
            "build/generated/kmpFlavors/commonMain/kotlin/com/example/shared/FlavorConfig.kt",
        )
        assertTrue(generatedFile.exists())

        val content = generatedFile.readText()
        assertTrue(content.contains("IS_PREMIUM"))
    }

    @Test
    fun `variant filter works with convention-style configuration`() {
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
                    register("env") { priority.set(1) }
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
                        dimension.set("env")
                        isDefault.set(true)
                    }
                    register("prod") {
                        dimension.set("env")
                    }
                }

                // Exclude free + prod combination (like convention plugin would)
                variantFilter {
                    if (hasAllFlavors("free", "prod")) {
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
        assertTrue(!result.output.contains("freeProd") || result.output.contains("Variant filter excluded"))
    }
}
