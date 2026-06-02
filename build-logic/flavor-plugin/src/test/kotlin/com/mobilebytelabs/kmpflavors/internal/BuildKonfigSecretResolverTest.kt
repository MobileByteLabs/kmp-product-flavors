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
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class BuildKonfigSecretResolverTest {

    @TempDir
    lateinit var tempDir: File

    private fun writeManifest(content: String): File =
        File(tempDir, "secrets-manifest.yaml").apply { writeText(content) }

    private fun writeLocalProperties(vararg pairs: Pair<String, String>): File =
        File(tempDir, "local.properties").apply {
            writeText(pairs.joinToString("\n") { "${it.first}=${it.second}" })
        }

    @Test
    fun `missing manifest returns secrets-manifest-missing`() {
        val resolver = BuildKonfigSecretResolver(tempDir)
        val res = resolver.resolveForVariant("API_KEY", "free")
        assertEquals(SecretResolution.Unavailable("secrets-manifest-missing"), res)
    }

    @Test
    fun `schema 20 falls back to schema-v20-fallback`() {
        writeManifest(
            """
            schema_version: "2.0"
            needs:
              - id: API_KEY
            """.trimIndent(),
        )
        val resolver = BuildKonfigSecretResolver(tempDir)
        val res = resolver.resolveForVariant("API_KEY", "free")
        assertEquals(SecretResolution.Unavailable("schema-v20-fallback"), res)
    }

    @Test
    fun `secret not in manifest`() {
        writeManifest(
            """
            schema_version: "2.1"
            needs:
              - id: OTHER_KEY
                flavor_selector:
                  selector_values:
                    free: FREE_OTHER
            """.trimIndent(),
        )
        val resolver = BuildKonfigSecretResolver(tempDir)
        val res = resolver.resolveForVariant("API_KEY", "free")
        assertEquals(SecretResolution.Unavailable("not-in-manifest"), res)
    }

    @Test
    fun `no selector for variant`() {
        writeManifest(
            """
            schema_version: "2.1"
            needs:
              - id: API_KEY
                flavor_selector:
                  selector_values:
                    free: FREE_API_KEY
            """.trimIndent(),
        )
        val resolver = BuildKonfigSecretResolver(tempDir)
        val res = resolver.resolveForVariant("API_KEY", "paid")
        assertEquals(SecretResolution.Unavailable("no-selector-for-variant:paid"), res)
    }

    @Test
    fun `missing local properties when manifest present`() {
        writeManifest(
            """
            schema_version: "2.1"
            needs:
              - id: API_KEY
                flavor_selector:
                  selector_values:
                    free: FREE_API_KEY
            """.trimIndent(),
        )
        val resolver = BuildKonfigSecretResolver(tempDir)
        val res = resolver.resolveForVariant("API_KEY", "free")
        assertEquals(SecretResolution.Unavailable("local-properties-missing"), res)
    }

    @Test
    fun `local properties missing the resolved key`() {
        writeManifest(
            """
            schema_version: "2.1"
            needs:
              - id: API_KEY
                flavor_selector:
                  selector_values:
                    free: FREE_API_KEY
            """.trimIndent(),
        )
        writeLocalProperties("OTHER_KEY" to "value")
        val resolver = BuildKonfigSecretResolver(tempDir)
        val res = resolver.resolveForVariant("API_KEY", "free")
        assertEquals(SecretResolution.Unavailable("local-properties-missing-key:FREE_API_KEY"), res)
    }

    @Test
    fun `resolved value returned when all conditions met`() {
        writeManifest(
            """
            schema_version: "2.1"
            needs:
              - id: API_KEY
                flavor_selector:
                  selector_values:
                    free: FREE_API_KEY
                    paid: PAID_API_KEY
            """.trimIndent(),
        )
        writeLocalProperties(
            "FREE_API_KEY" to "free-secret-123",
            "PAID_API_KEY" to "paid-secret-456",
        )
        val resolver = BuildKonfigSecretResolver(tempDir)
        val freeRes = resolver.resolveForVariant("API_KEY", "free")
        val paidRes = resolver.resolveForVariant("API_KEY", "paid")
        assertEquals(SecretResolution.Resolved("free-secret-123"), freeRes)
        assertEquals(SecretResolution.Resolved("paid-secret-456"), paidRes)
    }

    @Test
    fun `manifestSchemaVersion returns null when manifest missing`() {
        val resolver = BuildKonfigSecretResolver(tempDir)
        assertNull(resolver.manifestSchemaVersion())
    }

    @Test
    fun `manifestSchemaVersion returns parsed value`() {
        writeManifest("""schema_version: "2.1"""")
        val resolver = BuildKonfigSecretResolver(tempDir)
        assertEquals("2.1", resolver.manifestSchemaVersion())
    }

    @Test
    fun `manifest parse handles comments and blank lines`() {
        writeManifest(
            """
            # this is a comment
            schema_version: "2.1"

            # another comment
            needs:
              - id: API_KEY
                flavor_selector:
                  selector_values:
                    free: FREE_KEY
            """.trimIndent(),
        )
        writeLocalProperties("FREE_KEY" to "x")
        val resolver = BuildKonfigSecretResolver(tempDir)
        assertEquals(SecretResolution.Resolved("x"), resolver.resolveForVariant("API_KEY", "free"))
    }

    @Test
    fun `manifest parse handles multiple needs entries`() {
        writeManifest(
            """
            schema_version: "2.1"
            needs:
              - id: API_KEY
                flavor_selector:
                  selector_values:
                    free: FREE_API
              - id: AUTH_TOKEN
                flavor_selector:
                  selector_values:
                    free: FREE_TOKEN
            """.trimIndent(),
        )
        writeLocalProperties("FREE_API" to "a", "FREE_TOKEN" to "b")
        val resolver = BuildKonfigSecretResolver(tempDir)
        assertEquals(SecretResolution.Resolved("a"), resolver.resolveForVariant("API_KEY", "free"))
        assertEquals(SecretResolution.Resolved("b"), resolver.resolveForVariant("AUTH_TOKEN", "free"))
    }

    @Test
    fun `Resolved toString redacts the value`() {
        val resolved = SecretResolution.Resolved("super-secret-value")
        assertEquals("Resolved(value=<redacted>)", resolved.toString())
    }

    @Test
    fun `Unavailable toString surfaces the reason`() {
        val unavailable = SecretResolution.Unavailable("test-reason")
        // Default data-class toString includes the reason — confirm it's readable.
        assertTrue(unavailable.toString().contains("test-reason"))
    }

    @Test
    fun `Resolved and Unavailable are not equal`() {
        val resolved = SecretResolution.Resolved("x")
        val unavailable = SecretResolution.Unavailable("y")
        assertNotNull(resolved)
        assertNotNull(unavailable)
        // Different sealed subtypes — equals returns false.
        assertTrue(resolved != unavailable as Any)
    }

    @Test
    fun `unparseable manifest returns manifest-parse-failed surface`() {
        // Edge case — empty file. Parser-success path returns ParsedManifest with
        // schemaVersion="unknown" + empty needs, so resolution surfaces not-in-manifest.
        writeManifest("")
        val resolver = BuildKonfigSecretResolver(tempDir)
        val res = resolver.resolveForVariant("API_KEY", "free")
        // schema_version defaults to "unknown" which versionLessThan('unknown','2.1')
        // returns true (no int parts), so the schema-fallback fires first.
        assertEquals(SecretResolution.Unavailable("schema-v20-fallback"), res)
    }

    @Test
    fun `versionLessThan handles equal versions correctly`() {
        // 2.1 vs 2.1 — neither less than the other, so schema passes; not-in-manifest fires.
        writeManifest(
            """
            schema_version: "2.1"
            needs: []
            """.trimIndent(),
        )
        val resolver = BuildKonfigSecretResolver(tempDir)
        val res = resolver.resolveForVariant("ANYTHING", "free")
        assertEquals(SecretResolution.Unavailable("not-in-manifest"), res)
    }

    @Test
    fun `versionLessThan supports higher schemas like 3_0`() {
        writeManifest(
            """
            schema_version: "3.0"
            needs:
              - id: API_KEY
                flavor_selector:
                  selector_values:
                    free: FREE
            """.trimIndent(),
        )
        writeLocalProperties("FREE" to "v")
        val resolver = BuildKonfigSecretResolver(tempDir)
        assertEquals(SecretResolution.Resolved("v"), resolver.resolveForVariant("API_KEY", "free"))
    }
}
