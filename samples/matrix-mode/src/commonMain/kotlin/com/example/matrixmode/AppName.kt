/*
 * Copyright 2026 MobileByteLabs
 */

package com.example.matrixmode

/**
 * Variant-specific name resolved by KMP's expect/actual mechanism.
 *
 * Each per-flavor source set (commonFree, commonPaid) provides its own
 * `actual` declaration. In matrix mode, BOTH variants compile in parallel
 * — `compileKotlinDesktop` resolves the active variant's actual via the
 * standard KMP source-set hierarchy; `compilePaidKotlinDesktop` resolves
 * paid's actual via the v2.0 per-variant source-set wiring.
 */
expect fun appName(): String
