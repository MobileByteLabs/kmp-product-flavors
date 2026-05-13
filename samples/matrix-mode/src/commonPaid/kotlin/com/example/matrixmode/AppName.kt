/*
 * Copyright 2026 MobileByteLabs
 */

package com.example.matrixmode

import kotlinx.coroutines.delay

actual fun appName(): String = "PaidApp"

/**
 * Paid-only API exercising the per-variant dependency (kotlinx-coroutines-core
 * is declared only on commonPaid). Importing this from commonFree would FAIL
 * with "Unresolved reference: kotlinx.coroutines" — Q12 cross-variant
 * isolation + Q17 per-variant deps.
 */
suspend fun paidOnlyDelay() {
    delay(1)
}
