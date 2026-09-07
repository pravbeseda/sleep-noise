#!/usr/bin/env bash
# Tests for no-deleted-tests.sh, run by the Guardrails job before the check
# itself. Nothing else on CI exercises them, and this check fails silently when
# it is wrong: a miscount waves a deleted test through and still reports green.
set -euo pipefail

here=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
check_script="$here/no-deleted-tests.sh"
failures=0

unit=app/src/test/java/ru/pravbeseda/sleepnoise
instrumented=app/src/androidTest/java/ru/pravbeseda/sleepnoise

# A test class with <count> methods, named after <class>. Generated rather than
# copied so a case can say "four tests became three" in one line.
test_class() { # <class> <count>
  local class=$1 count=$2 i
  echo "package ru.pravbeseda.sleepnoise"
  echo
  echo "class $class {"
  for ((i = 1; i <= count; i++)); do
    printf '    @Test\n    fun case%d() = Unit\n' "$i"
  done
  echo "}"
}

# A repository shaped like this one: two test source sets, a helper carrying no
# @Test at all, and prose beside them.
fixture() {
  local dir; dir=$(mktemp -d)
  git -C "$dir" init --quiet
  git -C "$dir" config user.email t@example.com
  git -C "$dir" config user.name test
  mkdir -p "$dir/$unit/media" "$dir/$unit/timer" "$dir/$instrumented/media"
  test_class PinkNoiseTest 4 > "$dir/$unit/media/PinkNoiseTest.kt"
  test_class SleepTimerTest 8 > "$dir/$unit/timer/SleepTimerTest.kt"
  test_class NoiseEngineHammerTest 1 > "$dir/$instrumented/media/NoiseEngineHammerTest.kt"
  echo "class RewindableRandom" > "$dir/$unit/media/RewindableRandom.kt"
  echo base > "$dir/CLAUDE.md"
  git -C "$dir" add -A
  git -C "$dir" commit --quiet -m base
  echo "$dir"
}

# Builds the shape a pull request actually has — a branch off main, and a main
# that may have moved on since — and prints "<dir> <base sha>". BASE_SHA is the
# head of the base branch, never the merge base: that is the distinction the
# "base branch moved on" case below covers.
prepare() { # <branch-mutation> [<base-branch-mutation>]
  local mutate=$1 mutate_base=${2:-}
  local dir; dir=$(fixture)
  local root; root=$(git -C "$dir" rev-parse HEAD)

  git -C "$dir" checkout --quiet -b pr "$root"
  ( cd "$dir" && "$mutate" )
  git -C "$dir" add -A
  git -C "$dir" commit --quiet --allow-empty -m branch

  local base=$root
  if [ -n "$mutate_base" ]; then
    git -C "$dir" checkout --quiet --detach "$root"
    ( cd "$dir" && "$mutate_base" )
    git -C "$dir" add -A
    git -C "$dir" commit --quiet -m "base moves on"
    base=$(git -C "$dir" rev-parse HEAD)
  fi

  git -C "$dir" checkout --quiet pr
  echo "$dir $base"
}

# check <name> <pass|fail> <branch-mutation> [<base-branch-mutation>]
check() {
  local name=$1 expected=$2; shift 2
  local dir base; read -r dir base < <(prepare "$@")

  local actual=pass output
  output=$( cd "$dir" && BASE_SHA="$base" bash "$check_script" 2>&1 ) || actual=fail

  if [ "$actual" = "$expected" ]; then
    echo "ok   — $name"
  else
    echo "FAIL — $name: expected the check to $expected, it did $actual"
    echo "$output" | sed 's/^/       /'
    failures=$((failures + 1))
  fi
  rm -rf "$dir"
}

nothing() { :; }
delete_method()    { test_class PinkNoiseTest 3 > "$unit/media/PinkNoiseTest.kt"; }
delete_file()      { rm "$unit/media/PinkNoiseTest.kt"; }
delete_helper()    { rm "$unit/media/RewindableRandom.kt"; }
add_tests()        { test_class PinkNoiseTest 6 > "$unit/media/PinkNoiseTest.kt"; }
rename_method()    { sed -i.bak 's/fun case1(/fun clampsToUnitRange(/' "$unit/media/PinkNoiseTest.kt"
                     rm "$unit/media/PinkNoiseTest.kt.bak"; }
move_between_files() {
  test_class PinkNoiseTest 2 > "$unit/media/PinkNoiseTest.kt"
  test_class PinkNoiseSpectrumTest 2 > "$unit/media/PinkNoiseSpectrumTest.kt"
}
move_between_source_sets() {
  rm "$instrumented/media/NoiseEngineHammerTest.kt"
  test_class NoiseEngineHammerTest 1 > "$unit/media/NoiseEngineHammerTest.kt"
}
split_class() {
  rm "$unit/media/PinkNoiseTest.kt"
  test_class PinkNoiseLevelTest 2 > "$unit/media/PinkNoiseLevelTest.kt"
  test_class PinkNoiseSpectrumTest 2 > "$unit/media/PinkNoiseSpectrumTest.kt"
}
swap_test() { # one deleted, one added elsewhere: the net count is what is checked
  test_class PinkNoiseTest 3 > "$unit/media/PinkNoiseTest.kt"
  test_class SleepTimerTest 9 > "$unit/timer/SleepTimerTest.kt"
}
base_gains_a_test() { test_class NoiseMixerTest 5 > "$unit/media/NoiseMixerTest.kt"; }

check "an untouched branch passes"            pass nothing
check "a deleted test method fails"           fail delete_method
check "a deleted test file fails"             fail delete_file
check "a deleted helper carrying no test passes" pass delete_helper
check "added tests pass"                      pass add_tests
check "a renamed test passes"                 pass rename_method
check "a test moved to another file passes"   pass move_between_files
check "a test moved between source sets passes" pass move_between_source_sets
check "a split test class passes"             pass split_class
# The stated limit of a net count: this is the one deletion the check cannot
# see. Asserted rather than left to be discovered, because a check nobody can
# state the edges of is a check nobody trusts.
check "one test deleted and another added passes" pass swap_test
# Without the merge base this fails: BASE_SHA is the head of main, and a test
# added to main after the branch left it would read as one the branch deleted.
check "a base branch that moved on passes"    pass nothing base_gains_a_test

# The message is half the check: a red job that does not say what disappeared
# sends the reader off to diff two trees by hand.
read -r dir base < <(prepare delete_file)
message=$( cd "$dir" && BASE_SHA="$base" bash "$check_script" 2>&1 || true )
rm -rf "$dir"
for expected in "PinkNoiseTest.kt" "::error file=" "13 -> 9"; do
  if grep -qF -- "$expected" <<<"$message"; then
    echo "ok   — the failure message carries '$expected'"
  else
    echo "FAIL — the failure message does not carry '$expected'"
    echo "$message" | sed 's/^/       /'
    failures=$((failures + 1))
  fi
done

# Without the guard on it, a BASE_SHA misspelt in ci.yml would compare the
# branch against nothing and report green.
read -r dir base < <(prepare delete_file)
if ( cd "$dir" && env -u BASE_SHA bash "$check_script" ) >/dev/null 2>&1; then
  echo "FAIL — the check runs without BASE_SHA instead of refusing"
  failures=$((failures + 1))
else
  echo "ok   — a missing BASE_SHA is refused, not assumed"
fi
rm -rf "$dir"

if [ "$failures" -gt 0 ]; then
  echo "$failures test(s) failed"
  exit 1
fi
echo "all tests passed"
