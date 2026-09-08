/*
 * Copyright 2026 MobileByteLabs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.mobilebytelabs.kmpflavors

/**
 * Toolchain versions the TestKit fixtures generate into their `plugins { }` blocks.
 *
 * ## Why this exists
 *
 * `.github/workflows/multi-kgp-matrix.yml` runs the TestKit suite once per KGP row and
 * passes the pin twice — as `-Pkmpflavor.test.kgp.version` and as `KMPF_TEST_KGP_VERSION`.
 * **Nothing read either one.** Every fixture hardcoded `kotlin("multiplatform") version
 * "2.2.21"`, so all rows of the "multi-KGP" matrix compiled the same Kotlin version — one
 * that was neither the declared floor (2.3.21) nor the current line. The matrix was green
 * because it was vacuous, not because the plugin worked across versions.
 *
 * Resolution order, most explicit first:
 *   1. `-Pkmpflavor.test.kgp.version=…` — forwarded by the build as a system property
 *   2. `KMPF_TEST_KGP_VERSION` — the env var the workflow also sets
 *   3. the project's own `libs.versions.toml` pin, injected by the build
 *   4. [FALLBACK_KGP] — only when the suite is run outside Gradle (e.g. straight from an IDE)
 *
 * Keep [FALLBACK_KGP] at the project's supported FLOOR, not its current version: if the
 * injection ever breaks again, the fixtures silently degrade to the oldest version the
 * plugin claims to support, which is the safer failure direction.
 */
internal object TestToolchainVersions {

    /** Supported floor — see the note above on why this is the floor and not the current pin. */
    private const val FALLBACK_KGP: String = "2.3.21"

    /** CMP pin used by the two fixtures that exercise Compose. */
    private const val FALLBACK_CMP: String = "1.10.3"

    val kgp: String = resolve("kmpflavor.test.kgp.version", "KMPF_TEST_KGP_VERSION", FALLBACK_KGP)

    val cmp: String = resolve("kmpflavor.test.cmp.version", "KMPF_TEST_CMP_VERSION", FALLBACK_CMP)

    private fun resolve(property: String, env: String, fallback: String): String = System.getProperty(property)?.takeIf { it.isNotBlank() }
        ?: System.getenv(env)?.takeIf { it.isNotBlank() }
        ?: fallback
}
