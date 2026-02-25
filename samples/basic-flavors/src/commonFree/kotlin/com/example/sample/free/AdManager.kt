/*
 * Copyright 2026 Anthropic, Inc.
 *
 * Free-tier ad manager - only compiled when free flavor is active.
 */

package com.example.sample.free

/**
 * Ad manager for the free tier.
 *
 * This class is only included in free-tier builds. It demonstrates
 * how flavor-specific source sets can contain completely different
 * implementations.
 */
class AdManager {

    private var adsLoaded = false
    private var impressionCount = 0

    /**
     * Initializes the ad SDK.
     * In a real app, this would initialize AdMob, Unity Ads, etc.
     */
    fun initialize() {
        println("[AdManager] Initializing ad SDK...")
        adsLoaded = true
        println("[AdManager] Ad SDK initialized successfully")
    }

    /**
     * Shows a banner ad.
     */
    fun showBannerAd(placement: String) {
        if (!adsLoaded) {
            println("[AdManager] Warning: Ads not initialized")
            return
        }
        println("[AdManager] Showing banner ad at: $placement")
        impressionCount++
    }

    /**
     * Shows an interstitial ad.
     * Returns true if the ad was shown successfully.
     */
    fun showInterstitialAd(): Boolean {
        if (!adsLoaded) {
            println("[AdManager] Warning: Ads not initialized")
            return false
        }
        println("[AdManager] Showing interstitial ad...")
        impressionCount++
        return true
    }

    /**
     * Shows a rewarded video ad.
     * Returns the reward amount if successful, 0 otherwise.
     */
    fun showRewardedAd(onReward: (Int) -> Unit): Boolean {
        if (!adsLoaded) {
            println("[AdManager] Warning: Ads not initialized")
            return false
        }
        println("[AdManager] Showing rewarded video ad...")
        impressionCount++
        // Simulate reward
        onReward(10)
        return true
    }

    /**
     * Gets the total ad impression count.
     */
    fun getImpressionCount(): Int = impressionCount

    /**
     * Checks if ads are available.
     */
    fun areAdsAvailable(): Boolean = adsLoaded
}

/**
 * Helper function to create and initialize AdManager.
 */
fun createAdManager(): AdManager = AdManager().apply {
    initialize()
}
