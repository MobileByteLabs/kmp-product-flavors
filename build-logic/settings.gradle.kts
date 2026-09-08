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

rootProject.name = "build-logic"

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))

            // Multi-KGP matrix override.
            //
            // THIS is the lever that makes `.github/workflows/multi-kgp-matrix.yml` real.
            // The plugin declares `implementation(libs.kotlin.gradle.plugin)` so that KGP is
            // on the TestKit plugin classpath — which means a generated fixture takes its
            // KGP from that classpath and IGNORES the `kotlin("multiplatform") version "…"`
            // string in its own plugins block. Overriding the fixture text therefore proves
            // nothing; overriding the CATALOG changes the KGP the plugin is built and tested
            // against, which is what the matrix actually wants to vary.
            //
            // Without this, every matrix row ran the same KGP and the nightly was vacuous.
            providers
                .gradleProperty("kmpflavor.test.kgp.version")
                .orNull
                ?.takeIf { it.isNotBlank() }
                ?.let { version("kotlin", it) }
        }
    }
}

include(":flavor-plugin")
