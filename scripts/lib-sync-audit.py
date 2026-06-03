#!/usr/bin/env python3
"""
lib-sync-audit.py — audit + plan engine for /lib-sync.

Runs the library's Tier 1 consumer.md verify blocks against a consumer
codebase, classifies each result, and emits a structured plan for
lib-sync.sh to interpret.

The KEY insight: the library's `docs/adoption/v{X.Y}/consumer.md` is
already the abstract contract for "what a consumer at v{X.Y} should
have." Running each verify block against the consumer's working tree
IS the audit. We don't need a parallel rule engine — the doc IS the
rule engine.

What it classifies:

  PASS         — verify passed against the consumer; no action needed
  AUTO_FIX     — verify failed; the script knows how to fix it
                 (current safe auto-fixes: version pin bump, toolchain
                 floor bumps in libs.versions.toml, wrapper bump)
  MANUAL       — verify failed; needs human judgment
                 (e.g. DSL surface not wired, convention plugin missing,
                  flavors not registered)
  SKIP         — verify is conditional (e.g. AGP 9 landmines only matter
                 if the consumer is on AGP 9); auto-detected and ignored
                 when the precondition doesn't hold

Output is structured JSON (stdout) that lib-sync.sh parses to drive
the apply phase. Plain-text summary on stderr.

Usage:
  scripts/lib-sync-audit.py --consumer samples/kmp-project-template \\
                             --library-doc docs/adoption/v2.7/consumer.md \\
                             --target-version 2.7.1
  scripts/lib-sync-audit.py --json   (same; JSON is default)
  scripts/lib-sync-audit.py --summary-only   (no JSON; text only)

Exit codes:
  0  — audit complete (may include manual gaps; doesn't fail the run)
  1  — audit could not be performed (bad inputs, missing files)
"""

from __future__ import annotations

import argparse
import json
import re
import subprocess
import sys
from pathlib import Path
from typing import Dict, List, Optional, Tuple


# ─── verify-block extraction (mirrors adoption-doc-verify.py) ──────────
_HEADER_RE = re.compile(
    r"^###\s*✅\s*Verify|^###\s*Release-time check|^####\s*\[§\d+",
    re.MULTILINE,
)
_H2_RE = re.compile(r"^##\s+([^\n]+)$", re.MULTILINE)


def extract_blocks(doc_path: Path) -> List[Tuple[str, str]]:
    """Return list of (section_h2, bash_code) pairs."""
    text = doc_path.read_text(encoding="utf-8")
    blocks: List[Tuple[str, str]] = []
    header_matches = list(_HEADER_RE.finditer(text))
    h2_matches = list(_H2_RE.finditer(text))

    def latest_h2_before(pos: int) -> str:
        last = ""
        for m in h2_matches:
            if m.start() < pos:
                last = m.group(1).strip()
        return last or "(unsectioned)"

    for i, hm in enumerate(header_matches):
        end = (
            header_matches[i + 1].start()
            if i + 1 < len(header_matches)
            else len(text)
        )
        section_text = text[hm.start():end]
        bm = re.search(r"```bash\n(.*?)\n```", section_text, re.DOTALL)
        if not bm:
            continue
        code = bm.group(1)
        if code.split("\n", 1)[0].strip().startswith("# adoption-verify: skip"):
            continue
        blocks.append((latest_h2_before(hm.start()), code))
    return blocks


# ─── auto-fix classification ───────────────────────────────────────────
# Maps the H2 section title → kind of auto-fix available. Only sections
# whose title pattern matches one of these get classified as AUTO_FIX
# on failure; everything else is MANUAL.
AUTO_FIX_PATTERNS: Dict[str, str] = {
    r"Plugin pinned to v": "version_pin",
    r"Toolchain compatibility": "toolchain_floor",
}

# Sections that are conditional on consumer state. If the precondition
# returns False, the section is SKIPped regardless of its verify result.
CONDITIONAL_PATTERNS: Dict[str, "Precondition"] = {}


