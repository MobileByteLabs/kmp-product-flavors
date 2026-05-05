/*
 * Copyright 2026 MobileByteLabs
 *
 * Main application entry point.
 */

package com.example.sample.app

import com.example.sample.core.AppConfig
import com.example.sample.feature.FeatureScreen

/**
 * Main application class.
 */
object App {

    /**
     * Application entry point.
     */
    fun main() {
        println("=== Convention Integration Sample ===")
        println("Demo Mode: ${AppConfig.isDemoMode}")
        println("API URL: ${AppConfig.apiUrl}")
        println("Logging: ${AppConfig.loggingEnabled}")
        println()
        println("Feature Title: ${FeatureScreen.getTitle()}")
        println("Show Debug UI: ${FeatureScreen.showDebugUi}")
    }
}
