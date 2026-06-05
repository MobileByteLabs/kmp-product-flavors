package com.example.runtime

/**
 * Demonstrates consuming KmpFlavorsRuntime from commonMain.
 * The actual runtime values are resolved per-platform at compile/runtime.
 */
object AppRuntime {
    fun describe(): String = "Runtime API integration sample (accessed from commonMain)"
}
