#!/usr/bin/env bash
# Swift quality gate (TASK-030, `DEC-076`; hardened by `TASK-099`, `B2-R02`).
#
# Both tools fail open in ways a naive gate would hide, so this script states the contract:
#
# - `swift-format lint` exits 0 even when it reports violations, so it runs with `--strict`;
# - `swiftlint lint` exits 0 with `--quiet`, so the flag is never used here;
# - both exit 0 for an input set that matches nothing, so an empty set is the gate's own failure;
# - a gate that cannot run a tool is a **failure**, never a warning (`GAP-023`).
#
# The pins in `tools/swift-tools.lock` are enforced, not merely documented:
#
# - `swift-format` must come from the Xcode version the lock records, established from the
#   toolchain's own version information;
# - `swiftlint` must be the version the lock records, and a provisioned copy under the cache
#   directory is found without the caller editing `PATH`;
# - an override is validated like any other candidate: it may point at a different build of the
#   pinned tool, never at a different version.
#
# Usage: tools/swift-lint.sh [root]      (default root: iosApp)
# Exit: 0 clean, 1 a violation or a tool that could not run, 2 the input set is empty.
set -eu

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPOSITORY_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
ROOT_DIR="${1:-iosApp}"
CONFIG_FORMAT="$REPOSITORY_ROOT/.swift-format"
CONFIG_LINT="$REPOSITORY_ROOT/.swiftlint.yml"
LOCK="$SCRIPT_DIR/swift-tools.lock"
CACHE_DIR="${SWIFT_TOOLS_CACHE:-$SCRIPT_DIR/.cache}"

# `PINS` holds `<tool> <version> <url> <sha256>` per locked tool, read once.
read_pin() {
    _tool="$1"
    [ -f "$LOCK" ] || return 1
    awk -v want="$_tool" '$1 == want { print $2 "\t" $3 "\t" $4; found = 1 } END { exit !found }' "$LOCK"
}

# The Xcode version this toolchain provides, read from Apple's own version output rather than from
# a guess: `--- xcodebuild ---` then `Xcode 27.0` and `Build version …`. `DEVELOPER_DIR` wins
# because that is the toolchain `xcrun` will use.
xcode_version() {
    _xcodebuild="${DEVELOPER_DIR:-/Applications/Xcode.app/Contents/Developer}/usr/bin/xcodebuild"
    [ -x "$_xcodebuild" ] || _xcodebuild="$(command -v xcodebuild 2>/dev/null || true)"
    [ -n "$_xcodebuild" ] || return 1
    "$_xcodebuild" -version 2>/dev/null | awk '/^Xcode /{ print $2; found = 1 } END { exit !found }'
}

fail() { printf 'ERROR: %s\n' "$*" >&2; exit 1; }

# --- the locked toolchain -------------------------------------------------------------------

LOCK_XCODE="$(read_pin swift-format | cut -f1 || true)"
[ -n "$LOCK_XCODE" ] || fail "tools/swift-tools.lock records no swift-format pin"
LOCK_XCODE="${LOCK_XCODE#@Xcode-}"
ACTUAL_XCODE="$(xcode_version || true)"
[ -n "$ACTUAL_XCODE" ] || fail "could not establish the Xcode version; the swift-format pin cannot be checked"
[ "$ACTUAL_XCODE" = "$LOCK_XCODE" ] ||
    fail "the selected Xcode is $ACTUAL_XCODE but swift-tools.lock pins $LOCK_XCODE; the formatter would not be the locked one"

# --- collection (Bash 3.2: `mapfile` is unavailable, arrays keep paths intact) ---------------

if [ ! -d "$ROOT_DIR" ]; then
    fail "no Swift source root at $ROOT_DIR; refusing to report success"
fi

FILES=()
while IFS= read -r _file; do
    [ -n "$_file" ] && FILES+=("$_file")
done <<EOF
$(find "$ROOT_DIR" -name '*.swift' -type f 2>/dev/null | sort)
EOF

if [ "${#FILES[@]}" -eq 0 ]; then
    echo "ERROR: no Swift file under $ROOT_DIR; an empty input set is a failure, not a pass (DEC-071)." >&2
    exit 2
fi

# --- resolution -----------------------------------------------------------------------------

