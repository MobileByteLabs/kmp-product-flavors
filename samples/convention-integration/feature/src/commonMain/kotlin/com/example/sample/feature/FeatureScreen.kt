/*
 * Copyright 2026 MobileByteLabs
 *
 * Example feature module code.
 */

package com.example.sample.feature

import com.example.sample.core.AppConfig

/**
 * Example feature screen that uses flavor configuration.
 */
object FeatureScreen {

    /**
     * Get the title for the feature screen.
     * In demo mode, appends "(Demo)" to indicate demo environment.
     */
    fun getTitle(): String {
        val baseTitle = "My Feature"
        return if (AppConfig.isDemoMode) {
            "$baseTitle (Demo)"
        } else {
            baseTitle
        }
    }

    /**
     * Whether to show debug UI elements.
     * In a real project, this would use the generated FlavorConfig:
     * ```kotlin
     * val showDebugUi: Boolean = FlavorConfig.SHOW_DEBUG_UI
     * ```
     */
    val showDebugUi: Boolean
        get() = AppConfig.isDemoMode // Placeholder
}
