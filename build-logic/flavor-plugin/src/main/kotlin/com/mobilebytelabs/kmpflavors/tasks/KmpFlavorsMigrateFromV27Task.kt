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

package com.mobilebytelabs.kmpflavors.tasks

import com.mobilebytelabs.kmpflavors.internal.V27MigrationDetector
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option

/**
 * v2.8 Wave B2 — Automated v2.7 → v2.8 consumer migration task.
 *
 * Dry-run by default; pass `--apply` to mutate source files.
 *
 * Usage:
 *   ./gradlew :kmpFlavorsMigrateFromV27          # dry-run — prints diff
 *   ./gradlew :kmpFlavorsMigrateFromV27 --apply  # applies changes
 */
abstract class KmpFlavorsMigrateFromV27Task : DefaultTask() {

    init {
        group = "kmpflavors"
        description =
            "Migrates a consumer project from v2.7 to v2.8 DSL shape. " +
            "Dry-run by default; pass --apply to mutate."
    }

    @get:Internal
    @set:Option(option = "apply", description = "Apply the mutations (default: dry-run)")
    var applyChanges: Boolean = false

    @TaskAction
    fun migrate() {
        val findings = V27MigrationDetector.scan(project.projectDir)

        if (findings.isEmpty()) {
            logger.lifecycle("✓ No v2.7 telltales detected — project appears to already be on v2.8")
            return
        }

        if (!applyChanges) {
            logger.lifecycle("=== DRY-RUN — pass --apply to mutate ===")
            findings.forEach { logger.lifecycle(it.diff()) }
            logger.lifecycle("=== ${findings.size} file(s) would be changed — run with --apply ===")
            return
        }

        findings.forEach { it.apply() }
        logger.lifecycle("✓ ${findings.size} file(s) migrated to v2.8 shape. Review with `git diff` before committing.")
    }
}
