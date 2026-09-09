#!/usr/bin/env python3
"""Refuse a release whose notes still describe the previous one.

Run as `check_release_readiness.py <base-rev> [head-rev]`; the base is the
previous release's tag. An empty base means there is no previous release —
this repository has no tag of the `v<name>+<code>` scheme until release.yml
creates the first — and the check passes with a notice: nothing to repeat, no
previous name to differ from. No base at all is a workflow that lost its
variable, and is refused rather than read as a first release.

Every locale is checked, by text, against every note the base holds for that
same locale — a release that translated the English but left German on the
last release's words fails here, which is the whole point of a gate over a
convention. Two weaker tests were tried and dropped against real revisions of
SpendControl, the repository this script comes from:

  * comparing paths calls a rename a change — when the notes moved from
    production.txt to default.txt every file differed while every word stayed
    the same;
  * asking merely for one text nowhere in the base passes when a release adds
    locales, because a fresh translation of the *old* text is a new string.

The false positive is a locale whose new note repeats its own previous one word
for word — plausible for a short note like "Bug fixes." It costs a sentence to
break the tie, and a release note identical to the last one is worth looking at
anyway.

A base with no notes at all — every commit before the store texts landed — has
nothing to repeat, so only the version test speaks.

Tested by check_release_readiness.test.py beside it, which release.yml runs
before this check for the reason the decide-work action runs its own: a wrong
answer here is the failure that reports green.
"""
import subprocess
import sys

ROOT = "app/src/main/play/release-notes"
VERSION_FILE = "app/version.properties"


def git(*args):
    return subprocess.run(
        ["git", *args], capture_output=True, text=True, check=True
    ).stdout


def read(rev, path):
    """The file's text, or None where the revision does not carry it."""
    done = subprocess.run(
        ["git", "show", f"{rev}:{path}"], capture_output=True, text=True
    )
    return done.stdout.strip() if done.returncode == 0 else None


def locales(rev):
    paths = git("ls-tree", "-r", "--name-only", rev, "--", ROOT).split()
    return sorted({p[len(ROOT) + 1:].split("/")[0] for p in paths})


def base_notes(rev, locale):
    """Every note the base holds for one locale, whatever the files are called:
    the layout has changed once already in SpendControl and may here."""
    paths = git("ls-tree", "-r", "--name-only", rev, "--", f"{ROOT}/{locale}").split()
    return {read(rev, p) for p in paths}


def version(rev):
    body = git("show", f"{rev}:{VERSION_FILE}")
    return next(
        line.split("=", 1)[1].strip()
        for line in body.splitlines()
        if line.startswith("versionName=")
    )


def main(base, head="HEAD"):
    if not base:
        print(
            "No previous release tag: this is the first release of the "
            "v<name>+<code> scheme, so there is nothing for the notes to repeat "
            "and no previous versionName to differ from."
        )
        return 0

    failed = False

    stale = []
    for locale in locales(head):
        note = read(head, f"{ROOT}/{locale}/default.txt")
        if note is None:
            print(f"::error::{ROOT}/{locale}/default.txt is missing.")
            failed = True
        elif note in base_notes(base, locale):
            stale.append(locale)
    if stale:
        noun = "locale" if len(stale) == 1 else "locales"
        print(
            f"::error::{len(stale)} {noun} in {ROOT} carry a release note the "
            "previous release already has, so this release would ship its text "
            f"to testers: {', '.join(stale)}. Write the notes for this release "
            "before dispatching it."
        )
        failed = True

    old, new = version(base), version(head)
    if old == new:
        print(
            f"::error::versionName is still {new}, the same as in the previous "
            f"release. Bump it in {VERSION_FILE}."
        )
        failed = True
    else:
        print(f"versionName: {old} -> {new}")

    return 1 if failed else 0


if __name__ == "__main__":
    if len(sys.argv) < 2:
        print(f"usage: {sys.argv[0]} <base-rev> [head-rev]", file=sys.stderr)
        sys.exit(2)
    sys.exit(main(*sys.argv[1:3]))