class Precondition:
    """Tests whether a conditional section applies to this consumer."""

    def __init__(self, name: str, test_fn):
        self.name = name
        self.test_fn = test_fn  # (consumer_path: Path) -> bool

    def applies(self, consumer_path: Path) -> bool:
        return self.test_fn(consumer_path)


# AGP 9-only sections — only audit if the consumer is on AGP 9
def _on_agp_9(consumer_path: Path) -> bool:
    libs = consumer_path / "gradle" / "libs.versions.toml"
    if not libs.exists():
        return False
    text = libs.read_text(encoding="utf-8")
    return bool(re.search(r'^agp\s*=\s*"9\.', text, re.MULTILINE))


CONDITIONAL_PATTERNS[r"AGP 9\.x compatibility"] = Precondition("on_agp_9", _on_agp_9)


# Pattern 3a/3b are mutually exclusive — direct-apply (3a) vs convention plugin (3b).
# §4a only applies if the consumer uses pattern 3a; §4b only applies if 3b is used.
# Detection: 3b's signature is a `build-logic/.../KmpFlavorsConventionPlugin.kt`
# (or similar) that calls `pluginManager.apply(KmpFlavorPlugin::class.java)`.
def _on_pattern_3a(consumer_path: Path) -> bool:
    """True iff the consumer applies the plugin directly (not via convention plugin)."""
    return not _on_pattern_3b(consumer_path)


def _on_pattern_3b(consumer_path: Path) -> bool:
    """True iff the consumer wires the plugin through a convention plugin."""
    bl = consumer_path / "build-logic"
    if not bl.exists():
        return False
    try:
        proc = subprocess.run(
            ["grep", "-r", "-l", "KmpFlavorPlugin::class.java", str(bl)],
            capture_output=True,
            text=True,
        )
        return proc.returncode == 0 and bool(proc.stdout.strip())
    except (OSError, subprocess.SubprocessError):
        return False


CONDITIONAL_PATTERNS[r"4a\. Plugin applied \(direct-apply"] = Precondition(
    "on_pattern_3a", _on_pattern_3a
)
CONDITIONAL_PATTERNS[r"4b\. Plugin applied \+ configured via convention plugin"] = Precondition(
    "on_pattern_3b", _on_pattern_3b
)


def classify_fix(section: str) -> str:
    for pat, kind in AUTO_FIX_PATTERNS.items():
        if re.search(pat, section):
            return kind
    return "manual"


def get_precondition(section: str) -> Optional[Precondition]:
    for pat, pre in CONDITIONAL_PATTERNS.items():
        if re.search(pat, section):
            return pre
    return None


# ─── verify execution ───────────────────────────────────────────────────
def run_block(code: str, cwd: Path) -> Tuple[int, str]:
    proc = subprocess.run(
        ["bash", "-c", code],
        cwd=str(cwd),
        capture_output=True,
        text=True,
    )
    return proc.returncode, (proc.stdout + proc.stderr).strip()


