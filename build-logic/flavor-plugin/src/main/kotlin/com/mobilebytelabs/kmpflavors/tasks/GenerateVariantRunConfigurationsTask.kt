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

package com.mobilebytelabs.kmpflavors.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * v2.1 Phase 4 — RFC §3 G22 / RFC §3 Q9.
 *
 * Generates one IntelliJ / Android Studio Run Configuration **per
 * (variant × target)** in matrix mode. Sibling to the existing
 * [GenerateRunConfigurationsTask] which produces one config per variant
 * scoped to `assemble`.
 *
 * Each emitted `.run.xml` invokes the variant-specific compile task on
 * a single target — `compile{Variant}Kotlin{Target}` — so developers
 * can build / debug a specific variant on a specific target directly
 * from the IDE's run dropdown without editing `-PkmpFlavor` manually.
 *
 * Active variant compiles through the standard `compileKotlin{Target}`
 * task (no per-variant task), so the active variant's row produces
 * `compileKotlin{Target}` configs instead.
 *
 * Output naming: `{projectName}_{variant}_{target}.run.xml`.
 */
abstract class GenerateVariantRunConfigurationsTask : DefaultTask() {

    @get:Input
    abstract val projectName: Property<String>

    @get:Input
    abstract val projectPath: Property<String>

    /**
     * Variant names in registration order (active + inactive).
     */
    @get:Input
    abstract val variantNames: ListProperty<String>

    /**
     * Target names in registration order. Run configurations are emitted for
     * the cartesian product variantNames × targetNames.
     */
    @get:Input
    abstract val targetNames: ListProperty<String>

    /**
     * The active variant name — compiled through the standard
     * `compileKotlin{Target}` task; inactive variants compile through
     * `compile{Variant}Kotlin{Target}`.
     */
    @get:Input
    abstract val activeVariantName: Property<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    init {
        group = "kmp flavors"
        description = "Generates IDE run configurations for each variant × target pair (matrix mode)"
    }

    @TaskAction
    fun generate() {
        val outputDir = outputDirectory.get().asFile
        outputDir.mkdirs()

        val projectNameValue = projectName.get()
        val projectPathValue = projectPath.get()
        val variants = variantNames.get()
        val targets = targetNames.get()
        val active = activeVariantName.get()

        if (variants.isEmpty() || targets.isEmpty()) {
            logger.lifecycle(
                "[KMP Flavors] generateVariantRunConfigurations: no variants or no targets — " +
                    "no per-variant run configurations to emit.",
            )
            return
        }

        logger.lifecycle(
            "[KMP Flavors] generateVariantRunConfigurations: emitting ${variants.size * targets.size} " +
                "run configurations (${variants.size} variants × ${targets.size} targets)…",
        )

        var written = 0
        for (variant in variants) {
            for (target in targets) {
                val compileTaskName = compileTaskNameFor(variant, target, isActive = variant == active)
                val displayName = "$projectNameValue [$variant • $target]"
                val configFile = File(outputDir, "${projectNameValue}_${variant}_$target.run.xml")
                configFile.writeText(
                    renderRunXml(
                        name = displayName,
                        projectPath = projectPathValue,
                        compileTaskName = compileTaskName,
                        variantName = variant,
                    ),
                )
                logger.lifecycle("  Created: ${configFile.name}")
                written += 1
            }
        }

        logger.lifecycle("")
        logger.lifecycle("[KMP Flavors] Wrote $written run configurations to ${outputDir.absolutePath}")
        logger.lifecycle("Restart your IDE or sync the project to pick them up.")
    }

    private fun compileTaskNameFor(variantName: String, targetName: String, isActive: Boolean): String {
        val targetCap = targetName.replaceFirstChar { it.uppercase() }
        return if (isActive) {
            // Active variant compiles through the standard task.
            "compileKotlin$targetCap"
        } else {
            val variantCap = variantName.replaceFirstChar { it.uppercase() }
            "compile${variantCap}Kotlin$targetCap"
        }
    }

    private fun renderRunXml(name: String, projectPath: String, compileTaskName: String, variantName: String): String {
        val fullTaskName = if (projectPath == ":") ":$compileTaskName" else "$projectPath:$compileTaskName"
        val scriptParameters = "$fullTaskName -PkmpFlavor=$variantName"

        return """
            |<component name="ProjectRunConfigurationManager">
            |  <configuration default="false" name="$name" type="GradleRunConfiguration" factoryName="Gradle">
            |    <ExternalSystemSettings>
            |      <option name="executionName" />
            |      <option name="externalProjectPath" value="${'$'}PROJECT_DIR${'$'}" />
            |      <option name="externalSystemIdString" value="GRADLE" />
            |      <option name="scriptParameters" value="$scriptParameters" />
            |      <option name="taskDescriptions">
            |        <list />
            |      </option>
            |      <option name="taskNames">
            |        <list>
            |          <option value="$fullTaskName" />
            |        </list>
            |      </option>
            |      <option name="vmOptions" />
            |    </ExternalSystemSettings>
            |    <ExternalSystemDebugServerProcess>true</ExternalSystemDebugServerProcess>
            |    <ExternalSystemReattachDebugProcess>true</ExternalSystemReattachDebugProcess>
            |    <DebugAllEnabled>false</DebugAllEnabled>
            |    <RunAsTest>false</RunAsTest>
            |    <method v="2" />
            |  </configuration>
            |</component>
        """.trimMargin()
    }
}
