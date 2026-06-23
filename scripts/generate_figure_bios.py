#!/usr/bin/env python3
"""
Generate AI bios for Media Sage figures using Claude.

Reads figure metadata from seed_figures.sql, generates a 4-paragraph prose bio
per figure via the Claude API, and writes UPDATE statements to update_figure_bios.sql.

Usage:
    python scripts/generate_figure_bios.py [options]

Options:
    --limit=N            Process only the first N figures without bios
    --figures=Name1,...  Process specific figures by name (partial match, comma-separated)
    --force              Regenerate bios even if one already exists in the seed SQL
    --dry-run            List figures that would be processed without making API calls

Environment:
    ANTHROPIC_API_KEY    Your Anthropic API key (also accepts CLAUDE_API_KEY)

Requirements:
    pip install anthropic
"""

import os
import re
import sys
import time
import argparse
from pathlib import Path

REPO_ROOT = Path(__file__).parent.parent
SEED_SQL_PATH = REPO_ROOT / "appServer/src/main/resources/seed_figures.sql"
OUTPUT_SQL_PATH = Path(__file__).parent / "update_figure_bios.sql"
MODEL = "claude-sonnet-4-6"
RATE_LIMIT_PAUSE_S = 0.5

SYSTEM_PROMPT = (
    "You are writing biographical entries for a theological reference guide called The Media Sage. "
    "Your entries are authoritative, dignified, and spiritually rich — written for thoughtful Christian "
    "readers who want to understand who these figures were and why they matter to the faith.\n\n"
    "Write in plain prose only. No markdown headers, no bullet points, no bold text, no section labels. "
    "Four cohesive paragraphs, approximately 200–300 words total."
)


def build_user_prompt(name: str, role: str, century: str, lifespan: str) -> str:
    return (
        f"Write a biographical entry for {name} ({lifespan}), {role} of the {century} century.\n\n"
        "Structure the entry across exactly four paragraphs:\n"
        "1. Life overview — dates, nationality, era, and who they were in broad strokes\n"
        "2. Calling and formation — how they came to faith, key turning points, and the spiritual disciplines that shaped them\n"
        "3. Ministry and theological contribution — what they did, their key teachings, and their tradition or movement\n"
        "4. Legacy and notable works — why they still matter today, and their most significant writings if any\n\n"
        "Plain prose only. No markdown, no headers, no bullet points."
    )


def tokenize_sql_values(s: str) -> list:
    """Parse a SQL VALUES string into a list of Python values, handling '' quote escapes."""
    tokens = []
    i = 0
    while i < len(s):
        while i < len(s) and s[i] in " \t\n\r":
            i += 1
        if i >= len(s):
            break
        if s[i] == ",":
            i += 1
            continue
        if s[i] == "'":
            i += 1
            buf = []
            while i < len(s):
                if s[i] == "'" and i + 1 < len(s) and s[i + 1] == "'":
                    buf.append("'")
                    i += 2
                elif s[i] == "'":
                    i += 1
                    break
                else:
                    buf.append(s[i])
                    i += 1
            tokens.append("".join(buf))
        else:
            j = i
            while j < len(s) and s[j] not in ",)":
                j += 1
            tokens.append(s[i:j].strip())
            i = j
    return tokens


def parse_figures_from_sql(sql_path: Path) -> list:
    """
    Parse INSERT statements from seed_figures.sql.
    Column order: id, name, category, century, role, lifespan, bio, themes, portrait_url, is_enabled
    """
    content = sql_path.read_text(encoding="utf-8")
    figures = []
    for match in re.finditer(r"VALUES\s*\((.+?)\);", content, re.DOTALL):
        tokens = tokenize_sql_values(match.group(1))
        if len(tokens) < 7:
            continue
        figures.append(
            {
                "id": int(tokens[0]),
                "name": tokens[1],
                "century": tokens[3],
                "role": tokens[4],
                "lifespan": tokens[5],
                "bio": tokens[6],
            }
        )
    return figures


def escape_sql(s: str) -> str:
    return s.replace("'", "''")


def generate_bio(client, name: str, role: str, century: str, lifespan: str) -> str:
    message = client.messages.create(
        model=MODEL,
        max_tokens=600,
        system=SYSTEM_PROMPT,
        messages=[{"role": "user", "content": build_user_prompt(name, role, century, lifespan)}],
    )
    return message.content[0].text.strip()


def main():
    parser = argparse.ArgumentParser(description="Generate AI bios for Media Sage figures.")
    parser.add_argument("--limit", type=int, default=None)
    parser.add_argument("--figures", type=str, default=None)
    parser.add_argument("--force", action="store_true")
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()

    api_key = os.environ.get("ANTHROPIC_API_KEY") or os.environ.get("CLAUDE_API_KEY")
    if not api_key and not args.dry_run:
        print("Error: ANTHROPIC_API_KEY env var is not set.")
        sys.exit(1)

    figures = parse_figures_from_sql(SEED_SQL_PATH)

    if not args.force:
        figures = [f for f in figures if not f["bio"].strip()]

    if args.figures:
        targets = [n.strip().lower() for n in args.figures.split(",")]
        figures = [f for f in figures if any(t in f["name"].lower() for t in targets)]

    if args.limit:
        figures = figures[: args.limit]

    print("=== Generate Figure Bios ===")
    print(f"Figures to process : {len(figures)}")
    print(f"Model              : {MODEL}")
    print(f"Output             : {OUTPUT_SQL_PATH}")

    if args.dry_run:
        print("\n--- DRY RUN — no API calls ---")
        for f in figures:
            print(f"  [{f['id']:3d}] {f['name']}")
        return

    try:
        import anthropic
    except ImportError:
        print("Error: anthropic package not found. Run: pip install anthropic")
        sys.exit(1)

    client = anthropic.Anthropic(api_key=api_key)
    results = []
    success = 0

    for i, figure in enumerate(figures):
        print(f"  [{figure['id']:3d}] {figure['name']} ... ", end="", flush=True)
        try:
            bio = generate_bio(client, figure["name"], figure["role"], figure["century"], figure["lifespan"])
            results.append((figure["id"], figure["name"], bio))
            success += 1
            print(f"✓  ({len(bio)} chars)")
        except Exception as e:
            print(f"✗  ERROR: {e}")

        if i < len(figures) - 1:
            time.sleep(RATE_LIMIT_PAUSE_S)

    OUTPUT_SQL_PATH.parent.mkdir(parents=True, exist_ok=True)
    with OUTPUT_SQL_PATH.open("w", encoding="utf-8") as f:
        f.write("-- Generated figure bios — apply to Supabase via psql or the SQL editor\n\n")
        for fig_id, name, bio in results:
            f.write(f"UPDATE figures SET bio = '{escape_sql(bio)}' WHERE id = {fig_id}; -- {name}\n\n")

    print(f"\n=== Complete ===")
    print(f"Generated : {success} / {len(figures)}")
    print(f"Output    : {OUTPUT_SQL_PATH}")


if __name__ == "__main__":
    main()
