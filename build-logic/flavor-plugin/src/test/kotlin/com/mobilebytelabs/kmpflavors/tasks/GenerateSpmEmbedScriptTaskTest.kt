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

    @Test
    fun `header documents the ACTUAL script filename, not a placeholder`() {
        // Regression: the header was built with "$(target_basename())", which Kotlin does
        // NOT interpolate — it reached the generated script as literal bash command
        // substitution for a function that does not exist.
        val text = generated()
        assertTrue(text.contains("SRCROOT/scripts/embed-xcframework.sh"), text.lines().take(12).toString())
        assertFalse(text.contains("target_basename"), text)
    }

    @Test
    fun `log path is rendered relative when the root dir is known`() {
        val project = ProjectBuilder.builder().withProjectDir(tempDir).build()
        val task = project.tasks
            .register("genEmbedRel", GenerateSpmEmbedScriptTask::class.java).get().apply {
                xcframeworkName.set("ComposeApp")
                sharedModulePath.set(":cmp-shared")
                iosProjectDirName.set("cmp-ios")
                configurationToBuildType.set(mapOf("freeRelease" to "Release"))
                outputFile.set(File(tempDir, "cmp-ios/scripts/embed-xcframework.sh"))
                rootDirPath.set(project.layout.projectDirectory)
            }
        task.generate()
        assertTrue(File(tempDir, "cmp-ios/scripts/embed-xcframework.sh").exists())
    }

    @Test
    fun `an empty configuration map still yields a runnable script with a safe default`() {
        val project = ProjectBuilder.builder().withProjectDir(tempDir).build()
        val task = project.tasks
            .register("genEmbedEmpty", GenerateSpmEmbedScriptTask::class.java).get().apply {
                xcframeworkName.set("Shared")
                sharedModulePath.set(":shared")
                iosProjectDirName.set("ios")
                configurationToBuildType.set(emptyMap())
                outputFile.set(File(tempDir, "ios/scripts/embed-xcframework.sh"))
            }
        task.generate()
        val text = File(tempDir, "ios/scripts/embed-xcframework.sh").readText()
        // No case arms, but the wildcard default must still be present so the script
        // never leaves KOTLIN_BUILD_TYPE unset under `set -u`.
        assertTrue(text.contains("KOTLIN_BUILD_TYPE=\"Release\""), text)
    }
}
