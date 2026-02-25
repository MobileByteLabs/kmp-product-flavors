/*
 * Copyright 2026 Anthropic, Inc.
 *
 * Paid-tier premium features - only compiled when paid flavor is active.
 */

package com.example.sample.paid

/**
 * Premium features manager for the paid tier.
 *
 * This class is only included in paid-tier builds. It provides
 * advanced features that are not available in the free tier.
 */
class PremiumFeatures {

    private val unlockedFeatures = mutableSetOf<String>()

    init {
        // All premium features are unlocked by default
        unlockedFeatures.addAll(
            listOf(
                "unlimited_items",
                "cloud_sync",
                "custom_themes",
                "advanced_analytics",
                "priority_support",
                "offline_mode",
                "export_data",
                "api_access",
            ),
        )
    }

    /**
     * Checks if a feature is available.
     */
    fun isFeatureAvailable(feature: String): Boolean {
        return feature in unlockedFeatures
    }

    /**
     * Gets all available premium features.
     */
    fun getAvailableFeatures(): Set<String> = unlockedFeatures.toSet()

    /**
     * Enables cloud sync functionality.
     */
    fun enableCloudSync(userId: String): CloudSyncConfig {
        println("[Premium] Enabling cloud sync for user: $userId")
        return CloudSyncConfig(
            userId = userId,
            syncInterval = 60_000L, // 1 minute
            enableRealtime = true,
        )
    }

    /**
     * Exports user data in the specified format.
     */
    fun exportData(format: ExportFormat): ExportResult {
        println("[Premium] Exporting data in ${format.name} format...")
        return ExportResult(
            success = true,
            format = format,
            itemCount = 1000,
            exportPath = "/exports/data.${format.extension}",
        )
    }

    /**
     * Gets advanced analytics data.
     */
    fun getAnalytics(): AnalyticsData {
        return AnalyticsData(
            totalUsageMinutes = 1234,
            featureUsage = mapOf(
                "sync" to 500,
                "export" to 50,
                "themes" to 200,
            ),
            streakDays = 30,
        )
    }
}

/**
 * Cloud sync configuration.
 */
data class CloudSyncConfig(
    val userId: String,
    val syncInterval: Long,
    val enableRealtime: Boolean,
)

/**
 * Supported export formats.
 */
enum class ExportFormat(val extension: String) {
    JSON("json"),
    CSV("csv"),
    XML("xml"),
    PDF("pdf"),
}

/**
 * Export operation result.
 */
data class ExportResult(
    val success: Boolean,
    val format: ExportFormat,
    val itemCount: Int,
    val exportPath: String,
)

/**
 * Analytics data for premium users.
 */
data class AnalyticsData(
    val totalUsageMinutes: Long,
    val featureUsage: Map<String, Int>,
    val streakDays: Int,
)

/**
 * Factory function to create premium features manager.
 */
fun createPremiumFeatures(): PremiumFeatures {
    return PremiumFeatures()
}
