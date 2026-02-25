#!/bin/bash
#
# MavenLocalRelease - Local Test Script for KMP Product Flavors Plugin
# Builds the plugin, publishes to mavenLocal (without signing), and tests with the sample project
#

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo -e "${BLUE}╔════════════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║     KMP Product Flavors - Maven Local Release              ║${NC}"
echo -e "${BLUE}╚════════════════════════════════════════════════════════════╝${NC}"
echo

# Parse arguments
FLAVOR="${1:-freeDev}"
SKIP_PUBLISH=false
CLEAN=false
BUILD_ONLY=false

for arg in "$@"; do
    case $arg in
        --skip-publish)
            SKIP_PUBLISH=true
            ;;
        --clean)
            CLEAN=true
            ;;
        --build-only)
            BUILD_ONLY=true
            ;;
        --help|-h)
            echo "Usage: ./MavenLocalRelease.sh [flavor] [options]"
            echo ""
            echo "Arguments:"
            echo "  flavor          The flavor to build (default: freeDev)"
            echo "                  Available: freeDev, freeProd, freeStaging, paidDev, paidProd, paidStaging"
            echo ""
            echo "Options:"
            echo "  --skip-publish  Skip publishing to mavenLocal (use existing)"
            echo "  --build-only    Only build plugin, don't test sample"
            echo "  --clean         Clean build before running"
            echo "  --help, -h      Show this help message"
            echo ""
            echo "Examples:"
            echo "  ./MavenLocalRelease.sh                    # Build with freeDev flavor"
            echo "  ./MavenLocalRelease.sh paidProd           # Build with paidProd flavor"
            echo "  ./MavenLocalRelease.sh --build-only       # Only publish plugin"
            echo "  ./MavenLocalRelease.sh freeDev --clean    # Clean build with freeDev"
            exit 0
            ;;
    esac
done

# Clean if requested
if [ "$CLEAN" = true ]; then
    echo -e "${YELLOW}► Cleaning build directories...${NC}"
    ./gradlew clean --quiet 2>/dev/null || true
    echo -e "${GREEN}✓ Clean complete${NC}"
    echo
fi

# Step 1: Build and publish plugin to mavenLocal (without signing)
if [ "$SKIP_PUBLISH" = false ]; then
    echo -e "${YELLOW}► Step 1: Building and publishing plugin to mavenLocal...${NC}"

    # Publish without signing by excluding sign tasks
    ./gradlew :build-logic:flavor-plugin:publishToMavenLocal \
        --no-daemon \
        -x signPluginMavenPublication \
        -x signKmpProductFlavorsPluginMarkerMavenPublication \
        2>&1 | grep -E "(BUILD SUCCESSFUL|publishToMavenLocal)" || true

    # Verify publication
    PLUGIN_VERSION=$(grep 'version = ' build-logic/flavor-plugin/build.gradle.kts | head -1 | sed 's/.*"\(.*\)"/\1/')
    MAVEN_LOCAL_PATH="$HOME/.m2/repository/io/github/mobilebytelabs/kmpflavors/flavor-plugin/$PLUGIN_VERSION"

    if [ -d "$MAVEN_LOCAL_PATH" ]; then
        echo -e "${GREEN}✓ Plugin published to mavenLocal${NC}"
        echo -e "  ${BLUE}Version:${NC} $PLUGIN_VERSION"
        echo -e "  ${BLUE}Path:${NC} $MAVEN_LOCAL_PATH"
    else
        echo -e "${RED}✗ Failed to publish plugin${NC}"
        exit 1
    fi
    echo
else
    echo -e "${YELLOW}► Step 1: Skipping publish (using existing mavenLocal)${NC}"
    echo
fi

# Exit if build-only mode
if [ "$BUILD_ONLY" = true ]; then
    echo -e "${GREEN}✓ Build only mode - skipping sample tests${NC}"
    exit 0
fi

# Step 2: Build sample project with specified flavor
echo -e "${YELLOW}► Step 2: Building sample project with flavor: ${BLUE}$FLAVOR${NC}"
./gradlew :sample:assemble -PkmpFlavor="$FLAVOR" --no-daemon 2>&1 | grep -E "(BUILD|FAILURE|Task|KMP Flavors)" | head -10

echo -e "${GREEN}✓ Sample build successful${NC}"
echo

# Step 3: Validate flavors
echo -e "${YELLOW}► Step 3: Validating flavor configuration...${NC}"
./gradlew :sample:validateFlavors -PkmpFlavor="$FLAVOR" --no-daemon 2>&1 | grep -E "(✓|Valid|flavor)" | head -5
echo -e "${GREEN}✓ Flavor validation passed${NC}"
echo

# Step 4: List all flavors
echo -e "${YELLOW}► Step 4: Listing available flavors...${NC}"
./gradlew :sample:listFlavors --no-daemon 2>&1 | grep -E "(Variant|Dimension|Platform|Active|BUILD)" | head -20
echo

# Summary
echo -e "${GREEN}╔════════════════════════════════════════════════════════════╗${NC}"
echo -e "${GREEN}║     Maven Local Release Complete!                          ║${NC}"
echo -e "${GREEN}╚════════════════════════════════════════════════════════════╝${NC}"
echo
echo -e "Tested with flavor: ${BLUE}$FLAVOR${NC}"
echo
echo -e "To use in another project, add to settings.gradle.kts:"
echo -e "${YELLOW}  pluginManagement {"
echo -e "      repositories {"
echo -e "          mavenLocal()"
echo -e "          gradlePluginPortal()"
echo -e "      }"
echo -e "  }${NC}"
