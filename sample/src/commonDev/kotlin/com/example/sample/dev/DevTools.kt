/*
 * Copyright 2026 Anthropic, Inc.
 *
 * Development tools - only compiled when dev flavor is active.
 */

package com.example.sample.dev

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Developer tools for debugging and testing.
 *
 * This class is only included in dev builds. It provides
 * debug utilities, mock data generation, and feature toggles.
 */
object DevTools {

    private val logs = mutableListOf<LogEntry>()
    private val _logFlow = MutableStateFlow<List<LogEntry>>(emptyList())
    val logFlow: Flow<List<LogEntry>> = _logFlow.asStateFlow()

    private val featureToggles = mutableMapOf<String, Boolean>()

    /**
     * Logs a debug message.
     */
    fun log(tag: String, message: String, level: LogLevel = LogLevel.DEBUG) {
        val entry = LogEntry(
            timestamp = System.currentTimeMillis(),
            tag = tag,
            message = message,
            level = level,
        )
        logs.add(entry)
        _logFlow.value = logs.toList()

        // Also print to console
        println("[${level.name}][$tag] $message")
    }

    /**
     * Gets all logs.
     */
    fun getLogs(): List<LogEntry> = logs.toList()

    /**
     * Clears all logs.
     */
    fun clearLogs() {
        logs.clear()
        _logFlow.value = emptyList()
        println("[DevTools] Logs cleared")
    }

    /**
     * Exports logs to a string.
     */
    fun exportLogs(): String {
        return logs.joinToString("\n") { entry ->
            "[${entry.timestamp}][${entry.level}][${entry.tag}] ${entry.message}"
        }
    }

    /**
     * Sets a feature toggle.
     */
    fun setFeatureToggle(feature: String, enabled: Boolean) {
        featureToggles[feature] = enabled
        log("DevTools", "Feature toggle: $feature = $enabled")
    }

    /**
     * Gets a feature toggle value.
     */
    fun isFeatureEnabled(feature: String): Boolean {
        return featureToggles[feature] ?: false
    }

    /**
     * Gets all feature toggles.
     */
    fun getFeatureToggles(): Map<String, Boolean> = featureToggles.toMap()

    /**
     * Generates mock user data for testing.
     */
    fun generateMockUsers(count: Int): List<MockUser> {
        return (1..count).map { index ->
            MockUser(
                id = "user_$index",
                name = "Test User $index",
                email = "user$index@test.com",
                isPremium = index % 2 == 0,
            )
        }
    }

    /**
     * Generates mock items for testing.
     */
    fun generateMockItems(count: Int): List<MockItem> {
        return (1..count).map { index ->
            MockItem(
                id = "item_$index",
                title = "Test Item $index",
                description = "Description for item $index",
                price = (index * 9.99),
            )
        }
    }

    /**
     * Simulates a network delay.
     */
    suspend fun simulateNetworkDelay(delayMs: Long = 1000L) {
        log("DevTools", "Simulating network delay: ${delayMs}ms")
        kotlinx.coroutines.delay(delayMs)
        log("DevTools", "Network delay complete")
    }

    /**
     * Simulates a network error.
     */
    fun simulateNetworkError(): Nothing {
        log("DevTools", "Simulating network error", LogLevel.ERROR)
        throw DevNetworkException("Simulated network error for testing")
    }

    /**
     * Prints debug info about the current environment.
     */
    fun printEnvironmentInfo() {
        log("DevTools", "=== Environment Info ===")
        log("DevTools", "Runtime: ${System.getProperty("java.runtime.name") ?: "Unknown"}")
        log("DevTools", "Version: ${System.getProperty("java.version") ?: "Unknown"}")
        log("DevTools", "OS: ${System.getProperty("os.name") ?: "Unknown"}")
        log("DevTools", "Feature toggles: $featureToggles")
        log("DevTools", "========================")
    }
}

/**
 * Log entry data class.
 */
data class LogEntry(
    val timestamp: Long,
    val tag: String,
    val message: String,
    val level: LogLevel,
)

/**
 * Log levels.
 */
enum class LogLevel {
    VERBOSE,
    DEBUG,
    INFO,
    WARN,
    ERROR,
}

/**
 * Mock user for testing.
 */
data class MockUser(
    val id: String,
    val name: String,
    val email: String,
    val isPremium: Boolean,
)

/**
 * Mock item for testing.
 */
data class MockItem(
    val id: String,
    val title: String,
    val description: String,
    val price: Double,
)

/**
 * Exception for simulated network errors.
 */
class DevNetworkException(message: String) : Exception(message)
