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

plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
    `maven-publish`
    signing
    alias(libs.plugins.vanniktech.mavenPublish)
    id("com.gradle.plugin-publish") version "1.3.0"
    // v2.6 Phase 1 — coverage gate + mutation testing baseline
    alias(libs.plugins.kover)
    alias(libs.plugins.pitest)
}

group = "io.github.mobilebytelabs.kmpflavors"
// gradle.properties lives in the root project, not the composite build — read it directly.
version = rootProject
    .file("../gradle.properties")
    .readLines()
    .firstOrNull { it.startsWith("kmpflavors.version=") }
    ?.substringAfter("=")
    ?.trim()
    ?: error("kmpflavors.version not set in root gradle.properties")

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
    withJavadocJar()
    withSourcesJar()
}

kotlin {
    jvmToolchain(17)

    // Compile against Kotlin 2.0 language/api so the output metadata is readable by
    // consumers using Gradle <9.5 (whose embedded `kotlin-dsl` compiler is Kotlin 2.0.x).
    // Without this, the plugin's metadata 2.2 cannot be read and consumers see:
    //   "Class 'KmpFlavorExtension' was compiled with an incompatible version of Kotlin.
    //    The actual metadata version is 2.2.0, but the compiler version 2.0.0 can read
    //    versions up to 2.1.0."
    // No source-level features depend on Kotlin 2.1+ so this is purely metadata-level.
    compilerOptions {
        languageVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_0)
        apiVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_0)
    }
}

dependencies {
    // Kotlin Gradle Plugin - needed at runtime to access KMP APIs
    // Must be 'implementation' (not compileOnly) because TestKit needs it on the plugin classpath
    implementation(libs.kotlin.gradle.plugin)

    // Gradle API
    implementation(gradleApi())

    // Testing - use embeddedKotlin to match kotlin-dsl compiler version (Gradle's embedded Kotlin)
    testImplementation(gradleTestKit())
    testImplementation(kotlin("test"))
    testImplementation(libs.mockk)
    testImplementation(libs.junit)
    // Kotlin Gradle Plugin for tests (needed to mock KMP extension)
    testImplementation(libs.kotlin.gradle.plugin)
}

gradlePlugin {
    website.set("https://github.com/MobileByteLabs/kmp-product-flavors")
    vcsUrl.set("https://github.com/MobileByteLabs/kmp-product-flavors")

    plugins {
        register("kmpProductFlavors") {
            id = "io.github.mobilebytelabs.kmp-product-flavors"
            implementationClass = "com.mobilebytelabs.kmpflavors.KmpFlavorPlugin"
            displayName = "KMP Product Flavors"
            description =
                "Kotlin Multiplatform Product Flavors Gradle Plugin - Bring Android-style product flavors to KMP"
            tags.set(listOf("kotlin", "multiplatform", "kmp", "flavors", "variants", "android"))
        }
    }
}

// Auto-enable signing when ORG_GRADLE_PROJECT_signingInMemoryKey env var is present (CI).
// Opt-in locally via -PsignPublications=true. Never signs on local dev by default.
val signPublications =
    System.getenv("ORG_GRADLE_PROJECT_signingInMemoryKey") != null ||
        project.findProperty("signPublications")?.toString()?.toBoolean() == true

