#!/usr/bin/env bash
# Generate `iosApp/App/Version.xcconfig` from the repository's single `VERSION` source.
#
# `DEC-067` splits the version acceptance: `TASK-018` owns the source file and the Android wiring;
# this script is the iOS half (`AC-REQ-NFR-006-3`, `TASK-051`). The xcconfig is the seam between the
# one source and the Xcode target, and it is generated rather than hand-edited so
# `CFBundleShortVersionString` cannot drift from `VERSION`.
#
# Usage: tools/ios-version.sh [--check]
#   (no flag) writes the xcconfig
#   --check   fails when the committed xcconfig disagrees with VERSION
set -eu

REPOSITORY_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
VERSION_FILE="$REPOSITORY_ROOT/VERSION"
XCCONFIG="$REPOSITORY_ROOT/iosApp/App/Version.xcconfig"

[ -f "$VERSION_FILE" ] || { echo "ERROR: $VERSION_FILE does not exist" >&2; exit 1; }

# The same shape `verifyDependencyPins` P9 enforces on VERSION: one MAJOR.MINOR.PATCH line.
VALUE="$(tr -d '[:space:]' < "$VERSION_FILE")"
case "$VALUE" in
    *[!0-9.]*|.*|*.) echo "ERROR: VERSION is not MAJOR.MINOR.PATCH: '$VALUE'" >&2; exit 1 ;;
esac
[ "$(printf '%s' "$VALUE" | awk -F. '{print NF}')" = "3" ] || {
    echo "ERROR: VERSION must have exactly three components: '$VALUE'" >&2; exit 1
}

GENERATED="// GENERATED from the repository's single \`VERSION\` source by tools/ios-version.sh.
//
// Do not edit by hand: \`VERSION\` is the single source (\`DEC-067\`, \`AC-REQ-NFR-006-3\`), and
// \`tools/ios-version.sh --check\` fails when this file disagrees with it.
MULTIVERSE_VERSION = $VALUE"

if [ "${1:-}" = "--check" ]; then
    CURRENT="$(cat "$XCCONFIG" 2>/dev/null || true)"
    if [ "$CURRENT" != "$GENERATED" ]; then
        echo "ERROR: iosApp/App/Version.xcconfig disagrees with VERSION ('$VALUE')." >&2
        echo "Run tools/ios-version.sh to regenerate it." >&2
        exit 1
    fi
    echo "iosVersion: iosApp/App/Version.xcconfig matches VERSION ($VALUE)"
    exit 0
fi

printf '%s\n' "$GENERATED" > "$XCCONFIG"
echo "iosVersion: wrote iosApp/App/Version.xcconfig from VERSION ($VALUE)"
