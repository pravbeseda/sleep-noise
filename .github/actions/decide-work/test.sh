#!/usr/bin/env bash
# Tests for decide.sh. Runs from the action itself, before it decides anything:
# a pull request that edits .github/** is one CI skips every Gradle job for, so
# tests living there would be absent exactly when this action changes.
set -euo pipefail

here=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
decide="$here/decide.sh"
failures=0

# A throwaway repository with a base commit and a branch on top of it, so the
# decision runs against a real merge base rather than a mocked diff.
fixture() {
  local dir; dir=$(mktemp -d)
  git -C "$dir" init --quiet
  git -C "$dir" config user.email t@example.com
  git -C "$dir" config user.name test
  mkdir -p "$dir/app/src/main/java/ru/pravbeseda/sleepnoise/media" \
           "$dir/docs/plans" "$dir/.github/workflows"
  echo base > "$dir/app/src/main/java/ru/pravbeseda/sleepnoise/media/NoiseEngine.kt"
  echo base > "$dir/README.md"
  echo base > "$dir/CLAUDE.md"
  echo base > "$dir/docs/plans/REFACTORING_PLAN.md"
  echo base > "$dir/.github/workflows/ci.yml"
  git -C "$dir" add -A
  git -C "$dir" commit --quiet -m base
  echo "$dir"
}

# check <name> <expected> <event> <ref> <touched…>
check() {
  local name=$1 expected=$2 event=$3 ref=$4; shift 4
  local dir; dir=$(fixture)
  local base; base=$(git -C "$dir" rev-parse HEAD)

  local f
  for f in "$@"; do echo changed > "$dir/$f"; done
  if [ $# -gt 0 ]; then
    git -C "$dir" add -A
    git -C "$dir" commit --quiet -m change
  fi

  local out
  out=$(cd "$dir" && \
    GITHUB_EVENT_NAME="$event" GITHUB_REF="$ref" BASE="$base" \
    IGNORE="$IGNORE" COUNT_ANYWAY="${COUNT_ANYWAY:-}" \
    bash "$decide" 2>/dev/null | sed -n 's/^run=//p')

  if [ "$out" = "$expected" ]; then
    echo "ok   — $name"
  else
    echo "FAIL — $name: expected run=$expected, got run=${out:-<none>}"
    failures=$((failures + 1))
  fi
  rm -rf "$dir"
}

app=app/src/main/java/ru/pravbeseda/sleepnoise/media/NoiseEngine.kt

# Every caller in this repository passes exactly this, and no carve-out.
IGNORE=$'docs/**\n*.md\n.github/**'
COUNT_ANYWAY=''

check "push to main does no work"                false push refs/heads/main "$app"
check "manual dispatch always works"             true  workflow_dispatch refs/heads/main docs/plans/REFACTORING_PLAN.md
check "app code means work"                      true  pull_request refs/pull/1/merge "$app"
check "ignored paths only mean no work"          false pull_request refs/pull/1/merge docs/plans/REFACTORING_PLAN.md .github/workflows/ci.yml
check "prose alone is not app code"              false pull_request refs/pull/1/merge README.md CLAUDE.md
check "a mixed diff means work"                  true  pull_request refs/pull/1/merge docs/plans/REFACTORING_PLAN.md "$app"

# No caller passes count-anyway today, but the input is part of the ported
# interface and a later one will: cover the carve-out so it cannot rot.
COUNT_ANYWAY=$'README.md'

check "a carved-out path means work"             true  pull_request refs/pull/1/merge README.md
check "prose beside the carve-out is not work"   false pull_request refs/pull/1/merge docs/plans/REFACTORING_PLAN.md

if [ "$failures" -gt 0 ]; then
  echo "$failures test(s) failed"
  exit 1
fi
echo "all tests passed"
