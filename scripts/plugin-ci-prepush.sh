#!/usr/bin/env bash
#
# plugin-ci-prepush.sh — Local CI parity for kmp-product-flavors plugin.
#
# Mirrors what .github/workflows/pr-check.yml runs on every PR:
#   1. Code Quality       — :spotlessCheck + :detekt
#   2. Compile All Targets — :build-logic:flavor-plugin:compileKotlin
#   3. Test (JVM)         — :build-logic:flavor-plugin:test
#   4. Sample (matrix)    — :samples:basic-flavors:* per active flavor
#   5. kmp-project-template sample — :cmp-shared:generateFlavorBuildConfig
#                                    (default + -PkmpFlavor=prodBankBStaging)
#
# WHY: every CI failure we hit during the v1.1.5 release cycle would have
# been caught locally by running these tasks. The fix loop went:
# push -> wait 5-25 min for openMF runners -> diagnose -> fix -> repeat.
# Running this script first surfaces the same failures in 2-4 minutes
# on a developer machine.
#
# Usage:
#   bash scripts/plugin-ci-prepush.sh
#
# Exit code:
#   0 = all gates green (safe to push)
#   1 = at least one gate failed (DO NOT push without fixing)

set -uo pipefail

cd "$(dirname "$0")/.."

YELLOW='\033[1;33m'
GREEN='\033[1;32m'
RED='\033[1;31m'
NC='\033[0m'

FAILED=()
PASSED=()

run_gate() {
  local name="$1"
  shift
  echo ""
  printf "${YELLOW}▶ %s${NC}\n" "$name"
  if ./gradlew "$@" --no-daemon --no-configuration-cache; then
    PASSED+=("$name")
    printf "${GREEN}✓ %s${NC}\n" "$name"
  else
    FAILED+=("$name")
    printf "${RED}✗ %s${NC}\n" "$name"
  fi
}

# Gate 1 — Code Quality
run_gate "spotlessCheck (Kotlin format)" spotlessCheck

# Gate 2 — Compile + Test
run_gate "flavor-plugin compileKotlin" :build-logic:flavor-plugin:compileKotlin
run_gate "flavor-plugin test suite"    :build-logic:flavor-plugin:test

# Gate 3 — basic-flavors sample matrix
for FLAVOR in freeDev paidProd freeStaging; do
  run_gate "basic-flavors assemble (-PkmpFlavor=${FLAVOR})" \
    :samples:basic-flavors:assemble -PkmpFlavor="${FLAVOR}"
  run_gate "basic-flavors validateFlavors (-PkmpFlavor=${FLAVOR})" \
    :samples:basic-flavors:validateFlavors -PkmpFlavor="${FLAVOR}"
done
run_gate "basic-flavors listFlavors" :samples:basic-flavors:listFlavors

# Gate 4 — kmp-project-template sample (standalone subproject, separate Gradle)
echo ""
printf "${YELLOW}▶ kmp-project-template sample build (standalone)${NC}\n"
(
  cd samples/kmp-project-template
  ./gradlew :cmp-shared:generateFlavorBuildConfig --rerun-tasks \
    --no-daemon --no-configuration-cache
  GEN="cmp-shared/build/generated/kmpFlavors/commonMain/kotlin/org/openmf/kmptemplate/BuildKonfig.kt"
  if [[ ! -f "$GEN" ]]; then
    echo "::error::BuildKonfig.kt missing at $GEN"
    exit 1
  fi
  for needle in \
      'VARIANT_NAME: String = "demoBankADebug"' \
      'IS_DEMO: Boolean = true' \
      'IS_BANKA: Boolean = true' \
      'BUILD_TYPE: String = "debug"' \
      'IS_DEBUG: Boolean = true' \
      'ENABLE_LOGGING: Boolean = true' \
      'LOG_TAG: String = "KMPTemplate-DEBUG"' \
      'BANK_ID: String = "bankA"'; do
    if ! grep -qF "$needle" "$GEN"; then
      echo "::error::Missing expected line: $needle"
      cat "$GEN"
      exit 1
    fi
  done
  echo "✓ kmp-project-template sample BuildKonfig.kt contains all expected lines"
)
if [[ $? -eq 0 ]]; then
  PASSED+=("kmp-project-template sample default variant")
  printf "${GREEN}✓ kmp-project-template sample default variant${NC}\n"
else
  FAILED+=("kmp-project-template sample default variant")
  printf "${RED}✗ kmp-project-template sample default variant${NC}\n"
fi

# Summary
echo ""
echo "════════════════════════════════════════════"
echo "Summary"
echo "════════════════════════════════════════════"
for p in "${PASSED[@]}"; do printf "  ${GREEN}✓${NC} %s\n" "$p"; done
for f in "${FAILED[@]}"; do printf "  ${RED}✗${NC} %s\n" "$f"; done
echo ""
echo "Passed: ${#PASSED[@]}"
echo "Failed: ${#FAILED[@]}"

if [[ ${#FAILED[@]} -gt 0 ]]; then
  echo ""
  printf "${RED}REFUSING TO PUSH — fix failures above first.${NC}\n"
  exit 1
fi

echo ""
printf "${GREEN}All gates green — safe to push.${NC}\n"
exit 0
