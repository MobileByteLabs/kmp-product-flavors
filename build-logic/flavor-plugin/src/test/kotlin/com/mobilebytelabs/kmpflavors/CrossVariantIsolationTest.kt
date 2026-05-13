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
 * RFC §3 Q12 acceptance — cross-variant code isolation.
 *
 * Negative test: a symbol declared only in `commonPaid/SomePaidThing.kt`
 * MUST NOT be resolvable from `commonFree/SomeFreeFile.kt`. In v1.x
 * active-variant-only mode this "worked" by accident because the
 * inactive sibling source set isn't on the classpath. In v2.0 matrix
 * mode BOTH compilations run in parallel; the test verifies cross-
 * variant references still fail to compile (`Unresolved reference`).
 *
 * **Disabled in W1** for the same reason as `ExpectActualMatrixTest`:
 * per-variant source-set wiring lands in W2.
 */
class CrossVariantIsolationTest {

    @Test
    @Disabled("Enabled in v2.0 W2 when SourceSetConfigurator wires per-variant source sets")
    fun `referencing a commonPaid symbol from commonFree fails to compile`() {
        // TODO(v2.0-W2): build a TestKit project with:
        //   src/commonPaid/kotlin/PaidOnly.kt:
        //     object PaidOnly { const val SECRET = "paid" }
        //   src/commonFree/kotlin/LeakySite.kt:
        //     fun leak() = PaidOnly.SECRET  // <- must FAIL
        //
        // Run ./gradlew compileFreeKotlinDesktop and assert:
        //   - BUILD FAILED
        //   - output contains "Unresolved reference: PaidOnly"
    }
}
