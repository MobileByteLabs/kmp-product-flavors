#!/bin/bash
#
# Integration Script for kmp-project-template
#
# This script copies the convention plugin integration files to a kmp-project-template
# clone and updates the necessary build files.
#
# Usage:
#   ./install-to-kmp-project-template.sh /path/to/kmp-project-template
#
# Example:
#   ./install-to-kmp-project-template.sh ~/Projects/kmp-project-template
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
CONVENTION_PLUGIN_DIR="$SCRIPT_DIR/convention-plugin/src/main/kotlin"

# Target directory (from argument or current directory)
TARGET_DIR="${1:-}"

# Version of kmp-product-flavors
KMP_FLAVORS_VERSION="1.0.0"

echo -e "${BLUE}=== KMP Product Flavors Integration Script ===${NC}"
echo ""

# Validate target directory
if [ -z "$TARGET_DIR" ]; then
    echo -e "${RED}Error: Please specify the path to kmp-project-template${NC}"
    echo ""
    echo "Usage: $0 /path/to/kmp-project-template"
    exit 1
fi

if [ ! -d "$TARGET_DIR" ]; then
    echo -e "${RED}Error: Directory not found: $TARGET_DIR${NC}"
    exit 1
fi

if [ ! -d "$TARGET_DIR/build-logic/convention" ]; then
    echo -e "${RED}Error: Not a valid kmp-project-template directory (missing build-logic/convention)${NC}"
    exit 1
fi

echo -e "${GREEN}Target directory: $TARGET_DIR${NC}"
echo ""

# Create backup
BACKUP_DIR="$TARGET_DIR/.kmp-flavors-backup-$(date +%Y%m%d_%H%M%S)"
echo -e "${YELLOW}Creating backup at $BACKUP_DIR${NC}"
mkdir -p "$BACKUP_DIR"

# Backup existing files if they exist
if [ -f "$TARGET_DIR/build-logic/convention/src/main/kotlin/KMPFlavorsConventionPlugin.kt" ]; then
    cp "$TARGET_DIR/build-logic/convention/src/main/kotlin/KMPFlavorsConventionPlugin.kt" "$BACKUP_DIR/" 2>/dev/null || true
fi
if [ -f "$TARGET_DIR/build-logic/convention/src/main/kotlin/org/convention/KmpFlavors.kt" ]; then
    cp "$TARGET_DIR/build-logic/convention/src/main/kotlin/org/convention/KmpFlavors.kt" "$BACKUP_DIR/" 2>/dev/null || true
fi
if [ -f "$TARGET_DIR/build-logic/convention/src/main/kotlin/org/convention/KmpFlavorsBuildConfig.kt" ]; then
    cp "$TARGET_DIR/build-logic/convention/src/main/kotlin/org/convention/KmpFlavorsBuildConfig.kt" "$BACKUP_DIR/" 2>/dev/null || true
fi

echo ""
echo -e "${BLUE}Step 1: Copying convention plugin files...${NC}"

# Copy main plugin file
cp "$CONVENTION_PLUGIN_DIR/KMPFlavorsConventionPlugin.kt" \
   "$TARGET_DIR/build-logic/convention/src/main/kotlin/"
echo "  ✓ KMPFlavorsConventionPlugin.kt"

# Create org/convention directory if it doesn't exist
mkdir -p "$TARGET_DIR/build-logic/convention/src/main/kotlin/org/convention"

# Copy convention files
cp "$CONVENTION_PLUGIN_DIR/org/convention/KmpFlavors.kt" \
   "$TARGET_DIR/build-logic/convention/src/main/kotlin/org/convention/"
echo "  ✓ org/convention/KmpFlavors.kt"

cp "$CONVENTION_PLUGIN_DIR/org/convention/KmpFlavorsBuildConfig.kt" \
   "$TARGET_DIR/build-logic/convention/src/main/kotlin/org/convention/"
echo "  ✓ org/convention/KmpFlavorsBuildConfig.kt"

echo ""
echo -e "${BLUE}Step 2: Checking version catalog...${NC}"

VERSIONS_FILE="$TARGET_DIR/gradle/libs.versions.toml"

if ! grep -q "kmpProductFlavors" "$VERSIONS_FILE"; then
    echo -e "${YELLOW}Adding kmpProductFlavors to version catalog...${NC}"

    # Add version
    if grep -q "^\[versions\]" "$VERSIONS_FILE"; then
        # Add after [versions] section
        sed -i.bak '/^\[versions\]/a\
kmpProductFlavors = "'"$KMP_FLAVORS_VERSION"'"
' "$VERSIONS_FILE"
    fi

    # Add library
    if grep -q "^\[libraries\]" "$VERSIONS_FILE"; then
        sed -i.bak '/^\[libraries\]/a\
kmpProductFlavors-gradlePlugin = { module = "io.github.mobilebytelabs:kmp-product-flavors-gradle-plugin", version.ref = "kmpProductFlavors" }
' "$VERSIONS_FILE"
    fi

    # Add plugin
    if grep -q "^\[plugins\]" "$VERSIONS_FILE"; then
        sed -i.bak '/^\[plugins\]/a\
kmpProductFlavors = { id = "io.github.mobilebytelabs.kmp-product-flavors", version.ref = "kmpProductFlavors" }
' "$VERSIONS_FILE"
    fi

    # Clean up backup
    rm -f "$VERSIONS_FILE.bak"

    echo "  ✓ Added version catalog entries"
else
    echo "  ✓ Version catalog already contains kmpProductFlavors"
fi

echo ""
echo -e "${BLUE}Step 3: Checking build.gradle.kts dependencies...${NC}"

BUILD_LOGIC_FILE="$TARGET_DIR/build-logic/convention/build.gradle.kts"

if ! grep -q "kmpProductFlavors" "$BUILD_LOGIC_FILE"; then
    echo -e "${YELLOW}Note: Please add the following dependency to $BUILD_LOGIC_FILE:${NC}"
    echo ""
    echo "    compileOnly(libs.kmpProductFlavors.gradlePlugin)"
    echo ""
    echo -e "${YELLOW}And register the plugin:${NC}"
    echo ""
    echo '    register("kmpFlavors") {'
    echo '        id = "org.convention.kmp.flavors"'
    echo '        implementationClass = "KMPFlavorsConventionPlugin"'
    echo '    }'
else
    echo "  ✓ Dependencies already configured"
fi

echo ""
echo -e "${GREEN}=== Installation Complete ===${NC}"
echo ""
echo "Next steps:"
echo "1. Add dependency to build-logic/convention/build.gradle.kts:"
echo "   compileOnly(libs.kmpProductFlavors.gradlePlugin)"
echo ""
echo "2. Register the plugin in gradlePlugin { plugins { ... } }:"
echo '   register("kmpFlavors") {'
echo '       id = "org.convention.kmp.flavors"'
echo '       implementationClass = "KMPFlavorsConventionPlugin"'
echo '   }'
echo ""
echo "3. Update KMPLibraryConventionPlugin to apply the flavors plugin:"
echo '   pluginManager.apply("org.convention.kmp.flavors")'
echo ""
echo "4. Customize org/convention/KmpFlavors.kt for your project's flavors"
echo ""
echo -e "${BLUE}Backup created at: $BACKUP_DIR${NC}"
