# Plan: Reorganize Samples Directory

## Current State

```
kmp-product-flavors/
├── sample/          # Plugin demo with flavors (included in build)
├── sample-app/      # Compose UI app, no flavors (NOT included)
└── ...
```

**Issues:**
- Flat structure doesn't scale
- Names don't convey purpose
- No real-world template integration

---

## Proposed Structure

```
kmp-product-flavors/
├── samples/
│   ├── basic-flavors/              # Renamed from /sample
│   │   └── (minimal plugin demo)
│   │
│   ├── compose-multiplatform/      # Renamed from /sample-app
│   │   └── (full Compose app with flavors)
│   │
│   └── kmp-template-integration/   # NEW: Based on openMF template
│       ├── .git-subtree-ref        # Track upstream commit
│       └── (synced from openMF/kmp-project-template)
```

---

## Sample Descriptions

### 1. `basic-flavors/` (from `/sample`)
**Purpose:** Minimal demonstration of plugin features
- Multi-dimensional flavors (tier × environment)
- BuildConfig generation
- Flavor-specific source sets
- **Targets:** Desktop, iOS

### 2. `compose-multiplatform/` (from `/sample-app`)
**Purpose:** Real-world Compose app with flavor integration
- Full Material 3 UI
- Integrate the flavor plugin (currently missing)
- **Targets:** Android, iOS, Desktop, WASM

### 3. `kmp-template-integration/` (NEW)
**Purpose:** Production-ready KMP template with flavors
- Based on https://github.com/openMF/kmp-project-template
- Demonstrates plugin in modular architecture
- Sync script to pull upstream changes
- **Targets:** Android, iOS, Desktop, Web

---

## Implementation Steps

### Phase 1: Create Directory Structure
- [ ] Create `samples/` directory
- [ ] Move `sample/` → `samples/basic-flavors/`
- [ ] Move `sample-app/` → `samples/compose-multiplatform/`

### Phase 2: Update Gradle Configuration
- [ ] Update `settings.gradle.kts` with new paths:
  ```kotlin
  include(":samples:basic-flavors")
  include(":samples:compose-multiplatform")
  include(":samples:kmp-template-integration")
  ```
- [ ] Update each sample's `build.gradle.kts` if needed

### Phase 3: Add KMP Template Integration
- [ ] Add openMF template as git subtree:
  ```bash
  git subtree add --prefix=samples/kmp-template-integration \
    https://github.com/openMF/kmp-project-template.git main --squash
  ```
- [ ] Create sync script `scripts/sync-kmp-template.sh`
- [ ] Integrate flavor plugin into the template

### Phase 4: Enhance Compose Sample
- [ ] Add flavor plugin to `compose-multiplatform`
- [ ] Configure dimensions (free/paid, dev/prod)
- [ ] Add flavor-specific UI components

### Phase 5: Update Scripts & CI
- [ ] Update `MavenLocalRelease.sh` with new paths
- [ ] Update `.github/workflows/build.yml`
- [ ] Update documentation (README.md)

### Phase 6: Create Sync Script
```bash
# scripts/sync-kmp-template.sh
#!/bin/bash
# Pulls latest changes from openMF/kmp-project-template
git subtree pull --prefix=samples/kmp-template-integration \
  https://github.com/openMF/kmp-project-template.git main --squash
```

---

## File Changes Summary

| Action | From | To |
|--------|------|-----|
| Move | `/sample` | `/samples/basic-flavors` |
| Move | `/sample-app` | `/samples/compose-multiplatform` |
| Create | - | `/samples/kmp-template-integration` |
| Update | `settings.gradle.kts` | New include paths |
| Update | `MavenLocalRelease.sh` | New sample paths |
| Update | `.github/workflows/build.yml` | New matrix paths |
| Create | - | `scripts/sync-kmp-template.sh` |

---

## Questions for User

1. **Git subtree vs submodule:** Subtree is recommended (keeps history inline, no extra git commands for clones). Confirm?

2. **Include all samples in CI?** Currently only `basic-flavors` would be tested. Add others?

3. **Template customization:** Should we keep the openMF template as-is with plugin overlay, or fork and modify?

---

## Estimated Changes

- **Files moved:** ~50
- **Files modified:** ~10
- **Files created:** ~5
- **Breaking changes:** Build paths change (CI update required)
