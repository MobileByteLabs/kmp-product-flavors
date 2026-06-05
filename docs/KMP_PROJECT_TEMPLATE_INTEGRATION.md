# kmp-product-flavors: Integration with kmp-project-template

> This document is a pointer. The canonical adoption record and version history
> live in the submodule itself — see links below.

---

## What the submodule tracks

[`samples/kmp-project-template/`](../samples/kmp-project-template/) is a git
submodule pointing at
[openMF/kmp-project-template](https://github.com/openMF/kmp-project-template).
It serves as the live proof-of-adoption: every version bump here is a real
integration test run against the template's full 2-flavor × 3-buildType matrix.

---

## Where to find adoption details

| What you need | Where to look |
|---|---|
| Version history, bump rationale, verify commands | [`samples/kmp-project-template/docs/ADOPTION_KMP_PRODUCT_FLAVORS.md`](../samples/kmp-project-template/docs/ADOPTION_KMP_PRODUCT_FLAVORS.md) |
| Current plugin version in the submodule | [`samples/kmp-project-template/gradle/libs.versions.toml`](../samples/kmp-project-template/gradle/libs.versions.toml) — `kmpProductFlavors` key |
| Convention-plugin pattern (Pattern 3b) | [`samples/kmp-project-template/build-logic/convention/src/main/kotlin/KMPFlavorsConventionPlugin.kt`](../samples/kmp-project-template/build-logic/convention/src/main/kotlin/KMPFlavorsConventionPlugin.kt) |
| DSL reference, BuildKonfig patterns, signing | [`CONSUMER_GUIDE.md`](CONSUMER_GUIDE.md) |
| Quick step-by-step setup | [`QUICKSTART.md`](QUICKSTART.md) |

---

## Updating the submodule pointer

When a new plugin version is released and verified in the submodule:

```bash
# 1. Inside the submodule — bump libs.versions.toml and verify
cd samples/kmp-project-template
# edit gradle/libs.versions.toml: kmpProductFlavors = "X.Y.Z"
./gradlew :cmp-android:assembleDemoDebug -x :cmp-android:copyGitHooks

# 2. Back in the plugin repo — stage the new pointer
cd ../..
git add samples/kmp-project-template
# then commit as part of the release PR
```

See `ADOPTION_KMP_PRODUCT_FLAVORS.md` in the submodule for the full bump
checklist including Maven Local workaround for same-day releases.

---

## Related

- [CONSUMER_GUIDE.md](CONSUMER_GUIDE.md) — motivation, patterns, anti-patterns
- [QUICKSTART.md](QUICKSTART.md) — step-by-step installation
- [REFERENCE.md](REFERENCE.md) — full DSL reference
