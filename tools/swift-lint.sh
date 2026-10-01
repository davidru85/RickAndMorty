#!/usr/bin/env bash
# Swift quality gate (TASK-030, DEC-076): swift-format and SwiftLint over the iOS Swift sources.
#
# Both tools fail open in ways a naive gate would hide, so this script states the contract:
#
# - `swift-format lint` exits 0 even when it reports violations, so it runs with `--strict`;
# - `swiftlint lint` exits 0 with `--quiet`, so the flag is never used here;
# - both exit 0 for an input set that matches nothing, so an empty set is the gate's own failure.
#
# Usage: tools/swift-lint.sh [root]      (default root: iosApp)
# Exit: 0 clean, 1 a violation, 2 the input set is empty.
set -eu

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="${1:-iosApp}"
CONFIG_FORMAT="$SCRIPT_DIR/../.swift-format"
CONFIG_LINT="$SCRIPT_DIR/../.swiftlint.yml"

# Resolve each tool: an explicit override, then PATH, then the Xcode toolchain.
resolve() {
    _override="$1"; _name="$2"; shift 2
    if [ -n "$_override" ]; then printf '%s' "$_override"; return; fi
    if command -v "$_name" >/dev/null 2>&1; then command -v "$_name"; return; fi
    "$@" 2>/dev/null || true
}

SWIFT_FORMAT="$(resolve "${SWIFT_FORMAT:-}" swift-format xcrun --find swift-format)"
SWIFTLINT="$(resolve "${SWIFTLINT:-}" swiftlint true)"

if [ ! -d "$ROOT_DIR" ]; then
    echo "ERROR: no Swift source root at $ROOT_DIR; refusing to report success." >&2
    exit 2
fi

# POSIX-sh collection: `mapfile` needs bash 4 and the macOS runner ships bash 3.2.
FILES="$(find "$ROOT_DIR" -name '*.swift' -type f 2>/dev/null | sort)"
if [ -z "$FILES" ]; then
    echo "ERROR: no Swift file under $ROOT_DIR; an empty input set is a failure, not a pass (DEC-071)." >&2
    exit 2
fi

status=0
if [ -n "$SWIFT_FORMAT" ]; then
    # shellcheck disable=SC2086
    "$SWIFT_FORMAT" lint --strict --configuration "$CONFIG_FORMAT" $FILES || status=1
else
    echo "WARN: swift-format was not found; the formatting half did not run." >&2
fi

if [ -n "$SWIFTLINT" ]; then
    # shellcheck disable=SC2086
    "$SWIFTLINT" lint --strict --config "$CONFIG_LINT" $FILES || status=1
else
    echo "WARN: swiftlint was not found; the lint half did not run." >&2
fi

exit "$status"
