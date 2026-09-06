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

# Current ceiling. Remaining known classes at the time of writing:
#   * 20 — commonMain / commonTest as shared roots. Inherent to matrix mode: multiple
#          compilations must share commonMain for BOTH sources and dependencies, and KGP's
#          tree model has no supported shape for that. Removing the edge was implemented
#          and measured — it trades 20 cross-tree warnings for 112 "Missing 'dependsOn'"
#          ones, because KGP already includes commonMain in the variant compilation.
#          Closing it needs intermediate source sets dropped for variant compilations,
#          which collides with expect/actual placement.
#   * 10 — unused per-target-flavor intermediates (e.g. iosDev, iosFree).
KGP_WARNING_BUDGET=30

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
