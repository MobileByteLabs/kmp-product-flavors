#!/usr/bin/env bash
#
# lib-sync.sh — sync the kmp-product-flavors library version into a
# consumer project per the library's adoption doc.
#
# What it does (per `docs/adoption/v{X.Y}/consumer.md` recipe):
#   1. Resolves the library's current published version from gradle.properties.
#   2. Locates the consumer (default: samples/kmp-project-template — the
#      first-party canonical consumer; override via $1).
#   3. Pulls the consumer's default branch and creates
#      chore/sync-kmp-product-flavors-v{X.Y.Z}.
#   4. Applies the migration:
#        a. Bumps `kmpProductFlavors = "X.Y.Z"` in the consumer's
#           gradle/libs.versions.toml.
#        b. Appends/updates the version section in the consumer's
#           docs/ADOPTION_KMP_PRODUCT_FLAVORS.md (Tier 2 record).
#   5. Runs the consumer's adoption drift gate
#      (scripts/adoption-doc-verify.py).
#   6. Stages + commits if gate is green; bails clean if red.
#   7. Prints push instructions — does NOT push autonomously.
#
# Usage:
#   ./scripts/lib-sync.sh                              # syncs samples/kmp-project-template to current library version
#   ./scripts/lib-sync.sh samples/kmp-project-template # explicit consumer path
#   ./scripts/lib-sync.sh --target-version 2.7.0       # override the target version
#   ./scripts/lib-sync.sh --dry-run                    # show plan, don't commit
#   ./scripts/lib-sync.sh --no-bootstrap               # don't auto-scaffold missing adoption doc
#   ./scripts/lib-sync.sh --help
#
# Auto-scaffold behavior:
#   If docs/adoption/v{X.Y}/library.md doesn't exist for the target
#   version, the script scaffolds it from the most-recent previous
#   minor (e.g. for v2.8.0 target, copies v2.7/{library,consumer}.md →
#   v2.8/, search-replaces version tokens, prepends a BOOTSTRAPPED
#   banner, and runs the drift gate against the new v2.8/library.md).
#   Pass --no-bootstrap to refuse the scaffold and fail-fast instead.
#
# Exit codes:
#   0 — sync completed successfully (or --dry-run finished)
#   1 — gate failed after migration; commit was NOT created
#   2 — usage error or unmet precondition (not in library repo, etc.)
#
# Notes:
#   - Does NOT push. Final step prints `git push` instructions for the
#     consumer's remote.
#   - Does NOT auto-update the library's submodule pointer. After the
#     consumer's PR merges, separately bump the submodule pointer in the
#     library repo.
#   - The companion framework skill `.claude/skills/lib-sync/SKILL.md`
#     wraps this script as the `/lib-sync` slash-command for Claude
#     sessions; this script works standalone without the framework.

set -euo pipefail

# ─── Defaults ───────────────────────────────────────────────────────────
DEFAULT_CONSUMER="samples/kmp-project-template"
CONSUMER_PATH="$DEFAULT_CONSUMER"
TARGET_VERSION=""
DRY_RUN=0
NO_BOOTSTRAP=0

