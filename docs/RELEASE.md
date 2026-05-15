# Release flow

> v2.3+ document. Captures the end-to-end release cycle across the 3 repos involved: `MobileByteLabs/kmp-product-flavors` (this repo), `MobileByteLabs/mbl-actionhub-bump-version` (post-release bumper action), and `MobileByteLabs/mbl-actionhub` (workflow library).

---

## 30-second summary

```
feature PR merged                                  (manual approval)
       │
       ▼
manual dispatch: Actions → Publish                 (manual trigger)
       │
       ▼
publish to Maven Central + Plugin Portal           (auto)
       │
       ▼
mbl-actionhub-bump-version opens bump PR           (auto, alpha.0 → alpha.1)
       │
       ▼
auto-merge-bump.yml enables auto-merge             (auto, after CI green)
       │
       ▼
bump PR squash-merged + branch deleted             (auto)
       │
       ▼
ready for next publish dispatch                    (cycle complete)
```

End-to-end: ~30 minutes from publish dispatch to next-version ready.

Manual touch-points: 2 (feature PR merge approval + publish workflow dispatch).

---

## Pre-flight: what `Publish` validates before publishing

The publish workflow (`.github/workflows/publish-release.yml`) refuses to publish a commit unless one of these proves it was validated:

- A `PR Check` workflow ran successfully on the same `head_sha`.
- A `CI` workflow ran successfully on the same `head_sha`.

This means **always merge via PR**, never push directly to `development`. Direct pushes don't run `PR Check`, so the publish workflow will reject them with a clear error.

---

## Manual dispatch — `Publish` workflow

```
Actions → Publish → Run workflow → Run workflow
                    └─ optional: override `version` input
                    └─ (leave empty to use kmpflavors.version from gradle.properties)
```

The optional `version` input is **ignored** by `mbl-actionhub-resolve-version`. The gradle.properties value is the source of truth — bump it manually via a PR if you need to skip a version.

---

## SemVer-aware bumping (mbl-actionhub-bump-version v1.6+)

After a successful publish, `mbl-actionhub-bump-version` opens a bump PR. The bump algorithm is SemVer-pre-release-aware as of `@v1.6.0`:

| Just published | Default `next-bump-type: patch` opens bump PR to |
|---|---|
| `2.2.0-alpha.0` | `2.2.0-alpha.1` (continue alpha cycle) |
| `2.2.0-beta.2` | `2.2.0-beta.3` (continue beta cycle) |
| `2.2.0-rc.5` | `2.2.0-rc.6` (continue rc cycle) |
| `2.2.0` | `2.2.1` (GA patch — historical) |

To graduate from rc to GA (`2.2.0-rc.5` → `2.2.0`), manually override `next-bump-type: prerelease-graduate` on the publish workflow before dispatching the final rc publish.

---

## Pre-release-aware GitHub Release flag (mbl-actionhub v1.6+)

The publish workflow's `Create GitHub Release` step auto-flags SemVer pre-release tags as pre-releases:

| Tag | `isPrerelease` |
|---|---|
| `v2.2.0-alpha.0` | `true` (auto) |
| `v2.2.0-beta.1` | `true` (auto) |
| `v2.2.0-rc.2` | `true` (auto) |
| `v2.2.0` | `false` (GA — auto) |

Override the auto-detection with `force-prerelease: 'true' | 'false' | 'auto'` on the publish workflow inputs if needed (edge cases like a re-issued pre-release promoted to GA quality).

---

## Auto-merge bump PRs (`.github/workflows/auto-merge-bump.yml`)

`mbl-actionhub-bump-version` already enables auto-merge on its own bump PRs via `gh pr merge --auto`. That works when the default `GITHUB_TOKEN` has the right scopes.

When branch protection requires bot-bypass approval or the bumper's auto-merge attempt fails for any other reason, the local `auto-merge-bump.yml` workflow is a safety net:

- Listens to `pull_request` events for PRs whose head branch matches `chore/bump-version-*`.
- Listens to `check_suite.completed` events to retry enabling auto-merge after CI runs.
- Squash-merges + deletes the branch.

