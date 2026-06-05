package com.example.dsl

/**
 * Kitchen-sink v2.8 sample entry point.
 * Demonstrates consuming BuildConfig fields generated from multi-dimensional
 * flavors (tier × env) with v2.8 signingConfigs + versionCode/versionName DSL.
 */
object App {
    fun describe(): String = buildString {
        appendLine("DSL Completeness Integration Sample")
        appendLine("====================================")
        appendLine("Flavor: ${BuildKonfig.VARIANT_NAME}")
        appendLine("Tier Premium: ${BuildKonfig.TIER_PREMIUM}")
        appendLine("Rate Limit: ${BuildKonfig.RATE_LIMIT}")
        appendLine("API URL: ${BuildKonfig.API_URL}")
        appendLine("Env Demo: ${BuildKonfig.ENV_DEMO}")
    }
}
