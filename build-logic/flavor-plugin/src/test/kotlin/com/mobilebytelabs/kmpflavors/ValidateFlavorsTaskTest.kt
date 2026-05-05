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

import com.mobilebytelabs.kmpflavors.tasks.ValidateFlavorsTask
import org.gradle.api.GradleException
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class ValidateFlavorsTaskTest {

    @TempDir
    lateinit var tempDir: File

    private lateinit var task: ValidateFlavorsTask

    @BeforeEach
    fun setup() {
        val project = ProjectBuilder.builder()
            .withProjectDir(tempDir)
            .build()

        task = project.tasks.create("testValidateFlavors", ValidateFlavorsTask::class.java)
        // Initialize all properties with empty defaults
        task.dimensionNames.set(emptySet())
        task.flavorDimensions.set(emptyMap())
        task.flavorDefaults.set(emptyMap())
        task.validVariantNames.set(emptySet())
        task.allFlavorNames.set(emptyList())
    }

    @Test
    fun `validate passes with valid configuration`() {
        task.dimensionNames.set(setOf("tier", "environment"))
        task.flavorDimensions.set(
            mapOf(
                "free" to "tier",
                "paid" to "tier",
                "dev" to "environment",
                "prod" to "environment",
            ),
        )
        task.flavorDefaults.set(
            mapOf(
                "free" to true,
                "paid" to false,
                "dev" to true,
                "prod" to false,
            ),
        )
        task.validVariantNames.set(setOf("freeDev", "freeProd", "paidDev", "paidProd"))
        task.activeVariantName.set("freeDev")
        task.allFlavorNames.set(listOf("free", "paid", "dev", "prod"))

        // Should not throw
        task.validate()
    }

    @Test
    fun `validate fails with duplicate flavor names`() {
        task.dimensionNames.set(emptySet())
        task.flavorDimensions.set(mapOf("free" to "", "paid" to ""))
        task.flavorDefaults.set(mapOf("free" to false, "paid" to false))
        task.validVariantNames.set(setOf("free", "paid"))
        task.allFlavorNames.set(listOf("free", "free", "paid")) // Duplicate

        val exception = assertThrows(GradleException::class.java) {
            task.validate()
        }

        assertTrue(exception.message?.contains("Duplicate flavor names") == true)
    }

    @Test
    fun `validate fails with invalid flavor name`() {
        task.dimensionNames.set(emptySet())
        task.flavorDimensions.set(mapOf("free-tier" to "")) // Invalid: contains hyphen
        task.flavorDefaults.set(mapOf("free-tier" to false))
        task.validVariantNames.set(setOf("free-tier"))
        task.allFlavorNames.set(listOf("free-tier"))

        val exception = assertThrows(GradleException::class.java) {
            task.validate()
        }

        assertTrue(exception.message?.contains("Invalid flavor names") == true)
    }

    @Test
    fun `validate fails when flavor has no dimension but dimensions are defined`() {
        task.dimensionNames.set(setOf("tier"))
        task.flavorDimensions.set(mapOf("free" to "")) // Missing dimension (empty string = no dimension)
        task.flavorDefaults.set(mapOf("free" to false))
        task.validVariantNames.set(emptySet())
        task.allFlavorNames.set(listOf("free"))

        val exception = assertThrows(GradleException::class.java) {
            task.validate()
        }

        assertTrue(exception.message?.contains("has no dimension") == true)
    }

    @Test
    fun `validate fails when flavor references unknown dimension`() {
        task.dimensionNames.set(setOf("tier"))
        task.flavorDimensions.set(mapOf("free" to "unknown")) // Unknown dimension
        task.flavorDefaults.set(mapOf("free" to false))
        task.validVariantNames.set(emptySet())
        task.allFlavorNames.set(listOf("free"))

        val exception = assertThrows(GradleException::class.java) {
            task.validate()
        }

        assertTrue(exception.message?.contains("unknown dimension") == true)
    }

    @Test
    fun `validate fails when dimension has no flavors`() {
        task.dimensionNames.set(setOf("tier", "environment"))
        task.flavorDimensions.set(
            mapOf(
                "free" to "tier",
                // No "environment" flavors
            ),
        )
        task.flavorDefaults.set(mapOf("free" to false))
        task.validVariantNames.set(emptySet())
        task.allFlavorNames.set(listOf("free"))

        val exception = assertThrows(GradleException::class.java) {
            task.validate()
        }

        assertTrue(exception.message?.contains("has no flavors assigned") == true)
    }

    @Test
    fun `validate fails when active variant is invalid`() {
        task.dimensionNames.set(emptySet())
        task.flavorDimensions.set(mapOf("free" to ""))
        task.flavorDefaults.set(mapOf("free" to false))
        task.validVariantNames.set(setOf("free"))
        task.activeVariantName.set("unknown") // Invalid
        task.allFlavorNames.set(listOf("free"))

        val exception = assertThrows(GradleException::class.java) {
            task.validate()
        }

        assertTrue(exception.message?.contains("is not valid") == true)
    }

    @Test
    fun `validate passes with no dimensions`() {
        task.dimensionNames.set(emptySet())
        task.flavorDimensions.set(mapOf("free" to "", "paid" to ""))
        task.flavorDefaults.set(mapOf("free" to true, "paid" to false))
        task.validVariantNames.set(setOf("free", "paid"))
        task.activeVariantName.set("free")
        task.allFlavorNames.set(listOf("free", "paid"))

        // Should not throw
        task.validate()
    }

    @Test
    fun `validate accepts valid Kotlin identifiers`() {
        task.dimensionNames.set(emptySet())
        task.flavorDimensions.set(
            mapOf(
                "free" to "",
                "paidPro" to "",
                "_internal" to "",
                "tier1" to "",
            ),
        )
        task.flavorDefaults.set(
            mapOf(
                "free" to false,
                "paidPro" to false,
                "_internal" to false,
                "tier1" to false,
            ),
        )
        task.validVariantNames.set(setOf("free", "paidPro", "_internal", "tier1"))
        task.allFlavorNames.set(listOf("free", "paidPro", "_internal", "tier1"))

        // Should not throw
        task.validate()
    }

    @Test
    fun `validate fails with empty flavor name`() {
        task.dimensionNames.set(emptySet())
        task.flavorDimensions.set(mapOf("" to ""))
        task.flavorDefaults.set(mapOf("" to false))
        task.validVariantNames.set(setOf(""))
        task.allFlavorNames.set(listOf(""))

        val exception = assertThrows(GradleException::class.java) {
            task.validate()
        }

        assertTrue(exception.message?.contains("Invalid flavor names") == true)
    }

    @Test
    fun `validate fails when flavor name starts with number`() {
        task.dimensionNames.set(emptySet())
        task.flavorDimensions.set(mapOf("1free" to ""))
        task.flavorDefaults.set(mapOf("1free" to false))
        task.validVariantNames.set(setOf("1free"))
        task.allFlavorNames.set(listOf("1free"))

        val exception = assertThrows(GradleException::class.java) {
            task.validate()
        }

        assertTrue(exception.message?.contains("Invalid flavor names") == true)
    }
}