Scope is intentionally narrow — only PRs from the `github-actions[bot]` user with the `chore/bump-version-*` head prefix are eligible. A feature PR opened with that branch name by a human will NOT be auto-merged.

### Cron fallback (`.github/workflows/auto-merge-bump-cron.yml`)

The event-driven safety-net above is suppressed for PRs opened with the default `GITHUB_TOKEN` (GitHub's documented workflow-loop-prevention rule). The 2026-05-15 cascade smoke confirmed neither `pull_request` nor `check_suite` triggers fire for bumper-opened PRs.

The cron-based fallback closes the gap:

- Runs every 10 minutes.
- Scans for open bump PRs (`chore/bump-version-*` head ref + `github-actions[bot]` author).
- Squash-merges each one directly (no `--auto` wait-for-checks — workflow-token-pushed branches never get checks anyway).

Same scope restrictions: feature PRs opened by humans with the bump-prefix branch name are NOT auto-merged.

Trade-off: 5-10 min average merge latency vs the event-driven path. Worth it to avoid the alternative (PAT setup on every consumer repo, or manual `gh pr merge` after every publish).

Manual nudge during incidents:

```bash
gh workflow run "Auto-merge bump PRs (cron)" --repo MobileByteLabs/kmp-product-flavors
```

---

## Emergency stop

To temporarily disable the auto-merge cascade (e.g., during a release freeze, or to debug a misbehaving bump):

```bash
gh workflow disable "Auto-merge bump PRs" --repo MobileByteLabs/kmp-product-flavors
```

Re-enable:

```bash
gh workflow enable "Auto-merge bump PRs" --repo MobileByteLabs/kmp-product-flavors
```

Bump PRs opened during the disable window stay open as drafts; merge them manually.

---

## Cross-repo coordination

The full chain spans 3 repos:

| Repo | What lives there |
|---|---|
| `MobileByteLabs/kmp-product-flavors` | This repo. Owns the plugin source + the `publish-release.yml` consumer workflow + `auto-merge-bump.yml`. |
| `MobileByteLabs/mbl-actionhub` | Workflow library. Owns the reusable `publish-kmp-library.yml` workflow. v1.6+ ships SemVer-pre-release-aware GitHub Release flag auto-detection. |
| `MobileByteLabs/mbl-actionhub-bump-version` | Composite action. Owns the `bump.sh` algorithm. v1.6+ ships SemVer-pre-release-aware bumping. |

Both `mbl-actionhub` and `mbl-actionhub-bump-version` are pinned by tag in `.github/workflows/publish-release.yml`. Bump the pinned tags when upstream cuts a new release.

---

## Troubleshooting

**Bump PR went to `2.2.1` instead of `2.2.0-alpha.1`.**
The publish workflow is using `mbl-actionhub-bump-version` < v1.6.0. Bump the pin in `.github/workflows/publish-release.yml` to `@v1.6.0+`. The legacy behaviour (always strip pre-release suffix + patch-bump) is preserved for backwards compatibility on consumers that don't ship pre-releases.

**GitHub Release for `v2.2.0-alpha.N` shows as a full release (not pre-release).**
The publish workflow is using `mbl-actionhub` < v1.6.0. Bump the pin in `.github/workflows/publish-release.yml` to `@v1.6.0+`. Or override on the dispatch with `force-prerelease: 'true'`. Or manually flip with `gh release edit v2.2.0-alpha.N --prerelease`.

**Bump PR didn't auto-merge.**
Either the default `GITHUB_TOKEN` doesn't have merge scopes (check repo settings → Actions → workflow permissions → "Read and write permissions"), OR branch protection on `development` requires approval bypass for bots. The latter requires an admin to add the GitHub Actions bot to the bypass list, or the bumper to use a PAT with elevated scopes (`github-token` input on the action).

**Publish workflow refused with "No successful validation run (PR Check or CI) found".**
The commit on `development` wasn't validated by either workflow. Open a no-op PR against the same commit to trigger PR Check, then re-dispatch the publish workflow.
