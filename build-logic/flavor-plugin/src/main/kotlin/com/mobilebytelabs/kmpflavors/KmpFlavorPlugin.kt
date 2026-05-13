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

import com.mobilebytelabs.kmpflavors.internal.AgpBridge
import com.mobilebytelabs.kmpflavors.internal.CompilationRegistrar
import com.mobilebytelabs.kmpflavors.internal.DependencyConfigurator
import com.mobilebytelabs.kmpflavors.internal.FlavorVariantResolver
import com.mobilebytelabs.kmpflavors.internal.KmpFlavorPluginValidator
import com.mobilebytelabs.kmpflavors.internal.KmpFlavorValidationSeverity
import com.mobilebytelabs.kmpflavors.internal.MatrixModeResolver
import com.mobilebytelabs.kmpflavors.internal.PlatformDetector
import com.mobilebytelabs.kmpflavors.internal.PlatformPropertiesConfigurator
import com.mobilebytelabs.kmpflavors.internal.SourceSetConfigurator
import com.mobilebytelabs.kmpflavors.tasks.GenerateBuildConfigTask
import com.mobilebytelabs.kmpflavors.tasks.GenerateRunConfigurationsTask
import com.mobilebytelabs.kmpflavors.tasks.GenerateSpmManifestTask
import com.mobilebytelabs.kmpflavors.tasks.InitFlavorSourceSetsTask
import com.mobilebytelabs.kmpflavors.tasks.ListFlavorsTask
import com.mobilebytelabs.kmpflavors.tasks.PrintFlavorPropertiesTask
import com.mobilebytelabs.kmpflavors.tasks.ValidateFlavorsTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * KMP Product Flavors Gradle Plugin.
 *
 * This plugin brings Android-style product flavor support to Kotlin Multiplatform projects.
 * It enables conditional compilation, source set management, and build config generation
 * based on the active flavor variant.
 *
 * ## Usage
 *
 * ```kotlin
 * plugins {
 *     kotlin("multiplatform")
 *     id("io.github.mobilebytelabs.kmp-product-flavors")
 * }
 *
 * kmpFlavors {
 *     generateBuildConfig.set(true)
 *     buildConfigPackage.set("com.example.app")
 *
 *     flavorDimensions {
 *         register("tier") { priority.set(0) }
 *         register("environment") { priority.set(1) }
 *     }
 *
 *     flavors {
 *         register("free") {
 *             dimension.set("tier")
 *             isDefault.set(true)
 *             buildConfigField("Boolean", "IS_PREMIUM", "false")
 *         }
 *         register("paid") {
 *             dimension.set("tier")
 *             buildConfigField("Boolean", "IS_PREMIUM", "true")
 *         }
 *         register("dev") {
 *             dimension.set("environment")
 *             isDefault.set(true)
 *             buildConfigField("String", "BASE_URL", "\"https://dev-api.example.com\"")
 *         }
 *         register("prod") {
 *             dimension.set("environment")
 *             buildConfigField("String", "BASE_URL", "\"https://api.example.com\"")
 *         }
 *     }
 * }
 * ```
 *
 * ## Gradle Properties
 *
 * - `kmpFlavor`: Override the active flavor variant (e.g., `-PkmpFlavor=paidProd`)
 *
 * ## Tasks
 *
 * - `generateFlavorBuildConfig`: Generates the BuildConfig Kotlin object
 * - `validateFlavors`: Validates the flavor configuration
 * - `listFlavors`: Lists all available flavor variants
 */
class KmpFlavorPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        // Register the extension
        val extension = project.extensions.create(
            "kmpFlavors",
            KmpFlavorExtension::class.java,
        )

        // Defer configuration until after project evaluation
        project.afterEvaluate {
            configurePlugin(project, extension)
        }
    }

    private fun configurePlugin(project: Project, extension: KmpFlavorExtension) {
        val logger = project.logger

        // Find KMP extension
        val kotlin = project.extensions.findByType(KotlinMultiplatformExtension::class.java)
        if (kotlin == null) {
            logger.warn("[KMP Flavors] Kotlin Multiplatform plugin not found. Skipping flavor configuration.")
            return
        }

        val flavors = extension.flavors.toList()
        val dimensions = extension.flavorDimensions.toList()

        // Skip if no flavors configured (v1.x behaviour preserved unless matrix
        // mode is explicitly opted in, in which case KMPF-V08 must fire).
        if (flavors.isEmpty()) {
            if (MatrixModeResolver.isEnabled(project, extension)) {
                throw GradleException(
                    "kmpFlavors plugin configuration is invalid (1 error(s)):\n\n" +
                        "  ${KmpFlavorPluginValidator.CODE_MATRIX_MODE_WITHOUT_FLAVORS}: " +
                        "kmpFlavors.buildMatrix is enabled but no flavors are registered. " +
                        "Matrix mode requires at least one flavor to generate compilations from.\n" +
                        "  Fix: Either register flavors via " +
                        "`kmpFlavors { flavors { register(\"…\") } }` in the convention " +
                        "plugin, or remove the `buildMatrix.set(true)` / `gradle.properties: " +
                        "kmpFlavors.buildMatrix=true` opt-in.\n\n" +
                        "See docs/ERROR_CODES.md for the full catalog.",
                )
            }
            logger.info("[KMP Flavors] No flavors configured. Skipping.")
            return
        }

        logger.lifecycle("[KMP Flavors] Configuring ${flavors.size} flavors across ${dimensions.size} dimensions")

        val buildTypesList = extension.buildTypes.toList()
        val enableBuildTypesFlag = extension.enableBuildTypes.get()

        // Resolve all variants (with filtering). When enableBuildTypes is true and
        // at least one buildType is declared, the matrix expands by buildType axis.
        val allVariants = FlavorVariantResolver.resolveAllVariants(
            dimensions = dimensions,
            flavors = flavors,
            variantFilters = extension.variantFilterActions,
            buildTypes = buildTypesList,
            enableBuildTypes = enableBuildTypesFlag,
        )
        if (allVariants.isEmpty()) {
            logger.warn("[KMP Flavors] No variants resolved. Check dimension assignments or variant filters.")
            return
        }

        // Log filtered variants if any were excluded
        val baseTotal = if (dimensions.isEmpty()) {
            flavors.size
        } else {
            dimensions.fold(1) { acc, dim ->
                acc * flavors.count { it.dimension.orNull == dim.name }
            }
        }
        val totalPossible = if (enableBuildTypesFlag && buildTypesList.isNotEmpty()) baseTotal * buildTypesList.size else baseTotal
        if (allVariants.size < totalPossible) {
            logger.lifecycle("[KMP Flavors] Variant filter excluded ${totalPossible - allVariants.size} variants")
        }

        // Determine active variant
        val activeVariant = resolveActiveVariant(project, extension, dimensions, flavors, allVariants, buildTypesList, enableBuildTypesFlag)
        logger.lifecycle("[KMP Flavors] Active variant: ${activeVariant.name}")

        // Detect platforms
        val platforms = PlatformDetector.detect(kotlin, logger)
        val createIntermediates = extension.createIntermediateSourceSets.get()

        // v2.0 fail-fast validation (RFC §3 Q23). Run before the matrix-mode
        // registrar so structured errors are surfaced with stable KMPF-Vxx codes
        // instead of opaque downstream stack traces.
        val matrixModeEnabled = MatrixModeResolver.isEnabled(project, extension)
        val nonAndroidTargets = kotlin.targets.filter {
            it.name != "android" && it.name != "metadata"
        }
        val validationFindings = KmpFlavorPluginValidator.validate(
            flavors = flavors,
            buildTypes = buildTypesList,
            resolvedVariants = allVariants,
            matrixModeEnabled = matrixModeEnabled,
            detectedTargetCount = nonAndroidTargets.size,
        )
        val errors = validationFindings.filter { it.severity == KmpFlavorValidationSeverity.ERROR }
        val warnings = validationFindings.filter { it.severity == KmpFlavorValidationSeverity.WARNING }
        warnings.forEach { warning ->
            logger.warn("[KMP Flavors] ${warning.code} — ${warning.message} Fix: ${warning.fix}")
        }
        if (errors.isNotEmpty()) {
            val formatted = errors.joinToString("\n\n") { error ->
                "  ${error.code}: ${error.message}\n  Fix: ${error.fix}"
            }
            throw GradleException(
                "kmpFlavors plugin configuration is invalid (${errors.size} error(s)):\n\n" +
                    "$formatted\n\n" +
                    "See docs/ERROR_CODES.md for the full catalog.",
            )
        }


        // Wire intermediate source sets if needed
        if (createIntermediates) {
            PlatformDetector.wireIntermediateSourceSets(kotlin, platforms)
        }

        // Resolve platform source sets
        val platformSourceSets = PlatformDetector.resolveSourceSets(kotlin, platforms)

        // Configure flavor source sets (v1.x active-variant model). Runs in
        // BOTH v1.x and v2.0 matrix mode: the active variant always compiles
        // through the standard `main` compilation. Matrix mode adds
        // compilations for the INACTIVE variants on top — the active
        // variant doesn't get a duplicate `compile{Active}Kotlin{Target}`
        // task because that would collide with v1.x's source-set wiring.
        val sourceSetConfigurator = SourceSetConfigurator(logger)
        sourceSetConfigurator.configure(
            project = project,
            kotlin = kotlin,
            activeVariant = activeVariant,
            allFlavors = flavors,
            platforms = platforms,
            platformSourceSets = platformSourceSets,
            createIntermediates = createIntermediates,
        )

        // v2.0 matrix mode (RFC §3 Q1-Q4): register one KotlinCompilation per
        // (variant × target) across every non-Android target. Source-set wiring,
        // per-variant BuildConfig, and per-variant dependencies land in W2 of the
        // v2.0 impl plan. This W1 hook only stamps the compilations so the task
        // graph surfaces compileFreeDevKotlinDesktop etc. for downstream wiring.
        //
        // RFC §1 non-goal: "Change Android target behaviour (AGP already handles
        // matrix; we don't touch it)." → skip target name "android".
        //
        // The synthetic "metadata" target rejects custom compilations
        // ("Can't create custom metadata compilations by name") — skip it too.
        // Both exclusions are name-based following PlatformDetector's convention.
        if (matrixModeEnabled) {
            // Matrix mode adds compilations for INACTIVE variants only — the
            // active variant continues to compile through the standard `main`
            // compilation wired by v1.x SourceSetConfigurator above. This
            // preserves v1.x semantics for the active variant and avoids the
            // `compile{Active}Kotlin{Target}` naming collision with the
            // existing source-set wiring (KGP rejects sources being in two
            // compilations and dependsOn into a default source set).
            val activeVariantName = activeVariant.name
            val variantNames = allVariants
                .map { it.name }
                .filter { it != activeVariantName }
            // Index variants by name so the per-variant lookup is O(1).
            val variantByName = allVariants.associateBy { it.name }
            // RFC §3 Q2-C hybrid: dependsOn into per-flavor source sets that
            // v1.x SourceSetConfigurator created (commonFree, commonPaid, etc.),
            // plus optional variant-specific srcDir for code unique to one
            // variant. The per-flavor source sets already dependsOn(commonMain),
            // so the variant's defaultSourceSet transitively sees commonMain via
            // the KotlinSourceSet hierarchy — `expect` in commonMain and
            // `actual` in commonFlavor live in SEPARATE compilation modules,
            // which is exactly what KMP's expect/actual semantics require.
            // v1.x SourceSetConfigurator only wires `commonFlavor.dependsOn(commonMain)`
            // for the active flavor (see SourceSetConfigurator.kt line 81). For inactive
            // flavors in matrix mode, we wire that edge ourselves so the variant
            // compilation can see commonMain's `expect` declarations through the
            // commonFlavor -> commonMain dependsOn chain.
            val commonMainSourceSet = kotlin.sourceSets.getByName("commonMain")
            val parentSourceSetsFor: (String) -> List<org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet> = { name ->
                val variant = variantByName[name]
                if (variant == null) {
                    emptyList()
                } else {
                    variant.flavors.mapNotNull { flavor ->
                        val ssName = "common${flavor.name.replaceFirstChar { it.uppercase() }}"
                        kotlin.sourceSets.findByName(ssName)?.also { commonFlavor ->
                            commonFlavor.dependsOn(commonMainSourceSet)
                        }
                    }
                }
            }
            // Variant-specific srcDir (e.g., `src/freeDev/kotlin`) — only used
            // for code that lives in a single variant and isn't expressible
            // as a per-flavor common source set. Most projects won't need this.
            val variantSpecificSrcDirsFor: (String) -> List<String> = { name ->
                listOf("src/${name}/kotlin")
            }
            nonAndroidTargets.forEach { target ->
                CompilationRegistrar.register(
                    target = target,
                    variantNames = variantNames,
                    parentSourceSetsFor = parentSourceSetsFor,
                    variantSpecificSrcDirsFor = variantSpecificSrcDirsFor,
                    logger = logger,
                )
            }
            logger.lifecycle(
                "[KMP Flavors] Matrix mode: registered ${variantNames.size} inactive-variant " +
                    "compilations across ${nonAndroidTargets.size} non-Android target(s) " +
                    "(active variant '$activeVariantName' continues to compile through `main`)",
            )
        }


        // Configure dependencies
        val dependencyConfigurator = DependencyConfigurator(logger)
        dependencyConfigurator.configure(project, activeVariant)

        // AGP bridge — propagate KMP flavors / build types into the Android
        // Gradle Plugin extension when consumer opts in via bridgeAgp* flags.
        // v1.1.0 scope: com.android.application only.
        AgpBridge.apply(
            project = project,
            bridgeProductFlavors = extension.bridgeAgpProductFlavors.get(),
            bridgeBuildTypes = extension.bridgeAgpBuildTypes.get(),
            kmpDimensions = dimensions,
            kmpFlavors = flavors,
            kmpBuildTypes = extension.buildTypes.toList(),
            logger = logger,
        )

        // SPM manifest generator — opt-in via spm { generateManifest.set(true) }
        if (extension.spm.generateManifest.get()) {
            registerSpmTask(project, extension, activeVariant)
        }

        // Configure platform-specific properties
        val platformPropertiesConfigurator = PlatformPropertiesConfigurator(logger)
        platformPropertiesConfigurator.configure(project, activeVariant)

        // Register tasks
        registerTasks(project, extension, allVariants, activeVariant, flavors, dimensions, platforms)

        // Wire build config generation to compilation if enabled.
        // Multi-module-safe: in a build with multiple subprojects applying this
        // plugin under the same buildConfigPackage+ClassName, only the FIRST
        // subproject to apply (in Gradle's configuration order) generates the
        // class. Subsequent applications skip silently to prevent DEX-merge
        // duplicate-class collisions. See shouldGenerateCodegen() below.
        if (shouldGenerateCodegen(project, extension)) {
            wireGenerateBuildConfigToCompilation(project, kotlin)
        }
    }

    /**
     * Decide whether this project should generate FlavorConfig codegen.
     *
     * In a multi-module Gradle build where many subprojects apply this plugin
     * with the same `buildConfigPackage` + `buildConfigClassName`, every
     * subproject generating the class would produce duplicate class files
     * that collide at DEX merge time (Android) or at compile time (KMP).
     *
     * The first subproject to apply the plugin under a given
     * "package.ClassName" key claims codegen via a rootProject extra
     * property. Subsequent subprojects observe the existing claim and
     * silently skip codegen — they still get source-set wiring, variant
     * registration, and AGP bridging from this plugin; they just won't
     * emit a competing FlavorConfig.
     *
     * Consumers that want a specific module to be the codegen host can
     * force it to be the first applier (e.g. by adjusting include order
     * in settings.gradle.kts or by applying the plugin to that module
     * before any other).
     */
    private fun shouldGenerateCodegen(project: Project, extension: KmpFlavorExtension): Boolean {
        if (!extension.generateBuildConfig.get()) return false

        // Explicit per-module override via kmpFlavors { codegenHost.set(false) }.
        // Module declares it will NOT generate codegen even if it would have
        // claimed under the auto mechanism below.
        val explicitHost = extension.codegenHost.orNull
        if (explicitHost == false) {
            project.logger.info(
                "[KMP Flavors] ${project.path} skipping codegen — codegenHost = false",
            )
            return false
        }

        val pkg = extension.buildConfigPackage.orNull
        if (pkg.isNullOrBlank()) return true // No conflict possible without a package.
        val cls = extension.buildConfigClassName.orNull ?: "FlavorConfig"
        val key = "kmpFlavors.codegenClaim:$pkg.$cls"
        val rootExtras = project.rootProject.extensions.extraProperties
        val existing = if (rootExtras.has(key)) rootExtras.get(key) as? String else null

        // Explicit host always wins, even over an earlier non-explicit claim:
        // override the claim if codegenHost.set(true).
        if (explicitHost == true) {
            if (existing != null && existing != project.path) {
                project.logger.info(
                    "[KMP Flavors] ${project.path} taking over codegen claim from $existing (codegenHost = true)",
                )
            }
            rootExtras.set(key, project.path)
            return true
        }

        if (existing == null) {
            rootExtras.set(key, project.path)
            project.logger.info(
                "[KMP Flavors] ${project.path} claimed FlavorConfig codegen for $pkg.$cls (auto)",
            )
            return true
        }
        if (existing == project.path) return true
        project.logger.lifecycle(
            "[KMP Flavors] ${project.path} skipping FlavorConfig codegen — already generated by $existing",
        )
        return false
    }

    private fun resolveActiveVariant(
        project: Project,
        extension: KmpFlavorExtension,
        dimensions: List<FlavorDimension>,
        flavors: List<FlavorConfig>,
        allVariants: List<FlavorVariant>,
        buildTypes: List<BuildTypeConfig>,
        enableBuildTypes: Boolean,
    ): FlavorVariant {
        // Priority: 1) Gradle property, 2) Extension property, 3) Default variant
        val gradleProperty = project.findProperty("kmpFlavor")?.toString()
        val extensionProperty = extension.activeFlavor.orNull

        val activeName = gradleProperty ?: extensionProperty

        return if (activeName != null) {
            FlavorVariantResolver.resolveVariantByName(activeName, allVariants)
                ?: throw GradleException(
                    "[KMP Flavors] Unknown variant '$activeName'. " +
                        "Available variants: ${allVariants.joinToString(", ") { it.name }}",
                )
        } else {
            FlavorVariantResolver.resolveDefaultVariant(dimensions, flavors, buildTypes, enableBuildTypes)
                ?: allVariants.first()
        }
    }

    private fun registerTasks(
        project: Project,
        extension: KmpFlavorExtension,
        allVariants: List<FlavorVariant>,
        activeVariant: FlavorVariant,
        flavors: List<FlavorConfig>,
        dimensions: List<FlavorDimension>,
        platforms: List<com.mobilebytelabs.kmpflavors.internal.PlatformGroup>,
    ) {
        // Generate BuildConfig task — gated by claim (see shouldGenerateCodegen).
        if (shouldGenerateCodegen(project, extension)) {
            project.tasks.register(
                "generateFlavorBuildConfig",
                GenerateBuildConfigTask::class.java,
            ) {
                packageName.set(extension.buildConfigPackage)
                className.set(extension.buildConfigClassName)
                variantName.set(activeVariant.name)
                allFlavorNames.set(flavors.map { it.name }.toSet())
                activeFlavorNames.set(activeVariant.flavorNames.toSet())
                allBuildTypeNames.set(extension.buildTypes.map { it.name }.toSet())
                activeBuildTypeName.set(activeVariant.buildType?.name ?: "")
                buildConfigFields.set(activeVariant.mergedBuildConfigFields)
                outputDirectory.set(
                    project.layout.buildDirectory.dir("generated/kmpFlavors/commonMain/kotlin"),
                )
            }
        }

        // Validate flavors task
        project.tasks.register(
            "validateFlavors",
            ValidateFlavorsTask::class.java,
        ) {
            dimensionNames.set(dimensions.map { it.name }.toSet())
            flavorDimensions.set(flavors.associate { it.name to (it.dimension.orNull ?: "") })
            flavorDefaults.set(flavors.associate { it.name to it.isDefault.getOrElse(false) })
            validVariantNames.set(allVariants.map { it.name }.toSet())
            activeVariantName.set(activeVariant.name)
            allFlavorNames.set(flavors.map { it.name })
        }

        // List flavors task
        val variantsData = allVariants.associate { it.name to it.flavorNames }
        val activeVariantNameValue = activeVariant.name
        val dimensionsData = dimensions.associate { it.name to it.priority.getOrElse(0) }
        val platformsData = platforms.filter { !it.isIntermediate }.map { it.prefix }

        project.tasks.register("listFlavors", ListFlavorsTask::class.java).configure {
            this.variants.set(variantsData)
            this.activeVariant.set(activeVariantNameValue)
            this.dimensions.set(dimensionsData)
            this.platforms.set(platformsData)
        }

        // Generate run configurations task
        project.tasks.register(
            "generateRunConfigurations",
            GenerateRunConfigurationsTask::class.java,
        ).configure {
            projectName.set(project.name)
            projectPath.set(project.path)
            variants.set(variantsData)
            this.activeVariant.set(activeVariantNameValue)
            outputDirectory.set(project.rootProject.layout.projectDirectory.dir(".run"))
        }

        // Init flavor source sets task
        val leafPlatformPrefixes = platforms.filter { !it.isIntermediate }.map { it.prefix }.toSet()
        val intermediatePrefixes = platforms.filter { it.isIntermediate }.map { it.prefix }.toSet()

        project.tasks.register(
            "kmpFlavorInit",
            InitFlavorSourceSetsTask::class.java,
        ).configure {
            flavorNames.set(flavors.map { it.name }.toSet())
            platformPrefixes.set(leafPlatformPrefixes)
            this.intermediatePrefixes.set(intermediatePrefixes)
            createIntermediates.set(extension.createIntermediateSourceSets)
            createGitKeep.set(true)
            createExampleFiles.set(false)
            createReadmePerSourceSet.set(true)
            examplePackage.set(extension.buildConfigPackage)
            sourceDirectory.set(project.layout.projectDirectory.dir("src"))
        }

        // Print flavor properties task
        project.tasks.register(
            "printFlavorProperties",
            PrintFlavorPropertiesTask::class.java,
        ).configure {
            variantName.set(activeVariant.name)
            applicationIdSuffix.set(
                activeVariant.combinedApplicationIdSuffix.ifEmpty { null },
            )
            bundleIdSuffix.set(
                activeVariant.combinedBundleIdSuffix.ifEmpty { null },
            )
            versionNameSuffix.set(
                activeVariant.combinedVersionNameSuffix.ifEmpty { null },
            )
            desktopTitleSuffix.set(
                activeVariant.combinedDesktopTitleSuffix.ifEmpty { null },
            )
            webTitleSuffix.set(
                activeVariant.combinedWebTitleSuffix.ifEmpty { null },
            )
        }
    }

    private fun registerSpmTask(project: org.gradle.api.Project, extension: KmpFlavorExtension, activeVariant: FlavorVariant) {
        // Resolve the active flavor's name. activeVariant.flavors holds the list of
        // FlavorConfig objects forming this variant; we use the first dimension's flavor
        // for {flavor} interpolation, matching the convention applicationIdSuffix uses.
        val flavorName = activeVariant.flavors.firstOrNull()?.name ?: activeVariant.name

        project.tasks.register(
            "generateSpmManifest",
            GenerateSpmManifestTask::class.java,
        ).configure {
            group = "kmp flavors"
            description = "Generate Package.swift manifest for SPM distribution of the active flavor variant"
            variantName.set(activeVariant.name)
            this.flavorName.set(flavorName)
            xcframeworkName.set(extension.spm.xcframeworkName)
            distribution.set(extension.spm.distribution)
            binaryUrlTemplate.set(extension.spm.binaryUrlTemplate)
            xcframeworkPath.set(extension.spm.xcframeworkPath)
            projectVersion.set(project.provider { project.version.toString() })
            checksumStrategy.set(extension.spm.checksumStrategy)
            outputDirectory.set(project.layout.buildDirectory.dir("spm/${activeVariant.name}"))
        }

        // Hook into :assemble so generation happens automatically alongside builds.
        project.tasks.matching { it.name == "assemble" }.configureEach {
            dependsOn("generateSpmManifest")
        }
    }

    private fun wireGenerateBuildConfigToCompilation(project: Project, kotlin: KotlinMultiplatformExtension) {
        val generateTask = project.tasks.named(
            "generateFlavorBuildConfig",
            GenerateBuildConfigTask::class.java,
        )

        // Add generated source directory to commonMain
        kotlin.sourceSets.getByName("commonMain").kotlin.srcDir(
            generateTask.flatMap { it.outputDirectory },
        )

        // Make Kotlin compilation depend on generation
        project.tasks.matching { it.name.startsWith("compileKotlin") }.configureEach {
            dependsOn(generateTask)
        }
    }
}
