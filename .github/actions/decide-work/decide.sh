#!/usr/bin/env bash
# Decides whether the calling job has any work for this event and diff.
#
# Reads GITHUB_EVENT_NAME, GITHUB_REF and BASE from the environment; prints
# `run=true` or `run=false` on stdout and its reasoning on stderr, so the caller
# can append the former to $GITHUB_OUTPUT and read the latter in the log.
set -euo pipefail

# The single copy of the rule. It lives here and not in the four call sites
# because four copies of one decision drift into four different decisions, and
# the drift is silent: a job whose list is stale simply answers differently.
#
# Plain globs. The `:!` prefix that turns each into a git exclusion is added
# below and never written here: a pathspec handed over as one quoted string
# expands unquoted, git reads ':!docs/**' as a positive path matching nothing,
# and the empty result reads as "no work" — every gate skipped, green and
# silent.
#
# `.github/**` is deliberately absent, unlike SpendControl. A workflow is build
# configuration, not prose: a pull request that rewrites ci.yml has to run
# ci.yml, or a broken step lands behind five green checks that executed none of
# it.
ignored_globs=(
  'docs/**'
  '*.md'
)

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

exclusions=()
for glob in "${ignored_globs[@]}"; do
  exclusions+=(":!$glob")
done

changed=$(git diff --name-only "$BASE"...HEAD -- "${exclusions[@]}")

if [ -n "$changed" ]; then
  echo "work to do — changed outside the ignored set:" >&2
  echo "$changed" >&2
  echo "run=true"
else
  echo "nothing changed outside the ignored set" >&2
  echo "run=false"
fi