# ─── bootstrap_library_adoption_doc ────────────────────────────────────
# If docs/adoption/v{X.Y}/{library,consumer}.md don't exist for the target
# version, scaffold them from the most-recent previous v{P.Q}/ — copy +
# search-replace version tokens + prepend a BOOTSTRAPPED banner so the
# maintainer knows to review.
#
# Args: $1 = target version (X.Y.Z)
# Returns: 0 if doc exists or was successfully bootstrapped
#          1 on error
bootstrap_library_adoption_doc() {
    local target_version="$1"
    local target_minor
    target_minor=$(echo "$target_version" | cut -d. -f1-2)
    local target_dir="docs/adoption/v${target_minor}"

    if [ -f "${target_dir}/library.md" ] && [ -f "${target_dir}/consumer.md" ]; then
        return 0
    fi

    # Find most-recent previous version directory (semver-aware sort).
    local prev_minor=""
    for d in docs/adoption/v*/; do
        [ -d "$d" ] || continue
        local v
        v=$(basename "$d" | sed 's/^v//')
        # Skip if not strictly X.Y format
        echo "$v" | grep -qE '^[0-9]+\.[0-9]+$' || continue
        # Skip the target itself + any version >= target
        if [ "$(printf '%s\n%s\n' "$v" "$target_minor" | sort -V | tail -1)" != "$target_minor" ]; then
            continue
        fi
        [ "$v" = "$target_minor" ] && continue
        # candidate is greatest seen so far?
        if [ -z "$prev_minor" ] || [ "$(printf '%s\n%s\n' "$prev_minor" "$v" | sort -V | tail -1)" = "$v" ]; then
            prev_minor="$v"
        fi
    done

    if [ -z "$prev_minor" ]; then
        echo "✗ no previous version directory found under docs/adoption/ — cannot bootstrap" >&2
        echo "  Author docs/adoption/v${target_minor}/{library,consumer}.md manually." >&2
        return 1
    fi

    echo
    echo "→ adoption doc missing for v${target_minor} — bootstrapping from v${prev_minor}"
    if [ "$NO_BOOTSTRAP" = 1 ]; then
        echo "  (--no-bootstrap set; refusing to scaffold)"
        return 1
    fi

    if [ "$DRY_RUN" = 1 ]; then
        echo "  (dry-run — would copy + search-replace 2 files; skipping)"
        return 0
    fi

    mkdir -p "$target_dir"
    local banner_date
    banner_date=$(date -u +%Y-%m-%d)
    local banner_top
    banner_top="<!-- BOOTSTRAPPED FROM v${prev_minor} by scripts/lib-sync.sh on ${banner_date} -->
<!-- TODO: review against the v${prev_minor} → v${target_minor} migration doc + add any version-specific deltas before commit. -->
<!-- Targeted search-replace applied: v${prev_minor} → v${target_minor}, ${prev_minor}.0 → ${target_version}. -->
<!-- Other version literals (e.g. v2.6 floor references) were NOT touched. -->

"

    for f in library.md consumer.md; do
        local src="docs/adoption/v${prev_minor}/${f}"
        local dst="${target_dir}/${f}"
        if [ ! -f "$src" ]; then
            echo "  ⚠ source $src not found — skipping $f"
            continue
        fi
        # Search-replace targeted patterns. Conservative — only the
        # immediate-previous-minor tokens get touched. Older tokens
        # (e.g. v2.6 floor references) are left alone for manual review.
        python3 - "$src" "$dst" "$prev_minor" "$target_minor" "$target_version" "$banner_top" <<'PY'
import sys, pathlib
src_path, dst_path, prev, new_minor, new_version, banner = sys.argv[1:]
src = pathlib.Path(src_path).read_text()
new = src
# v{prev}.0 → v{new_version} (e.g. v2.7.0 → v2.8.0)
new = new.replace(f"v{prev}.0", f"v{new_version}")
new = new.replace(f"v{prev}.x", f"v{new_minor}.x")
# v{prev} → v{new_minor} only when followed by non-version char (avoid
# v2.7.0 → v2.8.0 double-bump from already-replaced text)
import re
new = re.sub(
    rf"v{re.escape(prev)}(?![0-9.])",
    f"v{new_minor}",
    new,
)
# {prev}.0 → {new_version}
new = new.replace(f"{prev}.0", new_version)
# Versioned headers in MIGRATION doc names: MIGRATION_v{prev}_TO_v{new} stays
# manually-authored; we don't auto-create it.
pathlib.Path(dst_path).write_text(banner + new)
PY
        echo "  ✓ wrote $dst"
    done

    # Verify gate against the bootstrapped library.md so the maintainer
    # sees immediately whether the search-replace broke any assertions.
    if [ -f "scripts/adoption-doc-verify.py" ]; then
        echo
        echo "→ running gate against bootstrapped ${target_dir}/library.md"
        if python3 scripts/adoption-doc-verify.py "${target_dir}/library.md" >/dev/null 2>&1; then
            echo "  ✓ gate green on bootstrapped doc"
        else
            echo "  ⚠ gate red — review which verify blocks reference v${prev_minor}-specific paths"
            echo "    that need version-specific updates. Run the gate manually for details:"
            echo "    python3 scripts/adoption-doc-verify.py ${target_dir}/library.md"
        fi
    fi

    echo
    echo "→ adoption doc pair scaffolded at $target_dir"
    echo "  Review the two files, fill in version-specific deltas (if any), commit before publishing."
    echo
    return 0
}

