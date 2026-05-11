/*
 * Example consumer-side flavor extension.
 *
 * In real consumer apps (mifos-mobile, mifos-pay, mifos-x-group-banking, ...)
 * this file lives at:
 *     build-logic/convention/src/main/kotlin/local/LocalFlavors.kt
 *
 * `sync-dirs.sh` is configured to SKIP the `local/` directory, so this file
 * is preserved when the consumer re-syncs build-logic from the template.
 *
 * In this sample, the file is checked in to demonstrate the pattern. A
 * consumer that has NO extensions should simply delete this file (or never
 * create it) — `LocalFlavorsLoader.applyIfPresent()` silently no-ops when
 * `local.LocalFlavors` is absent.
 *
 * This example adds a `bank` dimension for tenant-style apps. Swap for your
 * own dimensions (language, region, deployment-target, signing-config, ...).
 */
package local

import com.mobilebytelabs.kmpflavors.KmpFlavorExtension
import org.gradle.api.Project

object LocalFlavors {
    @JvmStatic
    fun apply(ext: KmpFlavorExtension, project: Project) {
        // Add a new dimension on top of `tier`.
        ext.flavorDimensions.register("bank") { priority.set(1) }

        ext.flavors.register("bankA") {
            dimension.set("bank")
            isDefault.set(true)
            applicationIdSuffix.set(".banka")
            bundleIdSuffix.set(".banka")
            buildConfigField("String", "BANK_ID", "\"bankA\"")
            buildConfigField("String", "BANK_URL", "\"https://api.banka.openmf.org\"")
        }

        ext.flavors.register("bankB") {
            dimension.set("bank")
            applicationIdSuffix.set(".bankb")
            bundleIdSuffix.set(".bankb")
            buildConfigField("String", "BANK_ID", "\"bankB\"")
            buildConfigField("String", "BANK_URL", "\"https://api.bankb.openmf.org\"")
        }

        // Optional: override a base flavor's URL for this specific consumer.
        ext.flavors.named("demo") {
            buildConfigField("String", "BASE_URL", "\"https://demo.bank-consortium.openmf.org\"")
        }
    }
}
