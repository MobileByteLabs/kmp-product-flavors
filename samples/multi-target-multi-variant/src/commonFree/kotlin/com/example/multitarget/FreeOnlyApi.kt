/*
 * Copyright 2026 MobileByteLabs
 *
 * commonFree source set — only visible to free* variants. Verifies
 * cross-variant isolation: paid* + enterprise* variants compiling code
 * that references FreeOnlyApi would fail with "Unresolved reference".
 */

package com.example.multitarget

object FreeOnlyApi {
    fun showAd(): String = "show free-tier ad"
}
