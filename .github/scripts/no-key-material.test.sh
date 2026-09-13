#!/usr/bin/env bash
# Tests for no-key-material.sh, run by the Guardrails job before the check
# itself. Nothing else on CI exercises them, and this check fails silently when
# it is wrong: a pattern that misses a keystore still reports green.
set -euo pipefail

here=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
check_script="$here/no-key-material.sh"
failures=0

# Every run of the check sees git's defaults as the runner has them, whatever
# this machine's global config says: core.quotePath=false there once hid a
# keystore under a non-ASCII path from every local run.
run_check() { # <dir> <base>
  ( cd "$1" && GIT_CONFIG_COUNT=1 GIT_CONFIG_KEY_0=core.quotePath GIT_CONFIG_VALUE_0=true \
      BASE_SHA="$2" bash "$check_script" 2>&1 )
}

# A repository with a little of this one's shape: a module, the composite action
# whose directory name is google-services, and prose.
fixture() {
  local dir; dir=$(mktemp -d)
  git -C "$dir" init --quiet
  git -C "$dir" config user.email t@example.com
  git -C "$dir" config user.name test
  mkdir -p "$dir/app/src/main" "$dir/.github/actions/google-services"
  echo "<manifest/>" > "$dir/app/src/main/AndroidManifest.xml"
  echo "name: google-services" > "$dir/.github/actions/google-services/action.yml"
  echo base > "$dir/CLAUDE.md"
  git -C "$dir" add -A
  git -C "$dir" commit --quiet -m base
  echo "$dir"
}

# Prints "<dir> <base sha>" for a branch that applied <mutation> on top of base.
prepare() { # <branch-mutation>
  local dir; dir=$(fixture)
  local base; base=$(git -C "$dir" rev-parse HEAD)
  git -C "$dir" checkout --quiet -b pr
  ( cd "$dir" && "$1" )
  # -f, because the point is a file .gitignore would have kept out.
  git -C "$dir" add -A -f
  git -C "$dir" commit --quiet --allow-empty -m branch
  echo "$dir $base"
}

# check <name> <pass|fail> <branch-mutation>
check() {
  local name=$1 expected=$2 mutation=$3
  local dir base; read -r dir base < <(prepare "$mutation")

  local actual=pass output
  output=$(run_check "$dir" "$base") || actual=fail

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
add_prose()            { echo notes > docs.md; }
add_upload_key()       { mkdir -p .key && printf '\xfe\xed\xfe\xed' > .key/Drevo.Keystore; }
add_other_key_file()   { mkdir -p .key && echo zip > .key/output.zip; }
add_jks_elsewhere()    { printf '\xfe\xed\xfe\xed' > app/release.jks; }
add_p12()              { printf '0\x82' > upload.p12; }
add_upper_case_ext()   { printf '\xfe\xed\xfe\xed' > app/UPLOAD.JKS; }
add_google_services()  { echo '{"project_info":{}}' > app/google-services.json; }
add_nested_key_dir()   { mkdir -p app/.key && echo secret > app/.key/notes.txt; }
# Names that merely mention key material are prose and configuration, not keys.
add_keystore_doc()     { echo "how to rotate" > keystore.md; }
edit_the_action()      { echo "# edited" >> .github/actions/google-services/action.yml; }
add_similar_dir()      { mkdir -p app/src/main/keyboard && echo x > app/src/main/keyboard/.keep; }
# git quotes a path holding a non-ASCII byte, so a pattern anchored at the end
# of the name meets a closing quote instead of the extension.
add_non_ascii_path()   { mkdir -p ключи && printf '\xfe\xed\xfe\xed' > ключи/upload.jks; }
# The pull request's history keeps the key even though its final tree does not.
add_then_delete()      { printf '\xfe\xed\xfe\xed' > app/transient.jks
                         git add -f app/transient.jks && git commit --quiet -m "add a key"
                         rm app/transient.jks; }

check "an untouched branch passes"                 pass nothing
check "added prose passes"                         pass add_prose
check "the upload keystore under .key/ fails"      fail add_upload_key
check "any file under .key/ fails"                 fail add_other_key_file
check "a .jks outside .key/ fails"                 fail add_jks_elsewhere
check "a PKCS#12 bundle fails"                     fail add_p12
check "an upper-case extension fails too"          fail add_upper_case_ext
check "google-services.json fails"                 fail add_google_services
check "a .key/ directory deeper in the tree fails" fail add_nested_key_dir
check "a document named after keystores passes"   pass add_keystore_doc
check "the google-services action itself passes"   pass edit_the_action
check "a directory that only starts with key passes" pass add_similar_dir
check "a key under a non-ASCII path fails"         fail add_non_ascii_path
check "a key added and deleted within the PR fails" fail add_then_delete

# The message is half the check: a red job has to say which file, on the file.
read -r dir base < <(prepare add_jks_elsewhere)
message=$(run_check "$dir" "$base" || true)
rm -rf "$dir"
for expected in "::error file=app/release.jks::" "git rm --cached"; do
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
read -r dir base < <(prepare add_upload_key)
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
