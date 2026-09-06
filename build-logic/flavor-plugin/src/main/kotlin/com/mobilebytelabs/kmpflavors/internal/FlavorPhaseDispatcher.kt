/*
 * Copyright 2026 MobileByteLabs
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.mobilebytelabs.kmpflavors.internal

import com.mobilebytelabs.kmpflavors.KmpFlavorExtension
import com.mobilebytelabs.kmpflavors.tasks.ExportKmpFlavorsManifestTask
import com.mobilebytelabs.kmpflavors.tasks.GenerateIosFlavorXcconfigsTask
import com.mobilebytelabs.kmpflavors.tasks.KmpFlavorsBootstrapXcodeTask
import com.mobilebytelabs.kmpflavors.tasks.KmpFlavorsDoctorTask
import com.mobilebytelabs.kmpflavors.tasks.KmpFlavorsMigrateFromV27Task
import org.gradle.api.Project
import org.gradle.api.logging.Logger
import org.gradle.kotlin.dsl.register
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation

/**
 * Phase dispatcher — composes per-platform integrators based on which
 * platform plugins / KMP targets are applied.
 *
 *   phaseAgp     — AGP productFlavors auto-wiring + Android Firebase router (gated on AGP)
 *   phaseKmp     — KMP source set fan-out + KmpFlavorsRuntime codegen + Compose Resources + Android res (gated on KMP)
 *   phaseIos     — per-variant xcconfig + umbrella integrate + pbxproj bootstrap + Firebase iOS router (gated on KMP + apple target)
 *   phaseDesktop — Compose Desktop nativeDistributions wiring + JAR Manifest entries (gated on KMP + compose plugin)
 *   phaseWeb     — Webpack overlay + DefinePlugin constants (gated on KMP + js/wasm target)
 *
 * Doctor task registers unconditionally.
 */
internal object FlavorPhaseDispatcher {

    /**
     * Wires phase capabilities. Returns the count of integrators that fired.
     */
    fun apply(project: Project, ext: KmpFlavorExtension): Int {
        val logger = project.logger
        var fired = 0

        // Tooling tasks register unconditionally
        registerToolingTasks(project)

        val hasAgp = project.plugins.hasPlugin("com.android.application") ||
            project.plugins.hasPlugin("com.android.library")
        val kmp = project.extensions.findByType(KotlinMultiplatformExtension::class.java)
        val hasKmp = kmp != null
        val hasCompose = project.pluginManager.hasPlugin("org.jetbrains.compose")

        if (!hasAgp && !hasKmp) {
            logger.info("[KMP Flavors] no AGP or KMP plugin; skipping phase dispatch")
            return 0
        }

        // phaseAgp — Android Firebase + per-flavor res routing.
        // (AGP productFlavors registration runs earlier, from KmpFlavorPlugin.apply()
        // via pluginManager.withPlugin so it lands ahead of AGP's finalizeDsl phase.)
        if (hasAgp) {
            fired += AndroidFirebaseFlavorRouter.apply(project, ext, logger)
            fired += AndroidResPerFlavorRouter.apply(project, ext, logger)
        }

        // phaseKmp — source set wiring + RuntimeApiGenerator
        if (hasKmp) {
            fired += KmpFlavorSourceSetWiring.apply(project, ext, logger)
            fired += generateRuntimeApi(project, ext, kmp!!, logger)
            if (hasCompose) {
                fired += ComposeResourcesPerFlavorRouter.apply(project, ext, logger)
            }
        }

        // phaseIos — per-variant xcconfig generation + pbxproj bootstrap + manifest + iOS firebase
        if (hasKmp && hasAnyAppleTarget(kmp!!)) {
            fired += registerIosTasks(project, ext, logger)
            fired += IosFirebaseFlavorRouter.apply(project, ext, logger)
        }

        // phaseDesktop
        if (hasKmp && hasCompose) {
            fired += DesktopFlavorIntegrator.apply(project, ext, logger)
        }

        // phaseWeb
        if (hasKmp && hasJsOrWasmTarget(kmp!!)) {
            fired += WebFlavorIntegrator.apply(project, ext, logger)
        }

        logger.lifecycle("[KMP Flavors] dispatched: $fired integrator actions")
        return fired
    }

    private fun registerToolingTasks(project: Project) {
        if (project.tasks.findByName("kmpFlavorsDoctor") == null) {
            project.tasks.register("kmpFlavorsDoctor", KmpFlavorsDoctorTask::class.java)
        }
        if (project.tasks.findByName("kmpFlavorsMigrateFromV27") == null) {
            project.tasks.register("kmpFlavorsMigrateFromV27", KmpFlavorsMigrateFromV27Task::class.java)
        }
    }

