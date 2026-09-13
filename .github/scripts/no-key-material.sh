#!/usr/bin/env bash
# Fails a pull request that adds key material: a keystore, a PKCS#12 bundle,
# anything under a .key/ directory, or google-services.json.
#
# Reads BASE_SHA — github.event.pull_request.base.sha — and compares the branch
# head against the merge base, for the reason no-deleted-tests.sh gives. Tested
# by no-key-material.test.sh beside it.
#
# gitleaks runs beside this and cannot stand in for it: it matches text, and the
# upload keystore is binary. Measured on this repository — gitleaks scanned
# .key/Drevo.Keystore and .key/output.zip and reported nothing. .gitignore keeps
# these paths out of an ordinary `git add`; this is what stops a `git add -f`,
# or a keystore saved somewhere .gitignore does not name.
#
# A path is judged by its name, never its content, so a document called
# keystore.md passes. There is no escape hatch: this repository is public, and a
# signing key that reaches its history cannot be taken back out of it.
set -euo pipefail

# No apostrophe in this message: bash reads one inside ${...} as an opening
# quote and the whole script stops parsing.
: "${BASE_SHA:?BASE_SHA, the base commit of the pull request, is required}"

base=$(git merge-base "$BASE_SHA" HEAD)

# Extended regex, matched case-insensitively: the upload key is Drevo.Keystore.
pattern='\.(keystore|jks|p12|pfx)$|(^|/)\.key/|(^|/)google-services\.json$'

found=0
while IFS= read -r file; do
  [ -n "$file" ] || continue
  grep -qiE "$pattern" <<<"$file" || continue
  found=1
  echo "::error file=$file::$file is key material and must never be committed: this repository is public, and its history keeps a key for good. Take it out of the branch with git rm --cached, rewrite the commit that added it, and keep the file where .gitignore already sends it."
done < <(git diff --name-only --no-renames --diff-filter=d "$base" HEAD)

if [ "$found" -eq 0 ]; then
  echo "No key material added."
fi
exit "$found"
