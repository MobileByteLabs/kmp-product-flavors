#!/bin/bash

# =============================================================================
# CI Pre-push Script for KMP Product Flavors Plugin
# =============================================================================
# Runs all checks locally before pushing to ensure CI will pass.
# This mirrors the exact checks in .github/workflows/build.yml
#
# Usage:
#   ./ci-prepush.sh          # Run all checks
#   ./ci-prepush.sh --fix    # Auto-fix formatting issues before checking
#   ./ci-prepush.sh --quick  # Skip sample builds (faster, less thorough)
#
# =============================================================================

set -e  # Exit on first error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Parse arguments
FIX_MODE=false
QUICK_MODE=false
for arg in "$@"; do
    case $arg in
        --fix)
            FIX_MODE=true
            ;;
        --quick)
            QUICK_MODE=true
            ;;
    esac
done

# Check if gradlew exists
if [ ! -f "./gradlew" ]; then
    echo -e "${RED}Error: gradlew not found. Run from project root.${NC}"
    exit 1
fi

echo -e "${BLUE}"
echo "╔═══════════════════════════════════════════════════════════════════╗"
echo "║           KMP Product Flavors - Pre-push CI Checks                ║"
echo "╚═══════════════════════════════════════════════════════════════════╝"
echo -e "${NC}"

failed_tasks=()
successful_tasks=()
skipped_tasks=()

run_task() {
    local name="$1"
    local command="$2"
    local skip="${3:-false}"

    echo ""
    echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${BLUE}▶ $name${NC}"
    echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"

    if [ "$skip" = "true" ]; then
        echo -e "${YELLOW}⏭ Skipped (--quick mode)${NC}"
        skipped_tasks+=("$name")
        return
    fi

    if eval "$command"; then
        echo -e "${GREEN}✓ $name passed${NC}"
        successful_tasks+=("$name")
    else
        echo -e "${RED}✗ $name failed${NC}"
        failed_tasks+=("$name")
    fi
}

# =============================================================================
# STEP 1: Code Formatting (Spotless)
# Mirrors: .github/workflows/build.yml - spotlessCheck is part of code quality
# =============================================================================
if [ "$FIX_MODE" = true ]; then
    run_task "Spotless Apply (auto-fix formatting)" \
        "./gradlew spotlessApply --no-daemon"
fi

run_task "Spotless Check (code formatting)" \
    "./gradlew spotlessCheck --no-daemon"

# =============================================================================
# STEP 2: Static Analysis (Detekt)
# Mirrors: Root build.gradle.kts detekt configuration
# =============================================================================
run_task "Detekt (static analysis)" \
    "./gradlew detekt --no-daemon"

# =============================================================================
# STEP 3: Build Plugin
# Mirrors: .github/workflows/build.yml - "Build Plugin" job
# =============================================================================
run_task "Build Plugin" \
    "./gradlew :build-logic:flavor-plugin:assemble --no-daemon"

# =============================================================================
# STEP 4: Build Samples with Different Flavors
# Mirrors: .github/workflows/build.yml - "Build Sample" matrix job
# =============================================================================
run_task "Build Sample (freeDev)" \
    "./gradlew :samples:basic-flavors:assemble -PkmpFlavor=freeDev --no-daemon" \
    "$QUICK_MODE"

run_task "Build Sample (paidProd)" \
    "./gradlew :samples:basic-flavors:assemble -PkmpFlavor=paidProd --no-daemon" \
    "$QUICK_MODE"

# =============================================================================
# STEP 5: Validate Flavors
# Mirrors: .github/workflows/build.yml - "Validate Flavors" step
# =============================================================================
run_task "Validate Flavors (freeDev)" \
    "./gradlew :samples:basic-flavors:validateFlavors -PkmpFlavor=freeDev --no-daemon" \
    "$QUICK_MODE"

run_task "Validate Flavors (paidProd)" \
    "./gradlew :samples:basic-flavors:validateFlavors -PkmpFlavor=paidProd --no-daemon" \
    "$QUICK_MODE"

# =============================================================================
# STEP 6: List Flavors (verification)
# Mirrors: .github/workflows/build.yml - "List Flavors" step
# =============================================================================
run_task "List Flavors" \
    "./gradlew :samples:basic-flavors:listFlavors --no-daemon" \
    "$QUICK_MODE"

# =============================================================================
# STEP 7: Verify Plugin JAR
# Mirrors: .github/workflows/build.yml - "Validate Plugin" job
# =============================================================================
run_task "Build Plugin JAR" \
    "./gradlew :build-logic:flavor-plugin:jar --no-daemon"

# Verify JAR exists with correct version
echo ""
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${BLUE}▶ Verify Plugin Artifacts${NC}"
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"

VERSION=$(grep 'kmpProductFlavors' gradle/libs.versions.toml | head -1 | sed 's/.*= *"\(.*\)"/\1/')
echo "Plugin version: $VERSION"

if ls build-logic/flavor-plugin/build/libs/*.jar 1> /dev/null 2>&1; then
    echo -e "${GREEN}✓ Plugin JAR found:${NC}"
    ls -la build-logic/flavor-plugin/build/libs/*.jar
    successful_tasks+=("Verify Plugin Artifacts")
else
    echo -e "${RED}✗ Plugin JAR not found${NC}"
    failed_tasks+=("Verify Plugin Artifacts")
fi

# =============================================================================
# SUMMARY
# =============================================================================
echo ""
echo -e "${BLUE}╔═══════════════════════════════════════════════════════════════════╗"
echo -e "║                           SUMMARY                                 ║"
echo -e "╚═══════════════════════════════════════════════════════════════════╝${NC}"

echo ""
echo -e "${GREEN}Passed (${#successful_tasks[@]}):${NC}"
for task in "${successful_tasks[@]}"; do
    echo -e "  ${GREEN}✓${NC} $task"
done

if [ ${#skipped_tasks[@]} -gt 0 ]; then
    echo ""
    echo -e "${YELLOW}Skipped (${#skipped_tasks[@]}):${NC}"
    for task in "${skipped_tasks[@]}"; do
        echo -e "  ${YELLOW}⏭${NC} $task"
    done
fi

if [ ${#failed_tasks[@]} -eq 0 ]; then
    echo ""
    echo -e "${GREEN}╔═══════════════════════════════════════════════════════════════════╗"
    echo -e "║  ✓ All checks passed! You're good to push.                       ║"
    echo -e "╚═══════════════════════════════════════════════════════════════════╝${NC}"
    exit 0
else
    echo ""
    echo -e "${RED}Failed (${#failed_tasks[@]}):${NC}"
    for task in "${failed_tasks[@]}"; do
        echo -e "  ${RED}✗${NC} $task"
    done
    echo ""
    echo -e "${RED}╔═══════════════════════════════════════════════════════════════════╗"
    echo -e "║  ✗ Some checks failed. Please fix before pushing.                ║"
    echo -e "╚═══════════════════════════════════════════════════════════════════╝${NC}"
    echo ""
    echo -e "${YELLOW}Tips:${NC}"
    echo "  • Run './ci-prepush.sh --fix' to auto-fix formatting issues"
    echo "  • Run './ci-prepush.sh --quick' to skip sample builds"
    echo "  • Check the output above for specific error messages"
    exit 1
fi