    /**
     * Registers the opt-in iOS tasks — `generateIosFlavorXcconfigs` (self-contained per-variant
     * xcconfigs) + `kmpFlavorsBootstrapXcode` (per-config pbxproj base-config wiring) when
     * [KmpFlavorExtension.iosXcconfigGeneration] is true, and `exportKmpFlavorsManifest`
     * (variants.json) when [KmpFlavorExtension.iosManifestExport] is true. All DSL reads use
     * lazy providers so `LocalFlavors.kt` additions are picked up at execution time.
     */
    private fun registerIosTasks(project: Project, ext: KmpFlavorExtension, logger: Logger): Int {
        val wantXcconfig = ext.iosXcconfigGeneration.getOrElse(false)
        val wantManifest = ext.iosManifestExport.getOrElse(false)
        if (!wantXcconfig && !wantManifest) {
            logger.info("[KMP Flavors] iOS xcconfig generation + manifest export disabled; skipping iOS task registration")
            return 0
        }
        var fired = 0

        if (wantManifest && project.tasks.findByName("exportKmpFlavorsManifest") == null) {
            project.tasks.register<ExportKmpFlavorsManifestTask>("exportKmpFlavorsManifest") {
                flavorAppIdSuffixes.set(project.provider { ext.flavors.associate { it.name to (it.applicationIdSuffix.orNull ?: "") } })
                flavorBundleIdSuffixes.set(project.provider { ext.flavors.associate { it.name to (it.bundleIdSuffix.orNull ?: "") } })
                buildTypeAppIdSuffixes.set(project.provider { ext.buildTypes.associate { it.name to (it.applicationIdSuffix.orNull ?: "") } })
                outputFile.set(project.layout.buildDirectory.file("kmp-flavors/variants.json"))
            }
            fired++
        }

        if (wantXcconfig) {
            val configsDirRel = ext.iosConfigsDir.getOrElse("../cmp-ios/Configs")
            val pbxprojRel = ext.iosPbxprojPath.getOrElse("../cmp-ios/iosApp.xcodeproj/project.pbxproj")
            val groupName = configsDirRel.substringAfterLast('/')
            val iosDirRel = configsDirRel.substringBeforeLast('/')
            val podsTarget = "iosApp"

            if (project.tasks.findByName("generateIosFlavorXcconfigs") == null) {
                project.tasks.register<GenerateIosFlavorXcconfigsTask>("generateIosFlavorXcconfigs") {
                    flavorBundleSuffixes.set(project.provider { ext.flavors.associate { it.name to (it.bundleIdSuffix.orNull ?: "") } })
                    buildTypeBundleSuffixes.set(project.provider { ext.buildTypes.associate { it.name to (it.bundleIdSuffix.orNull ?: "") } })
                    bundleIdBaseExpr.set(ext.iosBundleIdBaseExpr.orElse(""))
                    appId.set(ext.appId.orElse(""))
                    developmentTeamExpr.set(ext.iosDevelopmentTeamExpr.orElse(""))
                    identityInclude.set(ext.iosIdentityInclude.orElse(""))
                    cocoapodsIntegration.set(ext.iosIncludePodsXcconfig.orElse(false))
                    podsTargetName.set(podsTarget)
                    outputDir.set(project.layout.projectDirectory.dir(configsDirRel))
                }
            }
            fired++

            // Keep xcconfigs fresh before Xcode links the KMP framework.
            val generateProvider = project.tasks.named("generateIosFlavorXcconfigs")
            project.tasks.configureEach {
                if (name == "embedAndSignAppleFrameworkForXcode" ||
                    (name.startsWith("link") && name.contains("Framework") && name.contains("Ios"))
                ) {
                    dependsOn(generateProvider)
                }
            }

            // Bootstrap is NOT auto-hooked — a consumer runs it once to wire their pbxproj.
            if (project.tasks.findByName("kmpFlavorsBootstrapXcode") == null) {
                project.tasks.register<KmpFlavorsBootstrapXcodeTask>("kmpFlavorsBootstrapXcode") {
                    variantNames.set(
                        project.provider {
                            ext.flavors.flatMap { f ->
                                ext.buildTypes.map { bt -> f.name + bt.name.replaceFirstChar { it.uppercase() } }
                            }.toSet()
                        },
                    )
                    configsGroupName.set(groupName)
                    projectName.set(project.name)
                    pbxprojFile.set(project.layout.projectDirectory.file(pbxprojRel))
                    infoPlistFile.set(project.layout.projectDirectory.file("$iosDirRel/$podsTarget/Info.plist"))
                }
            }
            fired++
        }

        logger.info("[KMP Flavors] registered iOS tasks (xcconfig=$wantXcconfig, manifest=$wantManifest)")
        return fired
    }

