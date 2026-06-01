/*
 * Copyright 2026 MobileByteLabs
 *
 * v2.6 Phase 4 — documentation surface for the conditional-targets sample.
 * Demonstrates `variantFilter { excludeTargets(...) }` discipline; the active
 * variant resolves to `freeDev` by default. Switch via `-PkmpFlavor=paidProd`
 * (or any of the 4 variants).
 *
 * **Note:** like multi-dim-3d's Sample.kt, this file is a documentation
 * surface — it's compiled via the active variant's `main` source set only.
 * Move into `src/desktopMain/kotlin/` to compile end-to-end.
 */

@file:Suppress("unused")

package com.example.condtargets

fun describeActive(): String =
    "active variant: ${BuildKonfig.VARIANT_NAME} " +
        "(premium=${BuildKonfig.IS_PREMIUM}, apiBase=${BuildKonfig.API_BASE_URL})"
