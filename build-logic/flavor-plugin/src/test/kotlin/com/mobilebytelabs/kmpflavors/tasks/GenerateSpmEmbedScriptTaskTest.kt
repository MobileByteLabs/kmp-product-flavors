/*
 * Copyright 2026 MobileByteLabs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.mobilebytelabs.kmpflavors.tasks

import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * v2.9 — the Xcode-side half of end-to-end SPM.
 *
 * Generating `Package.swift` is not enough to make SPM work: something must ASSEMBLE the
 * XCFramework for the flavor Xcode is currently building and stage the SDK-matching slice.
 * Up to v2.8 every consumer hand-wrote that script (kmp-project-template's
 * `cmp-ios/scripts/embed-xcframework.sh` is the reference implementation these tests are
 * modelled on), and hand-rolling it meant re-deriving the
 * `{flavor}{BuildType}` → `NativeBuildType` mapping that the CocoaPods plugin used to own.
 *
 * The generated script derives that mapping from the DSL rather than glob-matching
 * configuration names, so a build type named `staging` with `isDebuggable = true` maps to
 * Debug — which a `*Debug` glob would get wrong.
 */
class GenerateSpmEmbedScriptTaskTest {

    @TempDir
    lateinit var tempDir: File

    private fun newTask(): GenerateSpmEmbedScriptTask {
        val project = ProjectBuilder.builder().withProjectDir(tempDir).build()
        return project.tasks
            .register("genEmbed", GenerateSpmEmbedScriptTask::class.java).get().apply {
                xcframeworkName.set("ComposeApp")
                sharedModulePath.set(":cmp-shared")
                iosProjectDirName.set("cmp-ios")
                configurationToBuildType.set(
                    mapOf(
                        "freeDevDebug" to "Debug",
                        "freeDevStaging" to "Debug",
                        "freeProdRelease" to "Release",
                        "paidProdRelease" to "Release",
                    ),
                )
                outputFile.set(File(tempDir, "cmp-ios/scripts/embed-xcframework.sh"))
            }
    }

    private fun generated(): String {
        newTask().generate()
        return File(tempDir, "cmp-ios/scripts/embed-xcframework.sh").readText()
    }

    @Test
    fun `emits an executable bash script with strict mode`() {
        newTask().generate()
        val script = File(tempDir, "cmp-ios/scripts/embed-xcframework.sh")
        assertTrue(script.exists())
        assertTrue(script.canExecute(), "script must be chmod +x for an Xcode Run-Script phase")
        val text = script.readText()
        assertTrue(text.startsWith("#!/usr/bin/env bash"), text.take(40))
        assertTrue(text.contains("set -euo pipefail"))
    }

    @Test
    fun `maps every declared Xcode configuration to its Kotlin build type`() {
        val text = generated()
        // Exact per-configuration mapping, derived from the DSL — this is what the removed
        // cocoapods { xcodeConfigurationToNativeBuildType[...] } block used to do.
        assertTrue(text.contains("freeDevDebug|freeDevStaging)"), text)
        assertTrue(text.contains("freeProdRelease|paidProdRelease)"), text)
        assertTrue(text.contains("KOTLIN_BUILD_TYPE=\"Debug\""), text)
        assertTrue(text.contains("KOTLIN_BUILD_TYPE=\"Release\""), text)
    }

    @Test
    fun `a debuggable non-Debug-named configuration still maps to Debug`() {
        // The regression a `*Debug` glob would introduce: `freeDevStaging` is declared
        // debuggable, so it must select the Debug slice.
        val text = generated()
        val debugCase = text.lines().first { it.contains("KOTLIN_BUILD_TYPE=\"Debug\"") }
        assertTrue(debugCase.contains("freeDevStaging"), debugCase)
    }

    @Test
    fun `assembles the XCFramework the SPM binary target points at`() {
        val text = generated()
        assertTrue(text.contains(":cmp-shared:assembleComposeApp\${KOTLIN_BUILD_TYPE}XCFramework"), text)
    }

    @Test
    fun `does not use embedAndSign which Kotlin rejects alongside SwiftPM dependencies`() {
        // Kotlin fails with "You have SwiftPM dependencies with embedAndSign integration"
        // the moment the Xcode project carries SwiftPM packages — which it does by
        // definition once Package.swift is consumed.
        // Asserted against EXECUTABLE lines only: the script documents *why* it avoids
        // embedAndSign, and that rationale is worth keeping in the generated output.
        val executable = generated().lines()
            .filterNot { it.trimStart().startsWith("#") }
        assertTrue(
            executable.none { it.contains("embedAndSignAppleFrameworkForXcode") },
            executable.filter { it.contains("embedAndSign") }.toString(),
        )
    }

    @Test
    fun `stages the SDK-matching slice for simulator and device`() {
        val text = generated()
        assertTrue(text.contains("SDK_NAME"), text)
        assertTrue(text.contains("simulator"), text)
        assertTrue(text.contains("ios-arm64"), text)
    }

    @Test
    fun `uses bash-3_2-safe lowercasing for the Xcode runner`() {
        val text = generated()
        // macOS ships bash 3.2; ${x,,} is a bash 4 feature and would fail on the runner.
        assertFalse(text.contains(",,}"), text)
        assertTrue(text.contains("tr '[:upper:]' '[:lower:]'"), text)
    }
}