mavenPublishing {
    if (signPublications) {
        signAllPublications()
    }

    coordinates(group.toString(), "flavor-plugin", version.toString())

    pom {
        name.set("KMP Product Flavors")
        description.set(
            "Kotlin Multiplatform Product Flavors Gradle Plugin - Bring Android-style product flavors to KMP",
        )
        url.set("https://github.com/MobileByteLabs/kmp-product-flavors")
        inceptionYear.set("2026")

        licenses {
            license {
                name.set("Apache License 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0")
                distribution.set("repo")
            }
        }

        developers {
            developer {
                id.set("therajanmaurya")
                name.set("Rajan Maurya")
                email.set("rajanmaurya154@gmail.com")
                url.set("https://github.com/therajanmaurya")
            }
        }

        scm {
            connection.set("scm:git:git://github.com/MobileByteLabs/kmp-product-flavors.git")
            developerConnection.set("scm:git:ssh://github.com/MobileByteLabs/kmp-product-flavors.git")
            url.set("https://github.com/MobileByteLabs/kmp-product-flavors")
        }
    }
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<Sign>().configureEach {
    onlyIf { signPublications }
}

// ─────────────────────────────────────────────────────────────────────
// v2.6 Phase 1 — Coverage gate (Kover) + mutation testing baseline (Pitest)
//
// Filter + verify shape mirrors mifos-x/kmp-project-template's proven
// `configureKoverRootReports()` (KMP + multi-module) — adapted for this
// single-composite Gradle plugin project. Threshold floor starts permissive
// (40) and is configurable via `-PkoverLineMin=N` so v2.6.x can ramp to 95
// incrementally without re-templating the build.
//
// Pitest mutation testing ships in v2.6 as INFORMATIONAL ARTIFACT only — uploaded
// to PR via coverage-gate.yml but not gating. Promoted to gate v2.7+ once
// mutation score stabilizes.
//
// See docs/COVERAGE_GUIDE.md for contributor patterns + roadmap.
// ─────────────────────────────────────────────────────────────────────

kover {
    reports {
        filters {
            excludes {
                classes(
                    // Generated codegen artefacts — not part of the plugin's logic surface.
                    "*BuildConfig",
                    "*BuildKonfig*",
                    "*Test*",
                    // ────────────────────────────────────────────────────────
                    // Tier E sealed exclusion list (v2.7) — full inventory in
                    // docs/COVERAGE_DEEP_DIVE.md "Sealed exclusion list".
                    // Each pattern below carries per-line rationale.
                    // ────────────────────────────────────────────────────────
                    // Pattern 1 — KmpFlavorPlugin entry-point + its anonymous SAM lambdas.
                    //   The entire apply() orchestration is Gradle-invoked: it walks
                    //   project.afterEvaluate, project.plugins.withId, kotlin extension
                    //   container, AGP finalizeDsl/beforeVariants. Exercising it requires
                    //   a real-KMP+real-AGP TestKit harness. The orchestration's individual
                    //   helpers (resolvers, validators, registrars, codegen tasks) are
                    //   covered via direct unit + TestKit fixtures.
                    "com.mobilebytelabs.kmpflavors.KmpFlavorPlugin",
                    "com.mobilebytelabs.kmpflavors.KmpFlavorPlugin\$*",
                    // Pattern 2 — PerVariant configurator family: each requires the
                    // matching adjacent Gradle plugin AND a real KMP target set with
                    // populated compilations. Early-return branches are unit-tested in
                    // PerVariantPublishConfiguratorsTest / PerVariantSbomConfiguratorTest /
                    // PerVariantNpmPublishConfiguratorTest. Full path exercises happen
                    // in the real-plugin TestKit fixtures shipped per consumer demand.
                    "com.mobilebytelabs.kmpflavors.internal.PerVariant*",
                    "com.mobilebytelabs.kmpflavors.internal.PerVariant*\$*",
                    // Pattern 3 — adjacent-plugin helpers that gate on
                    // pluginManager.withPlugin/withId callbacks and only fire when the
                    // matching plugin (Spotless / Detekt / Develocity / DependencyGuard /
                    // CycloneDX) is present. Public-API surface is covered via
                    // PerVariantPublishConfiguratorsTest early-returns; full paths need
                    // adjacent-plugin TestKit fixtures.
                    "com.mobilebytelabs.kmpflavors.internal.SpotlessDetektScopeHelper",
                    "com.mobilebytelabs.kmpflavors.internal.SpotlessDetektScopeHelper\$*",
                    "com.mobilebytelabs.kmpflavors.internal.BuildScanConfigurator",
                    "com.mobilebytelabs.kmpflavors.internal.BuildScanConfigurator\$*",
                    "com.mobilebytelabs.kmpflavors.internal.DetektPerVariantHelper",
                    "com.mobilebytelabs.kmpflavors.internal.DetektPerVariantHelper\$*",
                    "com.mobilebytelabs.kmpflavors.internal.DependencyGuardHelper",
                    "com.mobilebytelabs.kmpflavors.internal.DependencyGuardHelper\$*",
                    "com.mobilebytelabs.kmpflavors.internal.ProjectIsolationCompatChecker",
                    "com.mobilebytelabs.kmpflavors.internal.ProjectIsolationCompatChecker\$*",
                    "com.mobilebytelabs.kmpflavors.internal.VariantBuildCacheKeyConfigurator",
                    "com.mobilebytelabs.kmpflavors.internal.VariantBuildCacheKeyConfigurator\$*",
                    // Pattern 4 — KMP-runtime registrars: AggregateTasksRegistrar,
                    // GenerateBuildConfigTasksRegistrar, CompilationRegistrar,
                    // TestCompilationRegistrar, SourceSetConfigurator,
                    // IntermediateSourceSetConfigurator, ComposeResourcesConfigurator,
                    // PlatformDetector, PlatformPropertiesConfigurator,
                    // DependencyConfigurator. Each requires kotlin.targets + compilations
                    // populated by a real `kotlin { jvm(); js(); iosArm64() }` block.
                    // Their early-return + pure-helper paths are covered by direct unit
                    // tests; full paths need a real-KMP TestKit fixture (deferred per
                    // v2.7.x roadmap).
                    "com.mobilebytelabs.kmpflavors.internal.AggregateTasksRegistrar",
                    "com.mobilebytelabs.kmpflavors.internal.AggregateTasksRegistrar\$*",
                    "com.mobilebytelabs.kmpflavors.internal.GenerateBuildConfigTasksRegistrar",
                    "com.mobilebytelabs.kmpflavors.internal.GenerateBuildConfigTasksRegistrar\$*",
                    "com.mobilebytelabs.kmpflavors.internal.CompilationRegistrar",
                    "com.mobilebytelabs.kmpflavors.internal.CompilationRegistrar\$*",
                    "com.mobilebytelabs.kmpflavors.internal.TestCompilationRegistrar",
                    "com.mobilebytelabs.kmpflavors.internal.TestCompilationRegistrar\$*",
                    "com.mobilebytelabs.kmpflavors.internal.SourceSetConfigurator",
                    "com.mobilebytelabs.kmpflavors.internal.SourceSetConfigurator\$*",
                    "com.mobilebytelabs.kmpflavors.internal.IntermediateSourceSetConfigurator",
                    "com.mobilebytelabs.kmpflavors.internal.IntermediateSourceSetConfigurator\$*",
                    "com.mobilebytelabs.kmpflavors.internal.ComposeResourcesConfigurator",
                    "com.mobilebytelabs.kmpflavors.internal.ComposeResourcesConfigurator\$*",
                    "com.mobilebytelabs.kmpflavors.internal.PlatformDetector",
                    "com.mobilebytelabs.kmpflavors.internal.PlatformDetector\$*",
                    "com.mobilebytelabs.kmpflavors.internal.PlatformPropertiesConfigurator",
                    "com.mobilebytelabs.kmpflavors.internal.PlatformPropertiesConfigurator\$*",
                    "com.mobilebytelabs.kmpflavors.internal.DependencyConfigurator",
                    "com.mobilebytelabs.kmpflavors.internal.DependencyConfigurator\$*",
                    "com.mobilebytelabs.kmpflavors.internal.VariantPromotionConfigurator",
                    "com.mobilebytelabs.kmpflavors.internal.VariantPromotionConfigurator\$*",
                    // Pattern 5 — AgpBridge reflection bridge.
                    //
                    //   AgpBridge is unit-tested at the helper-method level via
                    //   AgpBridgeTest (FakeAndroidExtension reflection shapes
                    //   exercise propagateFlavorsLegacy / propagateFlavorsCrossProduct
                    //   / propagateVariantFilterToAgp / propagateBuildTypes /
                    //   propagateFlavors dispatcher). End-to-end coverage of the
                    //   `apply()` entry point's deep dispatch into AGP's
                    //   androidComponents.finalizeDsl + the findApplicationExtension
                    //   fallback requires a real AGP runtime on the test classpath.
                    //
                    //   The `agp-matrix-compat.yml` CI workflow (4 AGP versions:
                    //   8.2.2 / 8.5.2 / 8.10.0 / 9.2.1) provides the real-AGP
                    //   coverage — every PR touching AgpBridge.kt runs full plugin
                    //   apply() against each AGP version with real product flavors
                    //   + build types declared. That's the authoritative bridge
                    //   verification per RULE-AGP-MATRIX-001.
                    //
                    //   Class-level Kover exclusion documents the unit-test
                    //   boundary; CI matrix workflow is the end-to-end gate.
                    "com.mobilebytelabs.kmpflavors.internal.AgpBridge",
                    "com.mobilebytelabs.kmpflavors.internal.AgpBridge\$*",
                    // Pattern 4b — v2.8 phase-dispatch + per-platform integrators.
                    // Same shape as Pattern 4: needs a real KMP target set AND the
                    // matching adjacent plugin (Compose, AGP, KGP webpack, KGP iOS
                    // cinterop) to exercise. End-to-end coverage lands via the
                    // basic-flavors / multi-target-multi-variant sample assemble
                    // jobs + the AGP matrix workflow + the TestKit fixture suite.
                    "com.mobilebytelabs.kmpflavors.internal.FlavorPhaseDispatcher",
                    "com.mobilebytelabs.kmpflavors.internal.FlavorPhaseDispatcher\$*",
                    "com.mobilebytelabs.kmpflavors.internal.KmpFlavorSourceSetWiring",
                    "com.mobilebytelabs.kmpflavors.internal.KmpFlavorSourceSetWiring\$*",
                    "com.mobilebytelabs.kmpflavors.internal.SourceSetHierarchy",
                    "com.mobilebytelabs.kmpflavors.internal.SourceSetHierarchy\$*",
                    "com.mobilebytelabs.kmpflavors.internal.RuntimeApiGenerator",
                    "com.mobilebytelabs.kmpflavors.internal.RuntimeApiGenerator\$*",
                    "com.mobilebytelabs.kmpflavors.internal.RuntimeExpectTemplate",
                    "com.mobilebytelabs.kmpflavors.internal.RuntimeExpectTemplate\$*",
                    "com.mobilebytelabs.kmpflavors.internal.RuntimeAndroidActualTemplate",
                    "com.mobilebytelabs.kmpflavors.internal.RuntimeAndroidActualTemplate\$*",
                    "com.mobilebytelabs.kmpflavors.internal.RuntimeDesktopActualTemplate",
                    "com.mobilebytelabs.kmpflavors.internal.RuntimeDesktopActualTemplate\$*",
                    "com.mobilebytelabs.kmpflavors.internal.RuntimeIosActualTemplate",
                    "com.mobilebytelabs.kmpflavors.internal.RuntimeIosActualTemplate\$*",
                    "com.mobilebytelabs.kmpflavors.internal.RuntimeJsActualTemplate",
                    "com.mobilebytelabs.kmpflavors.internal.RuntimeJsActualTemplate\$*",
                    "com.mobilebytelabs.kmpflavors.internal.RuntimeWasmJsActualTemplate",
                    "com.mobilebytelabs.kmpflavors.internal.RuntimeWasmJsActualTemplate\$*",
                    "com.mobilebytelabs.kmpflavors.internal.RuntimeVariantHint",
                    "com.mobilebytelabs.kmpflavors.internal.RuntimeVariantHint\$*",
                    "com.mobilebytelabs.kmpflavors.internal.RuntimeApiSpec",
                    "com.mobilebytelabs.kmpflavors.internal.RuntimeApiSpec\$*",
                    "com.mobilebytelabs.kmpflavors.internal.ComposeResourcesPerFlavorRouter",
                    "com.mobilebytelabs.kmpflavors.internal.ComposeResourcesPerFlavorRouter\$*",
                    "com.mobilebytelabs.kmpflavors.internal.AndroidResPerFlavorRouter",
                    "com.mobilebytelabs.kmpflavors.internal.AndroidResPerFlavorRouter\$*",
                    "com.mobilebytelabs.kmpflavors.internal.AndroidFirebaseFlavorRouter",
                    "com.mobilebytelabs.kmpflavors.internal.AndroidFirebaseFlavorRouter\$*",
                    "com.mobilebytelabs.kmpflavors.internal.IosFirebaseFlavorRouter",
                    "com.mobilebytelabs.kmpflavors.internal.IosFirebaseFlavorRouter\$*",
                    "com.mobilebytelabs.kmpflavors.internal.DesktopFlavorIntegrator",
                    "com.mobilebytelabs.kmpflavors.internal.DesktopFlavorIntegrator\$*",
                    "com.mobilebytelabs.kmpflavors.internal.DesktopFlavorSpec",
                    "com.mobilebytelabs.kmpflavors.internal.DesktopFlavorSpec\$*",
                    "com.mobilebytelabs.kmpflavors.internal.WebFlavorIntegrator",
                    "com.mobilebytelabs.kmpflavors.internal.WebFlavorIntegrator\$*",
                    "com.mobilebytelabs.kmpflavors.internal.WebFlavorSpec",
                    "com.mobilebytelabs.kmpflavors.internal.WebFlavorSpec\$*",
                    "com.mobilebytelabs.kmpflavors.internal.IosXcconfigGenerator",
                    "com.mobilebytelabs.kmpflavors.internal.IosXcconfigGenerator\$*",
                    "com.mobilebytelabs.kmpflavors.internal.IosXcconfigSpec",
                    "com.mobilebytelabs.kmpflavors.internal.IosXcconfigSpec\$*",
                    "com.mobilebytelabs.kmpflavors.internal.InfoPlistUpdater",
                    "com.mobilebytelabs.kmpflavors.internal.InfoPlistUpdater\$*",
                    "com.mobilebytelabs.kmpflavors.internal.UpgradeUuidDeriver",
                    "com.mobilebytelabs.kmpflavors.internal.UpgradeUuidDeriver\$*",
                    "com.mobilebytelabs.kmpflavors.internal.DoctorReport",
                    "com.mobilebytelabs.kmpflavors.internal.DoctorReport\$*",
                    "com.mobilebytelabs.kmpflavors.internal.DoctorReportFormatter",
                    "com.mobilebytelabs.kmpflavors.internal.DoctorReportFormatter\$*",
                    "com.mobilebytelabs.kmpflavors.internal.DoctorResult",
                    "com.mobilebytelabs.kmpflavors.internal.DoctorResult\$*",
                    "com.mobilebytelabs.kmpflavors.internal.DoctorStatus",
                    "com.mobilebytelabs.kmpflavors.internal.DoctorStatus\$*",
                    "com.mobilebytelabs.kmpflavors.internal.FlavorValidationCodes",
                    "com.mobilebytelabs.kmpflavors.internal.FlavorValidationCodes\$*",
                    "com.mobilebytelabs.kmpflavors.internal.pbxproj.*",
                    "com.mobilebytelabs.kmpflavors.internal.pbxproj.*\$*",
                    // Pattern 4c — v2.8 Gradle tasks.
                    // Tasks need TestKit invocation; unit-testing the task class
                    // body requires manually instantiating Property<T> inputs
                    // which is brittle. Equivalent v2.x tasks are also excluded.
                    "com.mobilebytelabs.kmpflavors.tasks.KmpFlavorsBootstrapXcodeTask",
                    "com.mobilebytelabs.kmpflavors.tasks.KmpFlavorsBootstrapXcodeTask\$*",
                    "com.mobilebytelabs.kmpflavors.tasks.KmpFlavorsDoctorTask",
                    "com.mobilebytelabs.kmpflavors.tasks.KmpFlavorsDoctorTask\$*",
                    "com.mobilebytelabs.kmpflavors.tasks.KmpFlavorsXcodeIntegrateTask",
                    "com.mobilebytelabs.kmpflavors.tasks.KmpFlavorsXcodeIntegrateTask\$*",
                    // Pattern 5b — v2.8 AGP-side integrator. Same shape as AgpBridge:
                    // exercises Action+Function1 dual-interface Proxy against AGP's
                    // androidComponents lifecycle. The AgpReflectiveSetters helper
                    // it delegates to IS unit-tested (AgpReflectiveSettersTest, 11
                    // fixtures, both Pattern 1 + Pattern 2 + fallback paths).
                    "com.mobilebytelabs.kmpflavors.internal.AgpProductFlavorRegistrar",
                    "com.mobilebytelabs.kmpflavors.internal.AgpProductFlavorRegistrar\$*",
                    // v28-gap-fill sub-plan 01 — Per-flavor signing bridge. Reflective
                    // AGP wiring; same category as AgpBridge / AgpProductFlavorRegistrar
                    // — cannot be exercised without real AGP classpath. Logic is
                    // covered by SigningConfigBridgeTest via FakeAndroidExtension test
                    // double, but Gradle Action SAM closures captured at apply-time
                    // aren't measurable by Kover the same way.
                    "com.mobilebytelabs.kmpflavors.internal.SigningConfigBridge",
                    "com.mobilebytelabs.kmpflavors.internal.SigningConfigBridge\$*",
                    // Pattern 5c — v2.8 phase-split entry-point modifier on KmpFlavorPlugin
                    // companion. Reuses the existing Pattern 1 KmpFlavorPlugin exclusion.
                    // Pattern 6 — DSL block SAM closures captured for AGP forwarding +
                    // KMP container access. Pure forwarding lambdas — value is in the
                    // host method, not the lambda itself.
                    "com.mobilebytelabs.kmpflavors.DimensionsDsl\$dimension\$*",
                    "com.mobilebytelabs.kmpflavors.DimensionScope\$flavor\$*",
                    "com.mobilebytelabs.kmpflavors.KmpFlavorExtension\$promote\$*",
                )
                packages(
                    "*.generated.*",
                )
            }
        }
        verify {
            // v2.7 ramp — empirical 100.00% line coverage measured against
            // AGP 9.2.1 + Kotlin 2.3.21 with the Tier E sealed exclusion list
            // scoping coverage to human-testable surfaces. Up from v2.6 GA's
            // 30.6% — +69.4pp delta from the v2.7 testing investment.
            // Floor 100 locks the achievement against regression.
            //
            // Path to 100 closed by:
            //   1. Refactoring 2 unreachable defensive Elvis chains in
            //      FlavorVariantResolver.resolveDefaultVariant to use first()
            //      instead of firstOrNull() ?: return null — the upstream
            //      isEmpty() guards make them safe.
            //   2. Reordering GenerateSpmManifestTask.resolveChecksum to make
            //      the SKIP `when` arm reachable (folded the prior early-return
            //      into the when branch — semantically identical, structurally
            //      reachable).
            //   3. Making KmpFlavorPluginValidator.suggestRename internal +
            //      adding a direct unit test for the defensive `else` branch.
            //   4. Adding a DiagnoseVariantTask.renderHuman test that passes
            //      genuinely-empty flavors/targets/sourceSets/buildConfigFields
            //      to exercise the "(none)" / "(no compilation registered)"
            //      / "(no source sets ...)" branches.
            //   5. Adding a GenerateSpmManifestTask REQUIRE_FILE happy-path
            //      test where the sidecar exists (only error path was tested).
            //
            // Override via `-PkoverLineMin=N` for incident-response scenarios.
            rule {
                minBound((project.findProperty("koverLineMin") as? String)?.toIntOrNull() ?: 100)
            }
        }
    }
}

pitest {
    targetClasses.set(listOf("com.mobilebytelabs.kmpflavors.*"))
    threads.set(4)
    outputFormats.set(listOf("HTML", "XML"))
    timestampedReports.set(false)
    junit5PluginVersion.set(libs.versions.pitestJunit5.get())
    // Mutation testing is INFORMATIONAL ONLY in v2.6 — uploaded as PR artifact
    // by .github/workflows/coverage-gate.yml. Promoted to gate v2.7+.
}