    private fun generateRuntimeApi(project: Project, ext: KmpFlavorExtension, kmp: KotlinMultiplatformExtension, logger: Logger): Int {
        val packageName =
            (project.findProperty("kmpFlavorsRuntimePackage") as? String)
                ?: ext.flavors.firstOrNull()?.buildConfigFields?.orNull?.get("PACKAGE")?.value?.trim('"')
                ?: "${project.rootProject.name.lowercase().replace("-", ".")}.kmpflavors"

        // Cross-module election: exactly one module in the build emits KmpFlavorsRuntime
        // for a given package. Subsequent modules find the claim via rootProject extras
        // and skip codegen — they pick up the type via the producer module's classpath.
        // Mirrors the FlavorConfig election in KmpFlavorPlugin.shouldGenerateCodegen.
        val claimKey = "kmpFlavors.runtimeApiClaim:$packageName"
        val rootExtras = project.rootProject.extensions.extraProperties
        val existing =
            if (rootExtras.has(claimKey)) rootExtras.get(claimKey) as? String else null
        if (existing != null && existing != project.path) {
            // Deterministic election: lex-lowest project.path wins. Re-elect if our
            // path is earlier (same rule as the FlavorConfig election so the same
            // module wins both claims).
            if (project.path < existing) {
                rootExtras.set(claimKey, project.path)
                logger.info(
                    "[KMP Flavors] ${project.path} taking over RuntimeApi codegen from $existing",
                )
            } else {
                logger.info(
                    "[KMP Flavors] ${project.path} skipping RuntimeApi codegen — already by $existing",
                )
                return 0
            }
        } else if (existing == null) {
            rootExtras.set(claimKey, project.path)
        }

        val outDir = project.layout.buildDirectory
            .dir("generated/kmpflavors-runtime").get().asFile

        // Resolve the active variant's values for the concrete commonMain object.
        val activeFlavor = ext.flavors.firstOrNull { it.isDefault.orNull == true }
            ?: ext.flavors.firstOrNull()
        val activeBuildType = ext.buildTypes.firstOrNull { it.isDefault.orNull == true }
            ?: ext.buildTypes.firstOrNull()
        // Identity is consumer-declared via the DSL (ext.appId / ext.appDisplayName) or a
        // gradle property — NEVER guessed from the module/root-project name, which would
        // bake a misleading value (the runtime object is elected into an arbitrary module).
        // Unset → empty string (honest "not provided") rather than wrong.
        val baseBundleId = ext.appId.orNull
            ?: (project.findProperty("applicationId") as? String)
            ?: ""
        val runtimeAppDisplayName = ext.appDisplayName.orNull
            ?: (project.findProperty("appDisplayName") as? String)
            ?: ""
        val runtimeAppVersion = project.version.toString()
        val hint =
            if (activeFlavor != null && activeBuildType != null) {
                RuntimeVariantHint.from(
                    activeFlavor,
                    activeBuildType,
                    baseBundleId,
                    runtimeAppDisplayName,
                    runtimeAppVersion,
                )
            } else {
                RuntimeVariantHint(
                    flavorName = "",
                    buildTypeName = "",
                    bundleId = baseBundleId,
                    appDisplayName = runtimeAppDisplayName,
                    appVersion = runtimeAppVersion,
                    isDemo = false,
                    isDebug = false,
                )
            }

        val spec = RuntimeApiSpec(packageName = packageName)
        val files = RuntimeApiGenerator.generate(spec, hint, outDir)

        // Wire ONLY the generated commonMain source dir. The runtime is now a single
        // concrete `object` in commonMain (no expect/actual) so it compiles on every
        // platform AND every per-variant compilation via normal commonMain visibility —
        // no platform-actual replay, no per-variant second wiring pass required.
        val commonSrc = outDir.resolve("commonMain")
        if (commonSrc.exists()) {
            kmp.sourceSets.findByName("commonMain")?.kotlin?.srcDir(commonSrc)
        }
        logger.lifecycle(
            "[KMP Flavors] ${project.path} generated ${files.size} runtime API file(s) (package=$packageName)",
        )
        return files.size
    }

    private fun hasAnyAppleTarget(kmp: KotlinMultiplatformExtension): Boolean = kmp.targets.any { it.platformType.name.lowercase() == "native" && it.name.startsWith("ios") }

    private fun hasJsOrWasmTarget(kmp: KotlinMultiplatformExtension): Boolean = kmp.targets.any {
        it.platformType.name.lowercase().let { p -> p == "js" || p == "wasm" }
    }
}
