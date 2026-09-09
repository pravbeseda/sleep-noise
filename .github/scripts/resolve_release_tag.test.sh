#!/usr/bin/env bash
# Tests for resolve_release_tag.sh, run by all three release workflows before
# any of them trusts the script. A wrong answer here promotes or halts the wrong
# release, or hands release.yml the wrong previous tag, and reports green while
# doing it.
set -euo pipefail

here=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
resolve="$here/resolve_release_tag.sh"
failures=0

# A repository with one commit per tag, so every tag names a commit of its own.
fixture() { # <tags…>
  local dir; dir=$(mktemp -d)
  git -C "$dir" init --quiet
  git -C "$dir" config user.email t@example.com
  git -C "$dir" config user.name test
  local tag
  for tag in "$@"; do
    git -C "$dir" commit --quiet --allow-empty -m "$tag"
    git -C "$dir" tag "$tag"
  done
  [ $# -gt 0 ] || git -C "$dir" commit --quiet --allow-empty -m untagged
  echo "$dir"
}

# check <name> <expected-output-or-FAIL> <argument> <tags…>
check() {
  local name=$1 expected=$2 arg=$3; shift 3
  local dir; dir=$(fixture "$@")
  local out; out=$(cd "$dir" && bash "$resolve" "$arg" 2>/dev/null) || out=FAIL
  if [ "$out" = "$expected" ]; then
    echo "ok   — $name"
  else
    echo "FAIL — $name: expected '$expected', got '$out'"
    failures=$((failures + 1))
  fi
  rm -rf "$dir"
}

check "an empty argument means the newest release"   v1.1.0+286 ""          v1.0.4+99 v1.1.0+286
# The code decides, not the string: lexically +99 sorts after +286.
check "newest is by version code, not by text"       v1.1.0+286 ""          v1.1.0+286 v1.0.4+99
# A tag outside the scheme is neither picked nor mis-parsed into a release.
check "a tag without a code is not a release"        v1.0.4+99  ""          v1.0.3 v1.0.4+99 v1.0.5
check "no release tag at all is refused"             FAIL       ""          v1.0.3
check "a named tag that exists is returned as given" v1.0.4+99  v1.0.4+99   v1.0.4+99 v1.1.0+286
check "a named tag that does not exist is refused"   FAIL       v9.9.9+999  v1.0.4+99

# The warning is on stderr, which `check` throws away, so it needs its own shape.
# It is what stands in for the guard the script cannot make: Play alone knows
# which version code is on a track, and this script has no credentials for it.
# warns <name> <yes|no> <argument> <tags…>
warns() {
  local name=$1 expected=$2 arg=$3; shift 3
  local dir; dir=$(fixture "$@")
  local err; err=$(cd "$dir" && bash "$resolve" "$arg" 2>&1 >/dev/null) || true
  local got=no
  case "$err" in *"is not the newest release"*) got=yes ;; esac
  if [ "$got" = "$expected" ]; then
    echo "ok   — $name"
  else
    echo "FAIL — $name: expected warning=$expected, got warning=$got"
    failures=$((failures + 1))
  fi
  rm -rf "$dir"
}

warns "naming an older tag is warned about"          yes        v1.0.4+99   v1.0.4+99 v1.1.0+286
warns "naming the newest tag is not"                 no         v1.1.0+286  v1.0.4+99 v1.1.0+286
warns "the resolved newest tag is not"               no         ""          v1.0.4+99 v1.1.0+286

if [ "$failures" -gt 0 ]; then
  echo "$failures test(s) failed"
  exit 1
fi
echo "all tests passed"
