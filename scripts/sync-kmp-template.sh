#!/bin/bash
#
# Sync KMP Template Integration
# Pulls latest changes from openMF/kmp-project-template
#

set -e

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(dirname "$SCRIPT_DIR")"
cd "$ROOT_DIR"

REMOTE_URL="https://github.com/openMF/kmp-project-template.git"
BRANCH="dev"
PREFIX="samples/kmp-template-integration"

echo -e "${BLUE}╔════════════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║     Sync KMP Template Integration                          ║${NC}"
echo -e "${BLUE}╚════════════════════════════════════════════════════════════╝${NC}"
echo

# Check for uncommitted changes
if ! git diff --quiet || ! git diff --staged --quiet; then
    echo -e "${RED}✗ Error: You have uncommitted changes.${NC}"
    echo "Please commit or stash your changes before syncing."
    exit 1
fi

echo -e "${YELLOW}► Fetching latest from ${REMOTE_URL} (${BRANCH})...${NC}"
echo

# Pull the subtree
git subtree pull --prefix="$PREFIX" "$REMOTE_URL" "$BRANCH" --squash -m "chore: sync kmp-template-integration from upstream

Pulls latest changes from openMF/kmp-project-template ($BRANCH branch)
"

echo
echo -e "${GREEN}✓ Sync complete!${NC}"
echo
echo -e "To build the template sample:"
echo -e "  ${YELLOW}cd samples/kmp-template-integration${NC}"
echo -e "  ${YELLOW}./gradlew build${NC}"
