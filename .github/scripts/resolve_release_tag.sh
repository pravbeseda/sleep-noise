#!/usr/bin/env bash
# Resolve the release tag a promotion or a rollout acts on.
#
# Usage: resolve_release_tag.sh [tag]
#
# An empty argument means the newest tag of the `v<name>+<code>` scheme, which
# is what a promotion almost always wants: workflow_dispatch can neither prefill
# an input nor offer a live list of tags, so the workflow answers the question
# instead of the operator copying a tag across. A tag that does not exist is
# refused here, by name, rather than further down as a checkout error.
#
# Shared by promote.yml and rollout.yml because the two must never disagree
# about which release they are touching. release.yml keeps its own copy of the
# ordering line: there it looks for the *previous* tag to compare against, and
# an empty answer is a first release rather than a refusal.
#
# Tested by resolve_release_tag.test.sh beside it.
set -euo pipefail

TAG="${1:-}"

if [ -z "$TAG" ]; then
    # Ordered by version code, which is monotonic, so this is the correct order
    # — and it avoids --sort=version:refname, which has no defined behaviour
    # for '+'. The 'v*+*' glob is what fences the scheme off from any other tag
    # the repository may carry: this one has no tag at all until release.yml
    # creates the first, but a hand-made `v1.0.4` would otherwise be read as a
    # release with no code.
    TAG=$(git tag --list 'v*+*' | awk -F+ '{print $2"\t"$0}' | sort -rn | head -1 | cut -f2)
    if [ -z "$TAG" ]; then
        echo "::error::no v*+* tag exists — nothing has been released through release.yml yet." >&2
        exit 1
    fi
    echo "no tag given — using the newest release, $TAG" >&2
fi

if ! git rev-parse -q --verify "refs/tags/$TAG^{commit}" >/dev/null; then
    echo "::error::tag $TAG does not exist." >&2
    exit 1
fi

echo "$TAG"
