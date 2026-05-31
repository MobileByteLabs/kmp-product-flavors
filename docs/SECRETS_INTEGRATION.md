# Secrets Integration

> **Since v2.5** — consumer contract for vault-integrated per-flavor BuildKonfig
> secret injection via the `buildKonfig { secret(id) }` DSL block.

This document is the canonical contract for the v2.5+ `kmpFlavors.buildKonfig { secret(id) }`
DSL. It covers what the consumer must declare, how the plugin resolves the value,
what happens when the consumer's `secrets-manifest.yaml` doesn't meet the v2.5
schema floor, and the security discipline that gates real secret-value emission.

---

## tl;dr

```kotlin
kmpFlavors {
    dimensions {
        dimension("tier") { flavor("free"); flavor("paid") }
        dimension("env")  { flavor("dev"); flavor("prod") }
    }
    buildKonfig {
        secret("api-key")  // resolves per-variant via secrets-manifest.yaml
    }
}
```

Plus a `secrets-manifest.yaml` at the project root at schema v2.1+ declaring the
secret with a `flavor_selector` block.

Plus `/secrets pull` to materialize the per-variant values into `local.properties`
before the build.

If any of those preconditions are missing, the plugin emits a placeholder value
(`<unresolved:see-docs-SECRETS_INTEGRATION>`) and surfaces `KMPF-V26` WARN —
**no hardcoded secret values ever appear in the generated `.kt` file** (SV15
compliance per `RULE-SECRETS-VAULT-001`).

---

## Status: v2.5 ships the contract; v2.5.x ships the real value flow

| Component | v2.5 | v2.5.x patch |
|---|:--:|:--:|
| `buildKonfig { secret(id) }` DSL block | ✓ shipped | (unchanged) |
| `secrets-manifest.yaml` schema v2.1 framework-side support | (consumer concern) | (consumer concern) |
| `BuildKonfigSecretResolver` API in the plugin | ✓ shipped (callable) | wired into codegen |
| `FrameworkSchemaCheckTask` Gradle task | ✓ shipped | (unchanged) |
| `KMPF-V26` WARN emission on schema fallback | ✓ shipped | (unchanged) |
| Codegen real-value emission | placeholder | ✓ shipped |
| `samples/buildkonfig-rich/` sample | ✓ shipped (uses placeholder) | (gets real values) |

**Why split shipping:** v2.5 introduces the consumer-facing surface (DSL +
validator codes + framework integration) so consumers can adopt the API and start
declaring secrets. The plugin emits a placeholder value in the generated
`BuildKonfig.kt` until the framework-side `secrets-manifest.yaml` schema v2.1
PR + `secrets-pull.sh --emit-gradle-flavor-map` mode land — those are tracked as
a hard dependency in the v2.5 GOAL.md Risks section. Shipping the DSL early lets
consumers prepare migrations + verify the contract; shipping placeholder values
means there's never a window where the plugin emits a hardcoded secret into
generated code.

---

## Consumer contract

To use `kmpFlavors.buildKonfig { secret(id) }`, your project MUST provide three things:

### 1. A `secrets-manifest.yaml` at the project root

Schema version v2.1+ is required. v2.0 manifests trigger `KMPF-V26` WARN with
graceful degradation (placeholder emission, no build break).

### 2. A `needs[]` entry per secret with a `flavor_selector` block

```yaml
schema_version: "2.1"
default_vault: mbs

needs:
  - id: api-key                  # logical name (matches `buildKonfig { secret("api-key") }`)
    kind: env_var
    env_key: API_KEY
    materialize:
      at: local.properties
    flavor_selector:
      selector_type: gradle_flavor
      selector_values:
        # Map active variant name → vault secret id. Variant name format follows
        # kmpFlavors resolution: tier × env cross-product produces 'freeDev',
        # 'paidProd', etc.
        freeDev: api-key-test
        freeProd: api-key-test    # free tier always uses test endpoint
        paidDev: api-key-test
        paidProd: api-key-live    # only paid+prod uses real billing endpoint
```

The `selector_values` map keys MUST match the kmpFlavors resolved variant names
(visible via `./gradlew :samples:buildkonfig-rich:listFlavors`).

### 3. A `/secrets pull` run before each build

Materializes the per-secret values from the vault into `local.properties`. The
plugin reads from `local.properties` at codegen time — it does NOT decrypt
secrets directly (that would violate `RULE-SECRETS-VAULT-001` SV17).

---

## Fallback behavior — graceful degradation

The plugin honors three failure modes without breaking the build:

