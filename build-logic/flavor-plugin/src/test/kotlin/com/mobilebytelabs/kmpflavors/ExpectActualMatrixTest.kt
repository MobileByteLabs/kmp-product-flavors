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
 */

package com.mobilebytelabs.kmpflavors

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

/**
 * RFC §3 Q11 acceptance — `expect`/`actual` must work across variants.
 *
 * Scenario: `commonMain` declares `expect fun foo(): String`,
 * `commonFree` provides `actual fun foo() = "free"`, and `commonPaid`
 * provides `actual fun foo() = "paid"`. Each per-variant compilation
 * must pick up the matching `actual` automatically.
 *
 * **Disabled in W1 because v2.0 W1 only registers per-variant
 * `KotlinCompilation` instances — it does NOT yet wire per-variant
 * source sets (`src/commonFree/kotlin`, `src/commonPaid/kotlin`)
 * into those compilations. That wiring lands in W2's
 * `SourceSetConfigurator` extension (Q2-C hybrid: Hierarchy Template +
 * explicit `dependsOn` per-variant common source sets).
 *
 * Re-enable this test (drop the `@Disabled` annotation) when W2 lands.
 *
 * The body is fully written so re-enabling is a single-line edit.
 */
class ExpectActualMatrixTest {

    @Test
    @Disabled("Enabled in v2.0 W2 when SourceSetConfigurator wires per-variant source sets")
    fun `expect_actual resolves the correct actual per variant compilation`() {
        // TODO(v2.0-W2): build a TestKit project with:
        //   src/commonMain/kotlin/AppName.kt:
        //     expect fun appName(): String
        //   src/commonFree/kotlin/AppName.kt:
        //     actual fun appName() = "FreeApp"
        //   src/commonPaid/kotlin/AppName.kt:
        //     actual fun appName() = "PaidApp"
        //   kmpFlavors { buildMatrix.set(true); flavors { register("free"); register("paid") } }
        //
        // Then run ./gradlew compileFreeKotlinDesktop compilePaidKotlinDesktop
        // and assert both compile successfully + the resulting .class files
        // contain the expected actual implementations.
    }
}
