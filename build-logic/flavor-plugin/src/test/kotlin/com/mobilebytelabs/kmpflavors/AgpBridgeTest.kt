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

import com.mobilebytelabs.kmpflavors.internal.AgpBridge
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.gradle.api.Project
import org.gradle.api.logging.Logger
import org.gradle.api.plugins.PluginContainer
import org.junit.jupiter.api.Test

/**
 * Smoke tests for the AGP bridge.
 *
 * Full integration tests that exercise actual AGP propagation live in
 * `KmpFlavorPluginIntegrationTest` (TestKit-based). These tests verify the
 * defensive behaviours that don't require AGP on the classpath.
 */
class AgpBridgeTest {

    private val logger = mockk<Logger>(relaxed = true)

    @Test
    fun `apply is a no-op when both flags are disabled`() {
        val project = mockk<Project>(relaxed = true)

        AgpBridge.apply(
            project = project,
            bridgeProductFlavors = false,
            bridgeBuildTypes = false,
            kmpDimensions = emptyList(),
            kmpFlavors = emptyList(),
            kmpBuildTypes = emptyList(),
            logger = logger,
        )

        // No project lookup should have happened — early return.
        verify(exactly = 0) { project.plugins }
    }

    @Test
    fun `apply skips silently when com_android_application is not applied`() {
        val plugins = mockk<PluginContainer>(relaxed = true) {
            every { hasPlugin("com.android.application") } returns false
        }
        val project = mockk<Project>(relaxed = true) {
            every { this@mockk.plugins } returns plugins
        }

        AgpBridge.apply(
            project = project,
            bridgeProductFlavors = true,
            bridgeBuildTypes = true,
            kmpDimensions = emptyList(),
            kmpFlavors = emptyList(),
            kmpBuildTypes = emptyList(),
            logger = logger,
        )

        // Should log info (skip) and never touch project.extensions.
        verify(atLeast = 1) { logger.info(match<String> { it.contains("skipping AGP bridge") }) }
        verify(exactly = 0) { project.extensions }
    }
}