| Scenario | Behavior | Validator code |
|---|---|:--:|
| `secrets-manifest.yaml` missing | Emit placeholder + WARN log | `KMPF-V26` (WARN) |
| Schema version < v2.1 | Emit placeholder + WARN log | `KMPF-V26` (WARN) |
| Manifest valid, missing `flavor_selector` for active variant | Emit placeholder + WARN log + (real value flow ships in v2.5.x) | `KMPF-V26` (ERROR — v2.5.x) |
| Manifest valid, `local.properties` missing | Emit placeholder + WARN log + (real value flow ships in v2.5.x) | `KMPF-V26` (ERROR — v2.5.x) |
| Manifest valid + complete | Emit real value | (no finding) |

In all cases, **the codegen never inlines a hardcoded secret value into the
generated `BuildKonfig.kt` file**. SV15 compliance is the non-negotiable invariant.

---

## RULE-SECRETS-VAULT-001 compliance

The plugin honors three relevant sub-checks of `RULE-SECRETS-VAULT-001`:

| Sub-check | What it constrains | How the plugin complies |
|---|---|---|
| **SV4** PLAINTEXT-SAFETY | Plaintext-resolved secret values MUST NOT appear in logs, error messages, or task captures | Plugin reads `local.properties` via `Properties.load()` + threads value via `@Internal` task input (NOT `@Input` — would be cached). `KMPF-V26` WARN message names the secret ID, NEVER the value. |
| **SV15** NO-HARDCODED-SECRETS | Generated/committed source code MUST NOT contain hardcoded secret values | Placeholder emission discipline — generated `BuildKonfig.kt` is committed to consumer source control; the actual secret resolution defers to runtime injection (v2.5.x patch). |
| **SV17** SAFE-HANDOFF | Sub-process calls handling secrets MUST shred temp files + redact stderr | Plugin does NOT call `sops` or `gpg` directly. Value flow: `/secrets pull` (framework script) → `local.properties` (gitignored) → plugin (read-only). Plugin never owns the encryption boundary. |

---

## perTarget semantics — v2.5 simplification

The v2.5 `perTarget(name) { field(...) }` DSL emits a NESTED `object PerTarget {
object {TargetName} { ... } }` block inside the main BuildKonfig object — not
separate per-target `.kt` files.

```kotlin
// samples/buildkonfig-rich/build.gradle.kts declares:
buildKonfig {
    perTarget("iosMain") {
        field("BUNDLE_ID_SUFFIX", "String", "\".dev\"")
    }
}

// Generated BuildKonfig.kt contains:
object BuildKonfig {
    // ... main fields ...
    object PerTarget {
        object IosMain {
            const val BUNDLE_ID_SUFFIX: String = ".dev"
        }
    }
}

// Consumer code in iosMain accesses:
val suffix = BuildKonfig.PerTarget.IosMain.BUNDLE_ID_SUFFIX
```

**Why nested object instead of per-file isolation:** KMP source-set `dependsOn`
discipline naturally gates access at the language level. Code in `androidMain`
can't reference `BuildKonfig.PerTarget.IosMain` because `iosMain`'s symbols
aren't on the androidMain classpath. The nested-object pattern delivers the same
access discipline with simpler codegen.

**True per-file source-set isolation deferred to v2.6** — when a consumer
demand signal surfaces for emitting `iosMain/BuildKonfigPerTarget.kt` as a
separate file (e.g. for fine-grained @Input cacheability).

---

## Cross-references

- **DSL implementation:** `build-logic/flavor-plugin/src/main/kotlin/com/mobilebytelabs/kmpflavors/BuildKonfigDsl.kt`
- **Resolver:** `build-logic/flavor-plugin/src/main/kotlin/com/mobilebytelabs/kmpflavors/internal/BuildKonfigSecretResolver.kt`
- **Pre-codegen check task:** `build-logic/flavor-plugin/src/main/kotlin/com/mobilebytelabs/kmpflavors/tasks/FrameworkSchemaCheckTask.kt`
- **Codegen:** `build-logic/flavor-plugin/src/main/kotlin/com/mobilebytelabs/kmpflavors/tasks/GenerateBuildConfigTask.kt`
  (v2.5 emission blocks: dimension enums, customField, perTarget, secret placeholders)
- **Validator codes:** `docs/ERROR_CODES.md` — see KMPF-V26, V27, V28 entries (authored in v2.5 Phase 4)
- **Sample:** `samples/buildkonfig-rich/build.gradle.kts` — all four DSL features end-to-end
- **Framework-side dependency:** `core/schemas/secrets-manifest.schema.json` schema v2.1
  + `core/scripts/secrets-pull.sh --emit-gradle-flavor-map` mode — tracked as v2.5 GOAL.md
  hard dependency (ships in separate framework PR)
- **Rule:** `RULE-SECRETS-VAULT-001` (`layers/secrets/rules/RULE-SECRETS-VAULT-001.md`)
  — sub-checks SV4, SV15, SV17 constrain the plugin's secret-handling discipline
