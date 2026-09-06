/*
 * Copyright 2026 MobileByteLabs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.mobilebytelabs.kmpflavors.internal

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * v2.9 — SPM end-to-end wiring. The manifest generator must never emit a `Package.swift`
 * whose `binaryTarget` points at an XCFramework that nothing in the build produces
 * (the v2.8 gap: `generateSpmManifest` had no `dependsOn` on any assemble task).
 *
 * These tests pin the producer-task resolution contract: given the set of task names a
 * consumer's build actually registers, pick the most specific XCFramework producer for
 * the active variant, or report none so the caller can fail loudly.
 */
class SpmXcframeworkResolverTest {

    @Test
    fun `candidate names are ordered most-specific first`() {
        val candidates = SpmXcframeworkResolver.candidateTaskNames(
            xcframeworkName = "ComposeApp",
            variantName = "freeDevDebug",
            buildTypeName = "debug",
        )
        assertEquals(
            listOf(
                "assembleComposeAppFreeDevDebugXCFramework",
                "assembleComposeAppDebugXCFramework",
                "assembleComposeAppXCFramework",
            ),
            candidates,
        )
    }

    @Test
    fun `prefers the per-variant producer when the build registers one`() {
        val existing = setOf(
            "assembleComposeAppXCFramework",
            "assembleComposeAppDebugXCFramework",
            "assembleComposeAppFreeDevDebugXCFramework",
        )
        val resolved = SpmXcframeworkResolver.resolveProducer(
            xcframeworkName = "ComposeApp",
            variantName = "freeDevDebug",
            buildTypeName = "debug",
            existingTaskNames = existing,
        )
        assertEquals("assembleComposeAppFreeDevDebugXCFramework", resolved)
    }

    @Test
    fun `falls back to the build-type producer that KGP's XCFramework DSL registers`() {
        // This is the real-world shape: the kmp-project-template declares
        // XCFramework("ComposeApp") and gets assembleComposeApp{Debug,Release}XCFramework.
        val existing = setOf(
            "assembleComposeAppXCFramework",
            "assembleComposeAppDebugXCFramework",
            "assembleComposeAppReleaseXCFramework",
        )
        val resolved = SpmXcframeworkResolver.resolveProducer(
            xcframeworkName = "ComposeApp",
            variantName = "freeDevDebug",
            buildTypeName = "debug",
            existingTaskNames = existing,
        )
        assertEquals("assembleComposeAppDebugXCFramework", resolved)
    }

    @Test
    fun `falls back to the umbrella producer when no build-type variant exists`() {
        val existing = setOf("assembleComposeAppXCFramework")
        val resolved = SpmXcframeworkResolver.resolveProducer(
            xcframeworkName = "ComposeApp",
            variantName = "freeDevDebug",
            buildTypeName = "debug",
            existingTaskNames = existing,
        )
        assertEquals("assembleComposeAppXCFramework", resolved)
    }

    @Test
    fun `returns null when the build produces no XCFramework at all`() {
        val resolved = SpmXcframeworkResolver.resolveProducer(
            xcframeworkName = "ComposeApp",
            variantName = "freeDevDebug",
            buildTypeName = "debug",
            existingTaskNames = setOf("assemble", "build", "generateSpmManifest"),
        )
        assertNull(resolved)
    }

    @Test
    fun `null build type still resolves the umbrella producer`() {
        val resolved = SpmXcframeworkResolver.resolveProducer(
            xcframeworkName = "Shared",
            variantName = "free",
            buildTypeName = null,
            existingTaskNames = setOf("assembleSharedXCFramework"),
        )
        assertEquals("assembleSharedXCFramework", resolved)
    }

    @Test
    fun `output path is RELATIVE to the manifest, never absolute`() {
        // Package.swift is committed/consumed on other machines — an absolute path would
        // bake in the author's home directory.
        val path = SpmXcframeworkResolver.conventionalOutputPath(
            xcframeworkName = "ComposeApp",
            nativeBuildType = "debug",
        )
        assertEquals("../../XCFrameworks/debug/ComposeApp.xcframework", path)
        assertFalse(path.startsWith("/"), path)
    }

    @Test
    fun `a debuggable non-release build type maps to the debug bucket`() {
        // KGP emits only debug/release buckets. A build type named `staging` that is
        // declared debuggable must resolve to `debug`, not to a `staging` directory
        // that never exists.
        val native = SpmXcframeworkResolver.nativeBuildTypeFor(debuggable = true)
        assertEquals("debug", native)
        assertEquals(
            "../../XCFrameworks/debug/Shared.xcframework",
            SpmXcframeworkResolver.conventionalOutputPath("Shared", native),
        )
        assertEquals("release", SpmXcframeworkResolver.nativeBuildTypeFor(debuggable = false))
    }

    @Test
    fun `missing-producer diagnostic names the task the consumer should declare`() {
        val message = SpmXcframeworkResolver.missingProducerMessage(
            xcframeworkName = "ComposeApp",
            variantName = "freeDevDebug",
        )
        assertTrue(message.contains("XCFramework(\"ComposeApp\")"), message)
        assertTrue(message.contains("freeDevDebug"), message)
        assertTrue(message.contains("spm.requireXcframework"), message)
    }

    @Test
    fun `null or blank native build type falls back to the release bucket`() {
        // KGP always emits a release aggregate; a flavor-only matrix (no build-type
        // dimension) therefore resolves to `release` rather than an empty path segment.
        assertEquals(
            "../../XCFrameworks/release/Shared.xcframework",
            SpmXcframeworkResolver.conventionalOutputPath("Shared", null),
        )
        assertEquals(
            "../../XCFrameworks/release/Shared.xcframework",
            SpmXcframeworkResolver.conventionalOutputPath("Shared", "   "),
        )
    }

    @Test
    fun `an already-capitalised variant name is not double-capitalised`() {
        // Variant names normally arrive lowerCamel, but a consumer may register a flavor
        // with a leading capital; the task name must stay well-formed either way.
        val candidates = SpmXcframeworkResolver.candidateTaskNames(
            xcframeworkName = "Shared",
            variantName = "FreeDebug",
            buildTypeName = "Debug",
        )
        assertEquals("assembleSharedFreeDebugXCFramework", candidates.first())
        assertTrue(candidates.contains("assembleSharedDebugXCFramework"), candidates.toString())
    }

    @Test
    fun `mixed-case build type is lowercased into the bucket path`() {
        assertEquals(
            "../../XCFrameworks/debug/Shared.xcframework",
            SpmXcframeworkResolver.conventionalOutputPath("Shared", "DEBUG"),
        )
    }
}
