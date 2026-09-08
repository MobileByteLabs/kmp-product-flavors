#!/usr/bin/env bash
#
# kgp-warning-budget.sh — ratchet gate on KGP source-set warnings.
#
# WHY THIS EXISTS
# ---------------
# KGP source-set warnings are not cosmetic. "Invalid Source Set Dependency Across Trees"
# describes a wiring shape KGP explicitly does not support, so today's warning is
# tomorrow's hard error; "Unused Kotlin Source Sets" usually means a directory a consumer
# believes is compiled silently is not (see FlavorMainSourceSetLivenessTest, which caught
# exactly that for the documented `src/{flavor}Main/` convention).
#
# Nothing in CI counted them, so they accumulated to 95 before anyone looked. This gate
# makes the count a first-class, ratcheting budget: it may fall freely, but any increase
# fails the build with the offending warnings printed.
#
# LOWERING THE BUDGET
# -------------------
# Fixed some? Run this script, take the reported actual, and lower KGP_WARNING_BUDGET
# below. Never raise it to make a build pass — that is the failure mode this gate exists
# to prevent. If a raise is genuinely justified, say why in the commit message.
#
# Usage:
#   scripts/ci/kgp-warning-budget.sh            # gate (used by CI)
#   scripts/ci/kgp-warning-budget.sh --report   # print the breakdown, never fail
set -euo pipefail

# Current ceiling. ZERO "Invalid Source Set Dependency Across Trees" warnings remain —
# that class is fully fixed and must never come back.
#
# The remaining 7 are all "Unused Kotlin Source Sets", and they are the deliberate,
# irreducible cost of the "share DIRECTORIES, never NODES" design:
#
#   commonPaid / commonProd / commonTablet / commonEnterprise / commonPrd
#   commonDebug / commonRelease / commonStaging / desktopDebug / desktopRelease
#
# Each of these NODES must keep existing because it is a public configuration surface —
# consumers write `sourceSets.commonPaid.dependencies { ... }` (pinned by
# PerVariantDependencyClasspathTest) and registering a build type is a contract that
# `common{BuildType}` exists (pinned by IntermediateBuildTypeSourceSetTest). But the node
# itself is no longer attached to a compilation: variant compilations consume its
# DIRECTORY via srcDir, with dependencies carried by `extendsFrom`. KGP therefore reports
# it as unused, which is literally true and harmless.
#
# Removing them would mean deleting a documented API. Re-attaching them to compilations
# would restore the cross-tree warnings this work removed. Gating them on on-disk content
# was tried and breaks IntermediateBuildTypeSourceSetTest.
KGP_WARNING_BUDGET=7

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

# `help` configures every project without compiling anything, which is where source-set
# wiring warnings are emitted. --no-configuration-cache is REQUIRED: a cached
# configuration replays no warnings, which would silently make this gate vacuous.
LOG="$(mktemp)"
trap 'rm -f "$LOG"' EXIT
./gradlew help --console=plain --no-configuration-cache >"$LOG" 2>&1 || {
    echo "::error::gradle configure failed — cannot measure the warning budget"
    tail -40 "$LOG"
    exit 1
}

ACTUAL=$(grep -c 'w: ⚠️' "$LOG" || true)

echo "── KGP source-set warnings ──────────────────────────────"
grep -oE 'w: ⚠️ [A-Za-z'"'"' ]+' "$LOG" | sort | uniq -c | sort -rn || true
echo "─────────────────────────────────────────────────────────"
echo "  actual: ${ACTUAL}   budget: ${KGP_WARNING_BUDGET}"

# ── Hard zero: cross-tree edges ──────────────────────────────────────────────
# "Invalid Source Set Dependency Across Trees" describes wiring KGP does not support, so
# it is a forward-compat hazard rather than noise. It was 83 warnings; it is now 0 and is
# gated at 0 independently of the overall budget, so it can never creep back under cover
# of the aggregate count. The rule that keeps it at zero: share source-set DIRECTORIES
# across variant trees, never the NODES.
CROSS_TREE=$(grep -c 'Invalid Source Set Dependency Across Trees' "$LOG" || true)
if [ "$CROSS_TREE" -gt 0 ]; then
    echo ""
    echo "::error::${CROSS_TREE} cross-tree source-set warning(s) reintroduced (must stay 0)."
    grep -A 8 'Invalid Source Set Dependency Across Trees' "$LOG" | head -40
    exit 1
fi

if [ "${1:-}" = "--report" ]; then
    exit 0
fi

if [ "$ACTUAL" -gt "$KGP_WARNING_BUDGET" ]; then
    echo ""
    echo "::error::KGP warning budget exceeded — ${ACTUAL} > ${KGP_WARNING_BUDGET}."
    echo "New source-set warnings were introduced. Fix the wiring rather than raising the"
    echo "budget: share source-set DIRECTORIES across variant trees, never the NODES"
    echo "(a node depended on from two compilations lands in two Source Set Trees)."
    echo ""
    echo "Offending warnings:"
    grep -A 6 'w: ⚠️' "$LOG" | head -60
    exit 1
fi

if [ "$ACTUAL" -lt "$KGP_WARNING_BUDGET" ]; then
    echo ""
    echo "✓ Below budget by $((KGP_WARNING_BUDGET - ACTUAL)). Ratchet it down:"
    echo "  set KGP_WARNING_BUDGET=${ACTUAL} in $0"
fi

echo "✓ KGP warning budget satisfied"
