#!/usr/bin/env bash
# Decides whether the calling job has any work for this event and diff.
#
# Reads GITHUB_EVENT_NAME, GITHUB_REF, BASE, IGNORE and COUNT_ANYWAY from the
# environment; prints `run=true` or `run=false` on stdout and its reasoning on
# stderr, so the caller can append the former to $GITHUB_OUTPUT and read the
# latter in the log.
#
# IGNORE and COUNT_ANYWAY are plain path globs, one per line. The `:!` prefix
# that turns a glob into an exclusion is added here and never by the caller: a
# caller passing quoted pathspecs in one string expands unquoted, git reads
# ':!docs/**' as a positive path matching nothing, and an empty result reads as
# "no work" — every gate skipped, green and silent.
set -euo pipefail

lines_to_array() {
  local line
  while IFS= read -r line; do
    [ -n "$line" ] && printf '%s\n' "$line"
  done <<<"${1:-}"
}

if [ "${GITHUB_EVENT_NAME:-}" = push ] && [ "${GITHUB_REF:-}" = refs/heads/main ]; then
  echo "push to main: the pull request that landed this commit ran everything" >&2
  echo "run=false"
  exit 0
fi

if [ "${GITHUB_EVENT_NAME:-}" != pull_request ]; then
  echo "${GITHUB_EVENT_NAME:-unknown event}: no pull request to diff against" >&2
  echo "run=true"
  exit 0
fi

# No apostrophe in this message: bash reads one inside ${...} as an opening quote
# and the whole script stops parsing.
: "${BASE:?BASE, the base commit of the pull request, is required}"

ignore=()
while IFS= read -r pattern; do
  ignore+=(":!$pattern")
done < <(lines_to_array "${IGNORE:-}")

count_anyway=()
while IFS= read -r pattern; do
  count_anyway+=("$pattern")
done < <(lines_to_array "${COUNT_ANYWAY:-}")

if [ ${#ignore[@]} -eq 0 ]; then
  changed=$(git diff --name-only "$BASE"...HEAD)
else
  changed=$(git diff --name-only "$BASE"...HEAD -- "${ignore[@]}")
fi

# A second call, not a longer first one: a positive pathspec beside exclusions
# replaces the result instead of adding to it, and an empty pathspec list after
# `--` filters nothing at all.
if [ ${#count_anyway[@]} -gt 0 ]; then
  carved=$(git diff --name-only "$BASE"...HEAD -- "${count_anyway[@]}")
  if [ -n "$carved" ]; then
    if [ -n "$changed" ]; then
      changed="$changed
$carved"
    else
      changed="$carved"
    fi
  fi
fi

if [ -n "$changed" ]; then
  echo "work to do — changed outside the ignored set:" >&2
  echo "$changed" >&2
  echo "run=true"
else
  echo "nothing changed outside the ignored set" >&2
  echo "run=false"
fi
