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
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * Generates IntelliJ IDEA / Android Studio run configurations for each build variant.
 *
 * Creates `.run/` directory with XML configuration files that appear in the IDE's
 * run configuration dropdown, allowing users to easily switch between flavors.
 */
abstract class GenerateRunConfigurationsTask : DefaultTask() {

    @get:Input
    abstract val projectName: Property<String>

    @get:Input
    abstract val projectPath: Property<String>

    @get:Input
    abstract val variants: MapProperty<String, List<String>>

    @get:Input
    abstract val activeVariant: Property<String>

    @get:Input
    abstract val gradleTasks: ListProperty<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    init {
        group = "kmp flavors"
        description = "Generates IDE run configurations for each build variant"
        gradleTasks.convention(listOf("assemble"))
    }

    @TaskAction
    fun generate() {
        val outputDir = outputDirectory.get().asFile
        outputDir.mkdirs()

        val projectNameValue = projectName.get()
        val projectPathValue = projectPath.get()
        val variantsMap = variants.get()
        val tasks = gradleTasks.get()

        logger.lifecycle("Generating run configurations for ${variantsMap.size} variants...")

        // Generate a run configuration for each variant
        variantsMap.forEach { (variantName, flavorNames) ->
            val configFile = File(outputDir, "${projectNameValue}_${variantName}.run.xml")
            val configContent = generateRunConfiguration(
                name = "$projectNameValue [$variantName]",
                projectPath = projectPathValue,
                variantName = variantName,
                tasks = tasks,
            )
            configFile.writeText(configContent)
            logger.lifecycle("  Created: ${configFile.name}")
        }

        // Generate a "List Flavors" configuration
        val listFlavorsConfig = File(outputDir, "${projectNameValue}_listFlavors.run.xml")
        listFlavorsConfig.writeText(
            generateRunConfiguration(
                name = "$projectNameValue [List Flavors]",
                projectPath = projectPathValue,
                variantName = null,
                tasks = listOf("listFlavors"),
            ),
        )
        logger.lifecycle("  Created: ${listFlavorsConfig.name}")

        logger.lifecycle("")
        logger.lifecycle("Run configurations created in: ${outputDir.absolutePath}")
        logger.lifecycle("Restart your IDE or sync the project to see them in the run dropdown.")
    }

    private fun generateRunConfiguration(
        name: String,
        projectPath: String,
        variantName: String?,
        tasks: List<String>,
    ): String {
        val taskNames = tasks.joinToString(" ") { "$projectPath:$it" }
        val scriptParameters = if (variantName != null) {
            "$taskNames -PkmpFlavor=$variantName"
        } else {
            taskNames
        }

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
            |${tasks.joinToString("\n") { "          <option value=\"$projectPath:$it\" />" }}
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
