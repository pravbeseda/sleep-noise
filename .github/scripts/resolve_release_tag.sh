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

# A named tag older than the newest is legitimate and is not refused: halting a
# production rollout while a newer release sits on beta is exactly the emergency
# this pipeline must not block, and it can only be named by its own tag. But it
# is also the one dispatch that can do damage, because Gradle Play Publisher
# does not verify that a version code is on the track it is promoting from — it
# rewrites every release on that track with the code it is given. So an older
# tag is warned about, loudly and at the moment it is resolved, rather than
# guarded against with a Play API call this script has no credentials for.
NEWEST=$(git tag --list 'v*+*' | awk -F+ '{print $2"\t"$0}' | sort -rn | head -1 | cut -f2)
if [ -n "$NEWEST" ] && [ "$TAG" != "$NEWEST" ]; then
    echo "::warning::$TAG is not the newest release — $NEWEST is. Acting on an older tag rewrites" \
         "whatever is on the track with the older version code, so do this only to halt or finish a" \
         "rollout that is genuinely still on $TAG." >&2
fi

echo "$TAG"
