#!/usr/bin/env bash
# Provision the pinned Swift tools (TASK-030, `DEC-076`; hardened by `TASK-099`, `B2-R02`).
#
# Downloads each artifact named in `tools/swift-tools.lock`, verifies its SHA-256, and fails on a
# mismatch or a missing checksum. Tools provided by the Xcode toolchain are resolved, not
# downloaded, and their pin is the Xcode version the lock records — which is established from the
# toolchain's own version information, never accepted as "some nonempty answer from `xcrun`"
# (`GAP-023`).
#
# The provisioned binaries live under `tools/.cache`, where `swift-lint.sh` looks for them, so a
# provisioned SwiftLint is found without the caller editing `PATH`.
#
# Exit: 0 provisioned, 1 any failure.
set -eu

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
LOCK="$SCRIPT_DIR/swift-tools.lock"
CACHE_DIR="${SWIFT_TOOLS_CACHE:-$SCRIPT_DIR/.cache}"
mkdir -p "$CACHE_DIR"

fail() { echo "ERROR: $*" >&2; exit 1; }

# The Xcode version this toolchain provides, from Apple's own version output. `DEVELOPER_DIR` wins
# because that is the toolchain `xcrun` will use.
xcode_version() {
    _xcodebuild="${DEVELOPER_DIR:-/Applications/Xcode.app/Contents/Developer}/usr/bin/xcodebuild"
    [ -x "$_xcodebuild" ] || _xcodebuild="$(command -v xcodebuild 2>/dev/null || true)"
    [ -n "$_xcodebuild" ] || return 1
    "$_xcodebuild" -version 2>/dev/null | awk '/^Xcode /{ print $2; found = 1 } END { exit !found }'
}

ACTUAL_XCODE="$(xcode_version || true)"

while read -r tool version url sha256; do
    case "$tool" in ''|'#'*) continue ;; esac

    if [ "$sha256" = "-" ]; then
        # A toolchain-provided tool: the lock pins the Xcode that ships it, and the selected
        # toolchain must be that version before the tool is accepted.
        case "$url" in
            xcrun:*)
                pin="${version#@Xcode-}"
                [ -n "$ACTUAL_XCODE" ] ||
                    fail "could not establish the Xcode version; $tool is pinned to $pin"
                [ "$ACTUAL_XCODE" = "$pin" ] ||
                    fail "$tool is pinned to Xcode $pin but the selected toolchain is $ACTUAL_XCODE"
                resolved="$(xcrun --find "${url#xcrun:}" 2>/dev/null || true)"
                [ -n "$resolved" ] || fail "$tool is not provided by this Xcode toolchain ($version)"
                [ -x "$resolved" ] || fail "$tool resolved to $resolved, which is not executable"
                echo "$tool: $resolved ($version, Xcode $pin verified)"
                ;;
            *) fail "no checksum and no resolution rule for $tool" ;;
        esac
        continue
    fi

    archive="$CACHE_DIR/$(basename "$url")"
    if [ ! -f "$archive" ]; then
        curl -fsSL --retry 3 -o "$archive" "$url" || fail "could not download $url"
    fi

    # The checksum is verified before anything is extracted, so a tampered or truncated artifact is
    # never unpacked (TASK-099).
    actual="$(shasum -a 256 "$archive" | awk '{print $1}')"
    if [ "$actual" != "$sha256" ]; then
        rm -f "$archive"
        fail "$tool checksum mismatch: expected $sha256, got $actual (the artifact was removed; do not reuse it)"
    fi

    bin_dir="$CACHE_DIR/$tool-$version"
    mkdir -p "$bin_dir"
    case "$archive" in
        *.zip) unzip -oq "$archive" -d "$bin_dir" ;;
        *) fail "no extraction rule for $(basename "$archive")" ;;
    esac
    # The archive may nest its payload in a directory; the lint script looks for a flat binary, so
    # a nested one is flattened into the versioned directory.
    if [ ! -x "$bin_dir/$tool" ]; then
        nested="$(find "$bin_dir" -type f -name "$tool" -perm -u+x | head -1)"
        [ -n "$nested" ] || fail "$tool was extracted but no executable named $tool exists under $bin_dir"
        cp "$nested" "$bin_dir/$tool"
    fi
    chmod +x "$bin_dir/$tool" 2>/dev/null || true
    echo "$tool: $bin_dir/$tool ($version, sha256 verified)"
done < "$LOCK"
