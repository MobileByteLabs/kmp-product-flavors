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