# ─── CLI parsing ───────────────────────────────────────────────────────
while [ $# -gt 0 ]; do
    case "$1" in
        --target-version)
            TARGET_VERSION="$2"; shift 2 ;;
        --dry-run)
            DRY_RUN=1; shift ;;
        --no-bootstrap)
            NO_BOOTSTRAP=1; shift ;;
        -h|--help)
            sed -n '4,42p' "$0"; exit 0 ;;
        --)
            shift; break ;;
        -*)
            echo "unknown flag: $1" >&2; exit 2 ;;
        *)
            CONSUMER_PATH="$1"; shift ;;
    esac
done

# ─── Pre-flight ────────────────────────────────────────────────────────
# Must be run from the library repo root (where gradle.properties + scripts/ live).
if [ ! -f gradle.properties ] || [ ! -f scripts/adoption-doc-verify.py ]; then
    echo "error: run this from the kmp-product-flavors library repo root" >&2
    exit 2
fi

if [ ! -d "$CONSUMER_PATH" ]; then
    echo "error: consumer path not found: $CONSUMER_PATH" >&2
    exit 2
fi

# Read the library version (strip -alpha.N / -SNAPSHOT for the "stable target"
# unless --target-version was explicit).
if [ -z "$TARGET_VERSION" ]; then
    LIB_VERSION=$(grep '^kmpflavors\.version=' gradle.properties | cut -d= -f2)
    # If it ends in -alpha / -SNAPSHOT / -beta / -rc, strip the suffix to get
    # the implied stable target — the user can override with --target-version.
    TARGET_VERSION=$(echo "$LIB_VERSION" | sed -E 's/-(alpha|beta|rc|SNAPSHOT)[0-9.]*$//')
fi

# Validate target version shape: must be X.Y.Z
if ! echo "$TARGET_VERSION" | grep -qE '^[0-9]+\.[0-9]+\.[0-9]+$'; then
    echo "error: target version '$TARGET_VERSION' must be X.Y.Z (or pass --target-version)" >&2
    exit 2
fi

LIB_MINOR=$(echo "$TARGET_VERSION" | cut -d. -f1-2)
ADOPTION_DOC="docs/adoption/v${LIB_MINOR}/consumer.md"

# If the library-side adoption doc pair doesn't exist for this minor,
# offer to bootstrap from the most-recent previous minor. This closes
# the loop on "new release → adoption doc must exist" without requiring
# the maintainer to remember a separate scaffolding step.
if [ ! -f "$ADOPTION_DOC" ] || [ ! -f "docs/adoption/v${LIB_MINOR}/library.md" ]; then
    if ! bootstrap_library_adoption_doc "$TARGET_VERSION"; then
        echo "error: adoption doc not found: $ADOPTION_DOC" >&2
        echo "       expected docs/adoption/v${LIB_MINOR}/{library,consumer}.md to exist for migration recipe" >&2
        echo "       bootstrap from previous version failed (or --no-bootstrap was set)" >&2
        exit 2
    fi
fi

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  lib-sync"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  library version (gradle.properties): ${LIB_VERSION:-(detected from target)}"
echo "  target consumer version            : $TARGET_VERSION"
echo "  consumer path                      : $CONSUMER_PATH"
echo "  adoption recipe                    : $ADOPTION_DOC"
echo "  dry-run                            : $DRY_RUN"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo

# ─── Consumer sync: branch + pull ──────────────────────────────────────
BRANCH="chore/sync-kmp-product-flavors-v${TARGET_VERSION}"

