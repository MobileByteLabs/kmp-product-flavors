/*
 * Copyright 2026 MobileByteLabs
 *
 * iOS-only consumer code that references BuildKonfig.PerTarget.IosMain.* — the
 * v2.5 perTarget(name) { } DSL feature.
 *
 * v2.5 simplification: PerTarget block lives inside the main BuildKonfig object.
 * KMP source-set dependsOn discipline gates access at the language level — this
 * file in iosMain can reference IosMain; equivalent code in androidMain can't
 * (because iosMain's BuildKonfig wouldn't be on the androidMain classpath).
 *
 * True per-file source-set isolation deferred to v2.6 — see docs/SECRETS_INTEGRATION.md
 * "perTarget semantics" section.
 */

@file:Suppress("unused")

package com.example.buildkonfigrich

/**
 * Documents the perTarget("iosMain") { field(...) } DSL feature.
 * Generated field (resolve at compile time by moving to iosMain in a non-matrix build):
 *   BuildKonfig.PerTarget.IosMain.BUNDLE_ID_SUFFIX: String  (value: ".dev")
 *
 * In matrix mode, generated sources land in per-variant target source sets —
 * iosMain/kotlin cannot resolve BuildKonfig from commonMain generated output.
 */
fun iosBundleIdSuffix(): String = "<perTarget.IosMain.BUNDLE_ID_SUFFIX — see DSL docs>"
