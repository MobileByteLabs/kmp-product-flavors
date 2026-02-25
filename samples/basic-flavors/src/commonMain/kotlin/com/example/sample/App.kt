/*
 * Copyright 2026 Anthropic, Inc.
 *
 * Main application demonstrating flavor-aware code.
 */

package com.example.sample

/**
 * Main application entry point demonstrating KMP Product Flavors.
 *
 * This class uses the generated [AppConfig] object to access
 * flavor-specific configuration at compile time.
 */
object App {

    /**
     * Prints the current configuration to the console.
     */
    fun printConfiguration() {
        println("=== KMP Product Flavors Demo ===")
        println()
        println("Variant: ${AppConfig.VARIANT_NAME}")
        println()
        println("--- Tier Settings ---")
        println("Premium: ${AppConfig.IS_PREMIUM}")
        println("Max Items: ${AppConfig.MAX_ITEMS}")
        println("Show Ads: ${AppConfig.SHOW_ADS}")
        println()
        println("--- Environment Settings ---")
        println("Base URL: ${AppConfig.BASE_URL}")
        println("Debug Mode: ${AppConfig.DEBUG_MODE}")
        println("Log Level: ${AppConfig.LOG_LEVEL}")
        println()
        println("--- Flavor Flags ---")
        printFlavorFlags()
        println()
        println("================================")
    }

    private fun printFlavorFlags() {
        // These constants are generated based on all defined flavors
        println("IS_FREE: ${AppConfig.IS_FREE}")
        println("IS_PAID: ${AppConfig.IS_PAID}")
        println("IS_DEV: ${AppConfig.IS_DEV}")
        println("IS_STAGING: ${AppConfig.IS_STAGING}")
        println("IS_PROD: ${AppConfig.IS_PROD}")
    }

    /**
     * Returns the API client configuration for the current flavor.
     */
    fun getApiConfig(): ApiConfig = ApiConfig(
        baseUrl = AppConfig.BASE_URL,
        debugMode = AppConfig.DEBUG_MODE,
        maxRetries = if (AppConfig.IS_PREMIUM) 5 else 2,
    )
}

/**
 * API client configuration.
 */
data class ApiConfig(val baseUrl: String, val debugMode: Boolean, val maxRetries: Int)