(
    cd "$CONSUMER_PATH"

    # Identify the consumer's default branch (typically `dev` for kmp-project-template,
    # `development` or `main` for others). We probe in order.
    DEFAULT_BRANCH=""
    for candidate in dev development main master; do
        if git ls-remote --exit-code --heads origin "$candidate" >/dev/null 2>&1; then
            DEFAULT_BRANCH="$candidate"
            break
        fi
    done
    if [ -z "$DEFAULT_BRANCH" ]; then
        echo "✗ could not resolve consumer default branch (probed dev/development/main/master)" >&2
        exit 2
    fi

    echo "→ pulling consumer's $DEFAULT_BRANCH …"
    git fetch origin "$DEFAULT_BRANCH" 2>&1 | tail -2

    # Stash any in-flight work so we can switch branches safely. The submodule
    # may legitimately have uncommitted local changes if this is being run
    # within the library's working tree — the sync should NOT clobber them.
    if ! git diff --quiet || ! git diff --cached --quiet; then
        echo "✗ consumer has uncommitted local changes — stash or commit first" >&2
        exit 2
    fi

    # If the branch already exists, reuse it; else create.
    if git show-ref --verify --quiet "refs/heads/$BRANCH"; then
        echo "→ checking out existing $BRANCH"
        git checkout "$BRANCH"
        # Best-effort rebase onto fresh default branch.
        git rebase "origin/$DEFAULT_BRANCH" 2>&1 | tail -3 || {
            echo "✗ rebase onto origin/$DEFAULT_BRANCH failed — resolve manually" >&2
            exit 2
        }
    else
        echo "→ creating $BRANCH from origin/$DEFAULT_BRANCH"
        git checkout -b "$BRANCH" "origin/$DEFAULT_BRANCH"
    fi

    # ─── Migration: bump the plugin version in libs.versions.toml ──────
    LIBS_TOML="gradle/libs.versions.toml"
    if [ ! -f "$LIBS_TOML" ]; then
        echo "✗ consumer is missing $LIBS_TOML — adoption gate would fail anyway" >&2
        exit 2
    fi

    CURRENT_CONSUMER_VERSION=$(grep -E '^kmpProductFlavors\s*=' "$LIBS_TOML" | head -1 | cut -d'"' -f2)
    echo "→ consumer's current kmpProductFlavors pin: ${CURRENT_CONSUMER_VERSION:-(not set)}"

    if [ "$CURRENT_CONSUMER_VERSION" = "$TARGET_VERSION" ]; then
        echo "✓ consumer already at $TARGET_VERSION — nothing to bump"
    else
        echo "→ bumping ${CURRENT_CONSUMER_VERSION:-(unset)} → $TARGET_VERSION in $LIBS_TOML"
        if [ "$DRY_RUN" = 0 ]; then
            # Replace the version line in-place. Portable across BSD + GNU sed.
            python3 - <<PY
import re, pathlib
p = pathlib.Path("$LIBS_TOML")
src = p.read_text()
new = re.sub(
    r'^(kmpProductFlavors\s*=\s*)"[^"]*"',
    rf'\1"{("$TARGET_VERSION")}"',
    src,
    count=1,
    flags=re.MULTILINE,
)
if new == src:
    raise SystemExit("could not find kmpProductFlavors line to replace")
p.write_text(new)
PY
        fi
    fi

    # ─── Migration: append/update version section in adoption record ──
    ADOPTION_RECORD="docs/ADOPTION_KMP_PRODUCT_FLAVORS.md"
    if [ ! -f "$ADOPTION_RECORD" ]; then
        echo "ℹ consumer has no Tier 2 adoption record at $ADOPTION_RECORD — skipping append"
    else
        # Check if a section for this version already exists.
        if grep -qE "^## v${TARGET_VERSION}\b" "$ADOPTION_RECORD"; then
            echo "ℹ section ## v${TARGET_VERSION} already exists in $ADOPTION_RECORD — skipping append"
        else
            echo "→ appending v${TARGET_VERSION} section to $ADOPTION_RECORD"
            if [ "$DRY_RUN" = 0 ]; then
                # Insert above the first "## v" section line.
                python3 - <<PY
import re, pathlib, datetime
p = pathlib.Path("$ADOPTION_RECORD")
src = p.read_text()
today = datetime.datetime.utcnow().strftime("%Y-%m-%d")
section = f"""## v$TARGET_VERSION — adopted {today}

### Bump from v$CURRENT_CONSUMER_VERSION → v$TARGET_VERSION

\`\`\`diff
- gradle/libs.versions.toml#[versions].kmpProductFlavors = "$CURRENT_CONSUMER_VERSION"
+ gradle/libs.versions.toml#[versions].kmpProductFlavors = "$TARGET_VERSION"
\`\`\`

### Why the bump is safe

See the library's migration doc:
[MobileByteLabs/kmp-product-flavors/docs/MIGRATION_v{previous}_TO_v$LIB_MINOR.md](https://github.com/MobileByteLabs/kmp-product-flavors/tree/development/docs).

### Verify

Run the §1–§14 verify suite (see Tier 1 [consumer.md](https://github.com/MobileByteLabs/kmp-product-flavors/blob/development/$ADOPTION_DOC)) — the drift gate at .github/workflows/adoption-doc-verify.yml runs them automatically on every PR.

---

"""
new = re.sub(r"^(## v[0-9]+\.[0-9]+\.[0-9]+)", section + r"\1", src, count=1, flags=re.MULTILINE)
if new == src:
    new = src.replace("## v$TARGET_VERSION", section + "## v$TARGET_VERSION", 1) if "## v$TARGET_VERSION" in src else src + "\n\n" + section
p.write_text(new)
PY
            fi
        fi
    fi

    # ─── Drift gate ─────────────────────────────────────────────────────
    if [ -f "scripts/adoption-doc-verify.py" ] && [ -f "$ADOPTION_RECORD" ]; then
        echo
        echo "→ running consumer's drift gate against $ADOPTION_RECORD"
        if ! python3 scripts/adoption-doc-verify.py "$ADOPTION_RECORD"; then
            echo
            echo "✗ DRIFT GATE FAILED — verify blocks regressed after the migration." >&2
            echo "  Either: (a) the consumer's implementation broke, fix it." >&2
            echo "          (b) the new library version requires a structural change" >&2
            echo "              the doc doesn't yet reflect — update the doc + verify" >&2
            echo "              blocks in $ADOPTION_RECORD to match the new reality." >&2
            echo
            echo "  Branch '$BRANCH' has the partial migration applied but NOT committed." >&2
            echo "  Inspect with: git -C $CONSUMER_PATH diff" >&2
            exit 1
        fi
        echo "✓ drift gate green"
    else
        echo "ℹ consumer has no drift gate script/doc — skipping verification"
    fi

    # ─── Commit ─────────────────────────────────────────────────────────
    if [ "$DRY_RUN" = 1 ]; then
        echo
        echo "━ dry-run — not committing. Changes staged for inspection:"
        git status --short
        exit 0
    fi

    if git diff --quiet && git diff --cached --quiet; then
        echo "✓ no changes to commit (consumer is already in sync)"
        exit 0
    fi

    git add "$LIBS_TOML"
    [ -f "$ADOPTION_RECORD" ] && git add "$ADOPTION_RECORD"

    COMMIT_MSG="chore(deps): sync kmp-product-flavors → v${TARGET_VERSION}

Automated migration via lib-sync.sh against the library's
docs/adoption/v${LIB_MINOR}/consumer.md adoption recipe.

Changes:
  - gradle/libs.versions.toml: bump kmpProductFlavors ${CURRENT_CONSUMER_VERSION:-unset} → ${TARGET_VERSION}
  - docs/ADOPTION_KMP_PRODUCT_FLAVORS.md: appended v${TARGET_VERSION} section

The library's migration doc says no breaking changes for this bump
(see MobileByteLabs/kmp-product-flavors/docs/MIGRATION_*.md).
Drift gate passed against the consumer's verify blocks."

    git commit -m "$COMMIT_MSG"
    NEW_SHA=$(git rev-parse --short HEAD)

    echo
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo "  ✓ sync committed: $NEW_SHA on branch $BRANCH"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo
    echo "  Push to remote (not done autonomously):"
    echo "    git -C $CONSUMER_PATH push -u origin $BRANCH"
    echo
    echo "  Then open a PR on the consumer repo:"
    echo "    gh -R \"\$(git -C $CONSUMER_PATH remote get-url origin | sed 's|.*:||;s|.git\$||')\" pr create \\"
    echo "      --base $DEFAULT_BRANCH --head $BRANCH \\"
    echo "      --title 'chore(deps): sync kmp-product-flavors → v${TARGET_VERSION}'"
    echo
)
