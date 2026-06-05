// v2.7 — pure-AGP workaround
// This file was required in v2.7 to bridge AGP productFlavors with KMP.
// In v2.8, the plugin handles this automatically — this file can be deleted.
package com.example.app

/** v2.7 flavor identity bridge — no longer needed in v2.8. */
object AppFlavor {
    const val NAME: String = BuildConfig.FLAVOR
    const val IS_DEMO: Boolean = BuildConfig.IS_DEMO
}