# ─── audit pipeline ─────────────────────────────────────────────────────
def audit(
    consumer_path: Path,
    library_doc: Path,
    target_version: str,
) -> Dict:
    """
    Return a structured plan dict shaped like:

    {
      "consumer_path": "...",
      "library_doc": "...",
      "target_version": "2.7.1",
      "sections": [
        {
          "name": "1. Plugin pinned to v2.7.0",
          "status": "PASS|AUTO_FIX|MANUAL|SKIP",
          "fix_kind": "version_pin|toolchain_floor|manual|null",
          "output": "...",
          "exit_code": 0,
        }, ...
      ],
      "totals": { "pass": N, "auto_fix": N, "manual": N, "skip": N }
    }
    """
    if not library_doc.exists():
        raise FileNotFoundError(f"library doc not found: {library_doc}")
    if not consumer_path.exists():
        raise FileNotFoundError(f"consumer path not found: {consumer_path}")

    blocks = extract_blocks(library_doc)
    results = []
    totals = {"pass": 0, "auto_fix": 0, "manual": 0, "skip": 0}

    for section, code in blocks:
        # Conditional skip
        pre = get_precondition(section)
        if pre and not pre.applies(consumer_path):
            results.append(
                {
                    "name": section,
                    "status": "SKIP",
                    "fix_kind": None,
                    "output": f"(precondition '{pre.name}' did not hold for this consumer)",
                    "exit_code": 0,
                }
            )
            totals["skip"] += 1
            continue

        rc, out = run_block(code, consumer_path)
        if rc == 0:
            results.append(
                {
                    "name": section,
                    "status": "PASS",
                    "fix_kind": None,
                    "output": out[:500],
                    "exit_code": 0,
                }
            )
            totals["pass"] += 1
        else:
            fix = classify_fix(section)
            status = "AUTO_FIX" if fix != "manual" else "MANUAL"
            results.append(
                {
                    "name": section,
                    "status": status,
                    "fix_kind": fix if fix != "manual" else None,
                    "output": out[:500],
                    "exit_code": rc,
                }
            )
            if status == "AUTO_FIX":
                totals["auto_fix"] += 1
            else:
                totals["manual"] += 1

    return {
        "consumer_path": str(consumer_path),
        "library_doc": str(library_doc),
        "target_version": target_version,
        "sections": results,
        "totals": totals,
    }


# ─── reporting ──────────────────────────────────────────────────────────
def print_summary(plan: Dict, fh=sys.stderr) -> None:
    print("", file=fh)
    print("━━━ /lib-sync audit ━━━", file=fh)
    print(f"  consumer    : {plan['consumer_path']}", file=fh)
    print(f"  library doc : {plan['library_doc']}", file=fh)
    print(f"  target      : v{plan['target_version']}", file=fh)
    print("", file=fh)

    for s in plan["sections"]:
        glyph = {
            "PASS": "✓",
            "AUTO_FIX": "⟳",
            "MANUAL": "✗",
            "SKIP": "·",
        }[s["status"]]
        suffix = ""
        if s["status"] == "AUTO_FIX":
            suffix = f" → auto-fixable ({s['fix_kind']})"
        elif s["status"] == "MANUAL":
            suffix = " → manual fix"
        elif s["status"] == "SKIP":
            suffix = " (n/a)"
        print(f"  {glyph} {s['name']}{suffix}", file=fh)

    t = plan["totals"]
    print("", file=fh)
    print(
        f"  Totals — PASS: {t['pass']}   AUTO_FIX: {t['auto_fix']}   "
        f"MANUAL: {t['manual']}   SKIP: {t['skip']}",
        file=fh,
    )
    print("", file=fh)

    manual_gaps = [s for s in plan["sections"] if s["status"] == "MANUAL"]
    if manual_gaps:
        print("  Manual gaps need contributor judgment:", file=fh)
        for s in manual_gaps:
            first_err_line = s["output"].split("\n", 1)[0] if s["output"] else "(no output)"
            print(f"    · {s['name']}: {first_err_line[:80]}", file=fh)
        print("", file=fh)


# ─── CLI ────────────────────────────────────────────────────────────────
def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--consumer", type=Path, required=True)
    ap.add_argument("--library-doc", type=Path, required=True)
    ap.add_argument("--target-version", required=True)
    ap.add_argument("--json", action="store_true", help="emit JSON plan to stdout (default)")
    ap.add_argument(
        "--summary-only",
        action="store_true",
        help="suppress JSON; only emit human-readable summary on stderr",
    )
    args = ap.parse_args()

    try:
        plan = audit(args.consumer, args.library_doc, args.target_version)
    except FileNotFoundError as e:
        print(f"error: {e}", file=sys.stderr)
        return 1

    print_summary(plan, fh=sys.stderr)

    if not args.summary_only:
        json.dump(plan, sys.stdout, indent=2)
        sys.stdout.write("\n")

    return 0


if __name__ == "__main__":
    sys.exit(main())
