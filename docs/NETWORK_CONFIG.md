# Network Configuration

> **Since v2.6** — variant-aware base URL + timeout constants via
> `kmpFlavors { buildKonfig { network { ... } } }`. Constants land inside
> `BuildKonfig.Network` for use with Ktor / any HTTP client. Approach A only
> (constants); Ktor client factory (Approach B) deferred to v2.7.

## DSL

```kotlin
kmpFlavors {
    buildKonfig {
        network {
            baseUrl(
                "free" to "https://api.free.example.com",
                "paid" to "https://api.paid.example.com",
            )
            timeout(seconds = 30)
        }
    }
}
```

## Generated output

Each variant's `BuildKonfig.kt` now includes a `Network` object whose
`BASE_URL` matches the active variant's flavor:

```kotlin
// Variant freeDev (active flavor = "free"):
object BuildKonfig {
    const val VARIANT_NAME: String = "freeDev"
    // ... flavor flags + custom fields ...

    object Network {
        const val BASE_URL: String = "https://api.free.example.com"
        const val TIMEOUT_SECONDS: Int = 30
    }
}

// Variant paidProd (active flavor = "paid"):
object BuildKonfig {
    const val VARIANT_NAME: String = "paidProd"
    // ...
    object Network {
        const val BASE_URL: String = "https://api.paid.example.com"
        const val TIMEOUT_SECONDS: Int = 30
    }
}
```

Resolution rule: the codegen picks the first `baseUrls` key that matches one
of the variant's active flavor names. For a 2D `tier × env` variant like
`freeDev`, the active flavors are `["free", "dev"]`; the codegen picks
`"free"` because that's the only baseUrl key in the map.

## Consumer usage with Ktor

```kotlin
import io.ktor.client.HttpClient
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.url

val httpClient = HttpClient {
    install(DefaultRequest) {
        url(BuildKonfig.Network.BASE_URL)
    }
    install(HttpTimeout) {
        requestTimeoutMillis = (BuildKonfig.Network.TIMEOUT_SECONDS * 1000).toLong()
        connectTimeoutMillis = (BuildKonfig.Network.TIMEOUT_SECONDS * 1000).toLong()
    }
}
```

## Validation

The plugin runs two validators at configuration time:

| Code        | Severity | Fires when                                                              |
|-------------|----------|-------------------------------------------------------------------------|
| `KMPF-V29`  | ERROR    | `baseUrl("X" to ...)` references a flavor name not registered            |
| `KMPF-V30`  | ERROR    | At least one resolved variant's active flavor has no matching `baseUrl`  |

### V29 — orphan baseUrl flavor

```kotlin
kmpFlavors {
    flavors { register("free"); register("paid") }
    buildKonfig {
        network {
            baseUrl(
                "free" to "https://...",
                "paid" to "https://...",
                "ghost" to "https://..."   // ← V29: 'ghost' isn't registered
            )
        }
    }
}
```

Fix: either register the flavor (`flavors { register("ghost") }`) or drop the
orphan key.

### V30 — no baseUrl for some variant's active flavor

```kotlin
kmpFlavors {
    flavors { register("free"); register("paid") }
    buildKonfig {
        network {
            baseUrl("free" to "https://...")   // ← V30: paidDebug has no baseUrl
        }
    }
}
```

Fix: add the missing entry (`"paid" to "https://..."`), or exclude the
unwanted variant via `variantFilter { if (...) exclude() }`.

## v2.7 preview — Approach B (Ktor client factory)

v2.7 will optionally emit a `flavorHttpClient(): HttpClient` factory function
that wires `BASE_URL`, `TIMEOUT_SECONDS`, and consumer-supplied auth
interceptors automatically. The pure-constants Approach A shipped in v2.6
remains the default — Approach B is opt-in to keep the plugin Ktor-agnostic
by default.

Tracking: v2.7 GOAL.md when authored.

## Out of scope

- Per-variant timeout overrides (single global `TIMEOUT_SECONDS`; per-variant
  deferred per D14)
- Multi-environment URL groups (e.g. `dev / staging / prod` × `free / paid`)
  — combine flavors per dimension instead
- Auto-injecting Ktor as a dependency — plugin stays Ktor-agnostic; consumer
  brings their own dep
- URL validation (HTTPS, no trailing slash, etc.) — left to the consumer

## See also

- `docs/CONDITIONAL_TARGETS.md` — companion v2.6 Phase 4 capability
- `docs/ERROR_CODES.md` — full validator catalog (KMPF-V01 through V30)
- `docs/SECRETS_INTEGRATION.md` — `buildKonfig { secret(...) }` for sensitive values
- `samples/conditional-targets/` — sample using `excludeTargets` + custom URL constants
- `plan-layer/.../v26-stability-parity-beyond-platform/04-targets-network.md` — originating epic plan
