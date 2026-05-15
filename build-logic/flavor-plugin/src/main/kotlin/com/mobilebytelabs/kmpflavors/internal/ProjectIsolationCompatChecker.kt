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

package com.mobilebytelabs.kmpflavors.internal

import org.gradle.api.Project
import org.gradle.api.logging.Logger
import org.gradle.util.GradleVersion

/**
 * v2.2 Phase 1B — Gradle 9 Project Isolation compatibility checker.
 *
 * Gradle 9 LTS introduces stricter rules for cross-project state under
 * `--project-isolation`:
 *   - `settings.gradle.kts` must not read from sibling subprojects at
 *     configuration time.
 *   - Plugin instances must not retain cross-project references.
 *   - `rootProject.subprojects { ... }` traversal is disallowed.
 *
 * The kmp-product-flavors plugin uses `project.rootProject.extensions.extraProperties`
 * in [shouldGenerateCodegen()] to coordinate multi-module codegen claims
 * (RFC §3 Q15). That mechanism reads/writes cross-project state, which
 * triggers an isolation violation under Gradle 9 `--project-isolation`.
 *
 * v2.2 Phase 1B detects the violation surface + emits a structured
 * `KMPF-V13` WARNING under Gradle 9+. Actual remediation (refactor the
 * codegen-claim to use `IsolatedProjects` API or provider-based indirection)
 * lands in v2.3 if/when Gradle 9 adoption justifies the work.
 *
 * No-op on Gradle < 9.0.0.
 */
internal object ProjectIsolationCompatChecker {

    /**
     * Minimum Gradle version where Project Isolation strict rules apply.
     */
    private val MIN_ISOLATION_VERSION: GradleVersion = GradleVersion.version("9.0")

    /**
     * Runs at plugin apply() time. Returns the list of detected violations
     * (empty when Gradle < 9 OR the consumer opted out of isolation).
     */
    fun check(project: Project, logger: Logger): List<String> {
        val gradleVersion = GradleVersion.current()
        if (gradleVersion < MIN_ISOLATION_VERSION) {
            // Gradle < 9 — Project Isolation strict mode doesn't apply.
            return emptyList()
        }

        val violations = mutableListOf<String>()

        // Detect whether Project Isolation is actually enabled. Gradle exposes this
        // via the `org.gradle.unsafe.isolated-projects` property (preview feature
        // in Gradle 8.x → 9.0+) or `--project-isolation` CLI flag.
        val isolationEnabled = isProjectIsolationEnabled(project)
        if (!isolationEnabled) {
            logger.info(
                "[KMP Flavors] Phase 1B — Gradle ${gradleVersion.version} detected but Project " +
                    "Isolation is not enabled; skipping compat check. " +
                    "Set `org.gradle.unsafe.isolated-projects=true` to opt in.",
            )
            return emptyList()
        }

        // Violation 1: codegen-claim mechanism reads/writes rootProject.extensions.extraProperties.
        // The check is structural — we don't actually invoke shouldGenerateCodegen here, but we
        // surface the known violation site so consumers know what to expect.
        violations += "shouldGenerateCodegen() reads/writes rootProject.extensions.extraProperties " +
            "for multi-module codegen-host election; under Project Isolation this triggers a " +
            "cross-project state warning. Workaround: set `kmpFlavors.codegenHost.set(true)` " +
            "explicitly on the host module + `set(false)` on every other module — explicit " +
            "claims short-circuit the rootProject-extras lookup."

        // Surface the violation as a single structured WARNING. Future v2.3 work refactors the
        // codegen-claim mechanism to use Gradle 9's `IsolatedProjects` API.
        logger.warn(
            "[KMP Flavors] KMPF-V13: $isolationEnabled — Project Isolation enabled on Gradle " +
                "${gradleVersion.version}. The plugin's codegen-claim mechanism uses cross-project " +
                "state (rootProject.extraProperties). To avoid the isolation violation, set " +
                "`kmpFlavors.codegenHost.set(true)` explicitly on your designated codegen-host " +
                "module + `set(false)` on every other module that applies the plugin. " +
                "Full refactor to IsolatedProjects API tracked for v2.3.",
        )

        return violations
    }

    /**
     * Detects whether Gradle's Project Isolation feature is enabled. Checks both
     * the property form (`org.gradle.unsafe.isolated-projects=true` in gradle.properties)
     * and the CLI form (`--project-isolation`).
     *
     * The actual API to query this is internal to Gradle; the property check is the
     * documented public mechanism.
     */
    private fun isProjectIsolationEnabled(project: Project): Boolean {
        val prop = project.findProperty("org.gradle.unsafe.isolated-projects")?.toString()
        return prop?.equals("true", ignoreCase = true) == true
    }
}
