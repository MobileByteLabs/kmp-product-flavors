package com.example.dsl

/**
 * Kitchen-sink v2.8 sample entry point.
 * Demonstrates the complete v2.8 DSL surface declared in build.gradle.kts:
 *   - flavorDimensions (tier × env) with priorities
 *   - flavors with buildConfigField (String, Boolean, Int)
 *   - signingConfigs DSL (v2.8 Wave A1)
 *   - per-flavor versionCode / versionName (v2.8 Wave A1)
 *   - applicationIdSuffix, createIntermediateSourceSets
 *
 * The plugin processes the full DSL at configuration time; this entry point
 * exists to give the sample a compilable Kotlin source root.
 */
object App {
    fun describe(): String = buildString {
        appendLine("DSL Completeness Integration Sample")
        appendLine("====================================")
        appendLine("v2.8 DSL features exercised:")
        appendLine("  dimensions : tier (priority 0) × env (priority 1)")
        appendLine("  flavors    : free/premium × demo/prod")
        appendLine("  signing    : signingConfigs { register(\"release\") { ... } }")
        appendLine("  versioning : per-flavor versionCode + versionName")
        appendLine("  buildConfig: String, Boolean, Int fields per flavor")
        appendLine("  extras     : applicationIdSuffix, createIntermediateSourceSets")
    }
}
