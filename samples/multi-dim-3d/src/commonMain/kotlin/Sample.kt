/*
 * Copyright 2026 MobileByteLabs
 *
 * Documentation surface for the multi-dim-3d sample. Demonstrates how a
 * consumer reads the BuildKonfig + (v2.6 Phase 3) AnalyticsTags fields generated
 * by the plugin. The active variant resolves to one of:
 *   devPhoneFree, devPhonePaid, devTabletFree, devTabletPaid, prdPhonePaid, prdTabletPaid
 * (Ordering is dimension-priority-based; see FlavorVariantResolver.)
 *
 * **Note:** this file is a documentation example — it's compiled by the plugin's
 * snapshot tests / TestKit fixtures, not by `:samples:multi-dim-3d:compileKotlinDesktop`.
 * The active variant's generated source sets land in `<target>Main`, not
 * `commonMain`, so `commonMain/kotlin/Sample.kt` cannot resolve them at compile
 * time. Move this file into `src/desktopMain/kotlin/` (or any platform's main
 * source set) to compile end-to-end in your own fork.
 *
 * Switch active variant via -PkmpFlavor=devPhoneFree (or any of the 6 variants).
 */

@file:Suppress("unused")

package com.example.multidim3d

/**
 * Documents the 3-dimension BuildKonfig + AnalyticsTags API surface.
 * Generated fields per variant (move to desktopMain to resolve at compile time):
 *
 *   BuildKonfig.IS_PREMIUM: Boolean   (false=free, true=paid)
 *   BuildKonfig.API_BASE_URL: String  ("https://api.dev.example.com" | "https://api.prd.example.com")
 *   BuildKonfig.IS_TABLET: Boolean    (false=phone, true=tablet)
 *   BuildKonfig.MAX_ITEMS: Int        (per-flavor cap)
 *
 *   AnalyticsTags.VARIANT_NAME: String
 *   AnalyticsTags.BUILD_TYPE: String
 *   AnalyticsTags.ENVIRONMENT: String
 *   AnalyticsTags.TIER: String
 */
fun describeActiveVariant(): String = buildString {
    appendLine("Multi-Dim-3D Sample")
    appendLine("===================")
    appendLine("3-dimension matrix: tier(free|paid) × env(dev|prd) × form(phone|tablet)")
    appendLine("variantFilter prunes free×prd×* → 6 final variants:")
    appendLine("  devPhoneFree, devTabletFree, devPhonePaid, devTabletPaid,")
    appendLine("  prdPhonePaid, prdTabletPaid")
    appendLine("Generated: BuildKonfig.IS_PREMIUM, API_BASE_URL, IS_TABLET, MAX_ITEMS")
}

fun describeAnalyticsTags(): String = buildString {
    appendLine("v2.6 Phase 3 — AnalyticsTags codegen surface:")
    appendLine("  AnalyticsTags.VARIANT_NAME, BUILD_TYPE, ENVIRONMENT, TIER")
    appendLine("Wired per-variant into each target's main defaultSourceSet.")
}
