#!/usr/bin/env bash
# Tests for resolve_release_tag.sh, run by promote.yml and rollout.yml before
# either resolves anything. A wrong answer here promotes or halts the wrong
# release and reports green while doing it.
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

if [ "$failures" -gt 0 ]; then
  echo "$failures test(s) failed"
  exit 1
fi
echo "all tests passed"
