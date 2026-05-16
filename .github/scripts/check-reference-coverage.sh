#!/usr/bin/env bash
# Doc-consistency check G18 — every public property declared in
# KmpFlavorExtension.kt must have an entry in docs/REFERENCE.md.
#
# Prevents drift: when a new `kmpFlavors.xxx` property lands without a
# corresponding REFERENCE.md entry, this fails CI on the PR that
# introduced it. Counterpart of the K-Doc plugin-id check (G17).
#
# Allowlist: surface kinds intentionally excluded from REFERENCE.md (e.g.
# `internal` properties, container DSLs documented under a parent
# heading). Add a name + reason to the ALLOWLIST array below.

set -euo pipefail

EXTENSION_FILE="build-logic/flavor-plugin/src/main/kotlin/com/mobilebytelabs/kmpflavors/KmpFlavorExtension.kt"
REFERENCE_FILE="docs/REFERENCE.md"

if [ ! -f "$EXTENSION_FILE" ]; then
  echo "::error::expected extension file not found: $EXTENSION_FILE"
  exit 2
fi

if [ ! -f "$REFERENCE_FILE" ]; then
  echo "::error::expected reference file not found: $REFERENCE_FILE"
  exit 2
fi

# Names that don't need a REFERENCE.md entry (e.g. internal lists, container
# DSLs covered by the "Core flavor / build-type DSL" parent heading).
ALLOWLIST=(
  # No exclusions today. Add: "<propName>  # reason"
)

# 1. Extract property names from the extension source.
#    Pattern: `abstract val <name>: Property<...>`
#    (Use [[:space:]] not \s — POSIX-portable across BSD sed (macOS) + GNU sed (Linux).)
mapfile -t CODE_PROPS < <(
  grep -oE '^[[:space:]]*abstract val [a-zA-Z][a-zA-Z0-9]*: Property<' "$EXTENSION_FILE" \
    | sed -E 's/^[[:space:]]*abstract val ([a-zA-Z][a-zA-Z0-9]*): Property<.*/\1/' \
    | sort -u
)

if [ ${#CODE_PROPS[@]} -eq 0 ]; then
  echo "::error::no abstract val ...: Property<...> declarations found in $EXTENSION_FILE"
  echo "::error::either the file shape changed or the regex needs an update"
  exit 2
fi

# 2. Extract identifier names mentioned in any heading of REFERENCE.md.
#    A heading like '### 🟢 `buildMatrix`' or '### 🟡 `npmPublishMatrix` / `npmPackagePrefix`'
#    yields one identifier per backtick-wrapped token. Identifiers are
#    `[A-Za-z][A-Za-z0-9]*` only — multi-word headings (`flavors { register(...) { ... } }`)
#    don't match the identifier regex and are ignored, which is what we want.
mapfile -t DOC_NAMES < <(
  grep -E '^#{1,6}\s.*`[a-zA-Z][a-zA-Z0-9]*`' "$REFERENCE_FILE" \
    | grep -oE '`[a-zA-Z][a-zA-Z0-9]*`' \
    | tr -d '`' \
    | sort -u
)

if [ ${#DOC_NAMES[@]} -eq 0 ]; then
  echo "::error::no backtick-wrapped identifiers found in any heading of $REFERENCE_FILE"
  echo "::error::either the file shape changed or the regex needs an update"
  exit 2
fi

# 3. Diff: every code property must appear in the doc names.
MISSING=()
for prop in "${CODE_PROPS[@]}"; do
  # Allowlist check.
  allowed=0
  for entry in "${ALLOWLIST[@]}"; do
    name="${entry%% *}"
    if [ "$name" = "$prop" ]; then
      allowed=1
      break
    fi
  done
  [ "$allowed" -eq 1 ] && continue

  # Membership check.
  found=0
  for doc in "${DOC_NAMES[@]}"; do
    if [ "$doc" = "$prop" ]; then
      found=1
      break
    fi
  done
  [ "$found" -eq 0 ] && MISSING+=("$prop")
done

if [ ${#MISSING[@]} -gt 0 ]; then
  echo "::error::The following KmpFlavorExtension properties are missing from docs/REFERENCE.md:"
  for p in "${MISSING[@]}"; do
    line=$(grep -nE "^\s*abstract val ${p}: Property<" "$EXTENSION_FILE" | head -1 | cut -d: -f1)
    echo "::error::  - ${p} (declared at $EXTENSION_FILE:${line})"
  done
  echo ""
  echo "Add a section to docs/REFERENCE.md describing each missing property."
  echo "Use the existing entries as a template: stability emoji (🟢/🟡/🟠) + name in backticks +"
  echo "code block with the declaration + Default: line + one-paragraph behavior summary."
  echo ""
  echo "If a property is intentionally undocumented (internal/legacy/etc.), add it to the"
  echo "ALLOWLIST array in .github/scripts/check-reference-coverage.sh with a reason."
  exit 1
fi

echo "✓ all ${#CODE_PROPS[@]} KmpFlavorExtension properties documented in $REFERENCE_FILE"
