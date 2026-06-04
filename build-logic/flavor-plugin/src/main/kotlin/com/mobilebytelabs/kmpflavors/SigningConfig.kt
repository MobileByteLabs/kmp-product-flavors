/*
 * Copyright 2026 MobileByteLabs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mobilebytelabs.kmpflavors

import org.gradle.api.Named
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import java.io.File
import javax.inject.Inject

/**
 * v2.8 — Per-flavor signing config declaration.
 *
 * Public DSL element registered via `kmpFlavors { signingConfigs { create("release") { ... } } }`.
 * Backed by [com.mobilebytelabs.kmpflavors.internal.SigningConfigBridge] which reflectively
 * emits `android.signingConfigs.create(name)` and assigns the resolved AGP signing config
 * to flavors whose `signingConfig.set("name")` matches.
 *
 * Example usage:
 * ```kotlin
 * kmpFlavors {
 *     signingConfigs {
 *         create("release") {
 *             storeFile.set(file("keystore/release.jks"))
 *             storePasswordFromEnv("KEYSTORE_PASSWORD")
 *             keyAlias.set("kmpflavors")
 *             keyPasswordFromEnv("KEY_PASSWORD")
 *         }
 *     }
 *     flavors {
 *         register("prod") {
 *             dimension.set("environment")
 *             signingConfig.set("release")
 *             versionCode.set(280)
 *             versionName.set("2.8.0")
 *         }
 *     }
 * }
 * ```
 *
 * V51_SIGNING_ENV_VAR_SET — when a signing config's `storePassword`/`keyPassword` is empty
 * AND no `storePasswordFromEnv()` / `keyPasswordFromEnv()` resolution succeeded at apply time,
 * the validator fires a configuration-time error pointing at the missing env-var.
 */
abstract class SigningConfig @Inject constructor(
    private val configName: String,
    objects: ObjectFactory,
) : Named {

    /** AGP keystore path — resolved at apply time and assigned to `android.signingConfigs.create(name).storeFile`. */
    val storeFile: Property<File> = objects.property(File::class.java)

    /** AGP keystore password — prefer [storePasswordFromEnv] over hardcoded literals. */
    val storePassword: Property<String> = objects.property(String::class.java)

    /** Keystore alias to sign with. */
    val keyAlias: Property<String> = objects.property(String::class.java)

    /** AGP key password — prefer [keyPasswordFromEnv] over hardcoded literals. */
    val keyPassword: Property<String> = objects.property(String::class.java)

    /**
     * Convenience for env-var-driven store password resolution.
     *
     * Resolves the environment variable at configuration time and sets [storePassword].
     * If the env var is unset, [storePassword] stays unset and the V51 validator surfaces
     * the gap with a clear actionable error message.
     */
    fun storePasswordFromEnv(envVar: String) {
        System.getenv(envVar)?.let { storePassword.set(it) }
    }

    /** Counterpart to [storePasswordFromEnv] for the key password. */
    fun keyPasswordFromEnv(envVar: String) {
        System.getenv(envVar)?.let { keyPassword.set(it) }
    }

    override fun getName(): String = configName
}
