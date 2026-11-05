#!/usr/bin/env bash
# Checks a GitHub release ($1, the API JSON) against what the in-app updater reads from
# releases/latest: the tag of this run, not a pre-release, both files uploaded and identical
# to the ones in out/. $2 is "draft" before publishing and "latest" after it.
set -euo pipefail

release="$1"
stage="$2"
apk="nookast-${VERSION:?}.apk"
fail() { echo "Release check ($stage): $1"; exit 1; }

[ "$(jq -r '.tag_name // empty' <<< "$release")" = "$GITHUB_REF_NAME" ] || fail "no release of $GITHUB_REF_NAME"
draft=false
if [ "$stage" = draft ]; then draft=true; fi
[ "$(jq -r '.draft' <<< "$release")" = "$draft" ] || fail "draft is not $draft"
[ "$(jq -r '.prerelease' <<< "$release")" = false ] || fail 'marked as a pre-release'
for file in "$apk" "$apk.sha256"; do
  asset=$(jq -c --arg name "$file" '.assets[] | select(.name == $name and .state == "uploaded")' <<< "$release")
  [ -n "$asset" ] || fail "$file is not uploaded"
  [ "$(jq -r '.size' <<< "$asset")" = "$(stat -c %s "out/$file")" ] || fail "$file differs from the built one"
  digest=$(jq -r '.digest // empty' <<< "$asset")
  if [ -n "$digest" ] && [ "$digest" != "sha256:$(sha256sum "out/$file" | cut -d ' ' -f 1)" ]; then
    fail "$file differs from the built one"
  fi
done
echo "Release check ($stage): $GITHUB_REF_NAME with $apk and $apk.sha256"