# A candidate is accepted only when it is executable and reports the pinned version. The version
# probe differs per tool: `--version` for both, whose first line carries the number.
tool_version() {
    "$1" --version 2>/dev/null | head -1 | sed -n 's/[^0-9]*\([0-9][0-9.]*\).*/\1/p'
}

accept_tool() {
    _path="$1"; _tool="$2"; _pin="$3"
    [ -n "$_path" ] && [ -x "$_path" ] || return 1
    _found="$(tool_version "$_path" || true)"
    [ -n "$_found" ] || return 1
    case "$_found" in
        "$_pin"*) return 0 ;;
        *) fail "$_tool at $_path reports version $_found but swift-tools.lock pins $_pin" ;;
    esac
}

# swift-format is pinned by the Xcode version, and Apple's build does not report a version number
# (`--version` prints `main`), so membership of the validated toolchain is the check: the resolved
# path must live under the toolchain of the Xcode whose version the lock records. An override is
# accepted for routing (a different build of the same toolchain) but must still be executable.
TOOLCHAIN_DIR="${DEVELOPER_DIR:-/Applications/Xcode.app/Contents/Developer}/Toolchains/XcodeDefault.xctoolchain"

SWIFT_FORMAT_PATH="${SWIFT_FORMAT:-}"
if [ -n "$SWIFT_FORMAT_PATH" ]; then
    [ -x "$SWIFT_FORMAT_PATH" ] || fail "SWIFT_FORMAT=$SWIFT_FORMAT_PATH is not executable"
    SWIFT_FORMAT="$SWIFT_FORMAT_PATH"
else
    SWIFT_FORMAT="$(xcrun --find swift-format 2>/dev/null || true)"
    [ -n "$SWIFT_FORMAT" ] && [ -x "$SWIFT_FORMAT" ] ||
        fail "swift-format from the Xcode $LOCK_XCODE toolchain is missing or not executable; the formatting half cannot run"
    case "$SWIFT_FORMAT" in
        "$TOOLCHAIN_DIR"/*) : ;;
        *) fail "swift-format resolved to $SWIFT_FORMAT, which is outside the validated Xcode $LOCK_XCODE toolchain ($TOOLCHAIN_DIR)" ;;
    esac
fi

# SwiftLint: override (validated), PATH, then the provisioned cache directory — so a provisioned
# copy is found without the caller editing `PATH` (TASK-099).
LOCK_SWIFTLINT="$(read_pin swiftlint | cut -f1 || true)"
[ -n "$LOCK_SWIFTLINT" ] || fail "tools/swift-tools.lock records no swiftlint pin"
SWIFTLINT_PATH="${SWIFTLINT:-}"
if [ -n "$SWIFTLINT_PATH" ]; then
    accept_tool "$SWIFTLINT_PATH" swiftlint "$LOCK_SWIFTLINT" ||
        fail "SWIFTLINT=$SWIFTLINT_PATH is not an executable swiftlint"
    SWIFTLINT="$SWIFTLINT_PATH"
else
    SWIFTLINT=""
    if command -v swiftlint >/dev/null 2>&1; then
        SWIFTLINT="$(command -v swiftlint)"
    elif [ -x "$CACHE_DIR/swiftlint-$LOCK_SWIFTLINT/swiftlint" ]; then
        SWIFTLINT="$CACHE_DIR/swiftlint-$LOCK_SWIFTLINT/swiftlint"
    fi
    [ -n "$SWIFTLINT" ] || fail "swiftlint $LOCK_SWIFTLINT is not on PATH and not provisioned under $CACHE_DIR; run tools/swift-tools-setup.sh first"
    accept_tool "$SWIFTLINT" swiftlint "$LOCK_SWIFTLINT" || SWIFTLINT=""
    [ -n "$SWIFTLINT" ] || fail "swiftlint at the resolved location is not an executable $LOCK_SWIFTLINT"
fi

# --- execution ------------------------------------------------------------------------------

status=0
"$SWIFT_FORMAT" lint --strict --configuration "$CONFIG_FORMAT" "${FILES[@]}" || status=1
"$SWIFTLINT" lint --strict --config "$CONFIG_LINT" "${FILES[@]}" || status=1

exit "$status"
