/*
 * Copyright 2026 Anthropic, Inc.
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

package com.anthropic.kmpflavors

import com.anthropic.kmpflavors.tasks.GenerateBuildConfigTask
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class GenerateBuildConfigTaskTest {

    @TempDir
    lateinit var tempDir: File

    private lateinit var task: GenerateBuildConfigTask

    @BeforeEach
    fun setup() {
        val project = ProjectBuilder.builder()
            .withProjectDir(tempDir)
            .build()

        task = project.tasks.create("testGenerateBuildConfig", GenerateBuildConfigTask::class.java)
    }

    @Test
    fun `generate creates file with correct package structure`() {
        task.packageName.set("com.example.app")
        task.className.set("FlavorConfig")
        task.variantName.set("freeDev")
        task.allFlavorNames.set(setOf("free", "dev"))
        task.activeFlavorNames.set(setOf("free", "dev"))
        task.buildConfigFields.set(emptyMap())
        task.outputDirectory.set(tempDir)

        task.generate()

        val expectedFile = File(tempDir, "com/example/app/FlavorConfig.kt")
        assertTrue(expectedFile.exists(), "Generated file should exist")
    }

    @Test
    fun `generate includes VARIANT_NAME constant`() {
        task.packageName.set("com.example")
        task.className.set("Config")
        task.variantName.set("paidProd")
        task.allFlavorNames.set(emptySet())
        task.activeFlavorNames.set(emptySet())
        task.buildConfigFields.set(emptyMap())
        task.outputDirectory.set(tempDir)

        task.generate()

        val content = File(tempDir, "com/example/Config.kt").readText()
        assertTrue(content.contains("const val VARIANT_NAME: String = \"paidProd\""))
    }

    @Test
    fun `generate includes IS_FLAVOR constants for all flavors`() {
        task.packageName.set("com.example")
        task.className.set("Config")
        task.variantName.set("freeDev")
        task.allFlavorNames.set(setOf("free", "paid", "dev", "prod"))
        task.activeFlavorNames.set(setOf("free", "dev"))
        task.buildConfigFields.set(emptyMap())
        task.outputDirectory.set(tempDir)

        task.generate()

        val content = File(tempDir, "com/example/Config.kt").readText()
        assertTrue(content.contains("const val IS_FREE: Boolean = true"))
        assertTrue(content.contains("const val IS_PAID: Boolean = false"))
        assertTrue(content.contains("const val IS_DEV: Boolean = true"))
        assertTrue(content.contains("const val IS_PROD: Boolean = false"))
    }

    @Test
    fun `generate includes custom build config fields`() {
        task.packageName.set("com.example")
        task.className.set("Config")
        task.variantName.set("freeDev")
        task.allFlavorNames.set(emptySet())
        task.activeFlavorNames.set(emptySet())
        task.buildConfigFields.set(
            mapOf(
                "BASE_URL" to BuildConfigField("String", "BASE_URL", "\"https://dev-api.example.com\""),
                "IS_PREMIUM" to BuildConfigField("Boolean", "IS_PREMIUM", "false"),
                "MAX_ITEMS" to BuildConfigField("Int", "MAX_ITEMS", "100"),
            ),
        )
        task.outputDirectory.set(tempDir)

        task.generate()

        val content = File(tempDir, "com/example/Config.kt").readText()
        assertTrue(content.contains("const val BASE_URL: String = \"https://dev-api.example.com\""))
        assertTrue(content.contains("const val IS_PREMIUM: Boolean = false"))
        assertTrue(content.contains("const val MAX_ITEMS: Int = 100"))
    }

    @Test
    fun `generate creates valid Kotlin syntax`() {
        task.packageName.set("com.example.app")
        task.className.set("FlavorConfig")
        task.variantName.set("freeDev")
        task.allFlavorNames.set(setOf("free", "dev"))
        task.activeFlavorNames.set(setOf("free", "dev"))
        task.buildConfigFields.set(
            mapOf(
                "API_KEY" to BuildConfigField("String", "API_KEY", "\"abc123\""),
            ),
        )
        task.outputDirectory.set(tempDir)

        task.generate()

        val content = File(tempDir, "com/example/app/FlavorConfig.kt").readText()

        // Check structure
        assertTrue(content.contains("package com.example.app"))
        assertTrue(content.contains("object FlavorConfig {"))
        assertTrue(content.endsWith("}\n"))

        // Check no syntax issues
        assertTrue(!content.contains("const val : ")) // No empty names
        assertTrue(!content.contains("const val null")) // No null names
    }

    @Test
    fun `generate uses custom class name`() {
        task.packageName.set("com.example")
        task.className.set("AppBuildConfig")
        task.variantName.set("test")
        task.allFlavorNames.set(emptySet())
        task.activeFlavorNames.set(emptySet())
        task.buildConfigFields.set(emptyMap())
        task.outputDirectory.set(tempDir)

        task.generate()

        val expectedFile = File(tempDir, "com/example/AppBuildConfig.kt")
        assertTrue(expectedFile.exists())

        val content = expectedFile.readText()
        assertTrue(content.contains("object AppBuildConfig {"))
    }

    @Test
    fun `generate includes documentation comments`() {
        task.packageName.set("com.example")
        task.className.set("Config")
        task.variantName.set("freeDev")
        task.allFlavorNames.set(setOf("free", "dev"))
        task.activeFlavorNames.set(setOf("free", "dev"))
        task.buildConfigFields.set(emptyMap())
        task.outputDirectory.set(tempDir)

        task.generate()

        val content = File(tempDir, "com/example/Config.kt").readText()
        assertTrue(content.contains("Generated by KMP Product Flavors Plugin"))
        assertTrue(content.contains("DO NOT EDIT"))
        assertTrue(content.contains("Active variant: freeDev"))
        assertTrue(content.contains("Active flavors: dev, free") || content.contains("Active flavors: free, dev"))
    }
}
