#!/usr/bin/env bash
# Fails a pull request that removes tests: the number of @Test annotations under
# the two test source sets may never drop against the base branch.
#
# Reads BASE_SHA — github.event.pull_request.base.sha — and runs in the checkout
# of the branch. Both sides are read out of git rather than off disk: the rule
# is about committed history, and a worktree read would answer differently on a
# machine with local edits. Tested by no-deleted-tests.test.sh beside it.
#
# It counts the whole tree, not each file, so a rename, a move between files or
# source sets, and a split class all remove and add the same annotations and go
# through. The price is stated rather than hidden: a pull request that deletes
# one test and adds another passes, because the net count is what is measured.
#
# There is no escape hatch, deliberately. A deletion that is genuinely right is
# a conversation in review, not a flag anyone can set (issue #14) — the same
# reasoning the two baselines carry.
set -euo pipefail

roots=(app/src/test app/src/androidTest)
# @Test and @org.junit.Test alike, and neither @TestOnly nor @ParameterizedTest:
# the trailing \b is what keeps a longer name out. The same shape as the
# annotation pattern in the @Ignore step of the Guardrails job.
annotation='@([[:alnum:]_]+\.)*Test\b'

# No apostrophe in this message: bash reads one inside ${...} as an opening
# quote and the whole script stops parsing.
: "${BASE_SHA:?BASE_SHA, the base commit of the pull request, is required}"

# The merge base, not BASE_SHA itself — a no-op on CI and load-bearing off it.
# The Guardrails job checks out with no ref:, so on a pull_request event HEAD is
# refs/pull/N/merge, whose first parent is base.sha; the merge base is then
# base.sha itself and the two readings agree. Run by hand on the branch with
# BASE_SHA=origin/main they do not: main has moved on, and every test it gained
# since would read as one this branch deleted. That is the shape the test builds
# and the one the pull request description exercised.
base=$(git merge-base "$BASE_SHA" HEAD)

# Kotlin and Java only. Everything else under those roots — a fixture, a
# resource, a README — can hold the word @Test without holding a test.
test_files_at() { # <ref>
  git ls-tree -r --name-only "$1" -- "${roots[@]}" | grep -E '\.(kt|java)$' || true
}

# Comments are cut out before anything is counted. A test switched off by
# commenting it out no longer runs, so counting its annotation would leave the
# floor open at exactly the point it exists to close: the @Ignore step next to
# this one sees an added annotation, and nothing sees an added comment marker.
#
# Both forms an editor produces are handled, because both are one keystroke:
# // per line, and a /* */ block, whose lines carry no leading star when the IDE
# writes it. A prefix match on the line is not enough for the second — it was
# what this replaced, and it counted the @Test inside such a block as live.
# Nesting is tracked rather than assumed away: Kotlin allows it, so a method
# that already carries a /* */ comment still comments out as one whole block.
strip_comments() {
  awk '
    {
      line = $0
      out = ""
      while (1) {
        if (depth > 0) {
          i = index(line, "*/")
          k = index(line, "/*")
          # Kotlin nests block comments where C does not, so an inner /* raises
          # the depth and the outer comment survives the inner terminator. A
          # boolean here ended the comment at the first */ and handed the rest
          # of a commented-out method back to the count as live code.
          if (k > 0 && (i == 0 || k < i)) { depth++; line = substr(line, k + 2); continue }
          if (i == 0) { line = ""; break }
          depth--
          line = substr(line, i + 2)
        } else {
          i = index(line, "/*")
          j = index(line, "//")
          if (j > 0 && (i == 0 || j < i)) { out = out substr(line, 1, j - 1); break }
          if (i == 0) { out = out line; break }
          out = out substr(line, 1, i - 1)
          line = substr(line, i + 2)
          depth = 1
        }
      }
      print out
    }
  '
}

count_at() { # <ref> <path>
  git cat-file -e "$1:$2" 2>/dev/null || { echo 0; return; }
  git show "$1:$2" | strip_comments | grep -cE "$annotation" || true
}

before=0
after=0
dropped=()

while IFS= read -r file; do
  [ -n "$file" ] || continue
  was=$(count_at "$base" "$file")
  now=$(count_at HEAD "$file")
  before=$((before + was))
  after=$((after + now))
  if [ "$now" -lt "$was" ]; then
    dropped+=("$file"$'\t'"$was"$'\t'"$now")
  fi
done < <( { test_files_at "$base"; test_files_at HEAD; } | sort -u )

echo "@Test annotations under ${roots[*]}: $before -> $after"

if [ "$after" -ge "$before" ]; then
  echo "No test removed."
  exit 0
fi

# The per-file lines first, so the annotations land on the files themselves, and
# the summary last, where a reader looking at the job sees it without scrolling.
for entry in "${dropped[@]}"; do
  IFS=$'\t' read -r file was now <<<"$entry"
  echo "::error file=$file::$file went from $was @Test to $now. If the test moved or was renamed, the count elsewhere has to make up for it; if it was deleted, that is the thing this check exists to stop."
done

echo "::error::@Test count under ${roots[*]} fell from $before to $after. Never weaken a test to get a green build (CLAUDE.md, \"Tests are mandatory\"): a moved, renamed or split test keeps the count and passes here. There is no flag for a deliberate deletion — make the case in review."
exit 1
