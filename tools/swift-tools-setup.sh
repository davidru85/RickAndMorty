#!/usr/bin/env bash
# Provision the pinned Swift tools (TASK-030, DEC-076).
#
# Downloads each artifact named in `tools/swift-tools.lock`, verifies its SHA-256, and fails on a
# mismatch or a missing checksum. Tools provided by the Xcode toolchain are resolved, not
# downloaded, and their pin is the Xcode version the lock records.
set -eu

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
LOCK="$SCRIPT_DIR/swift-tools.lock"
CACHE_DIR="${SWIFT_TOOLS_CACHE:-$SCRIPT_DIR/.cache}"
mkdir -p "$CACHE_DIR"

fail() { echo "ERROR: $*" >&2; exit 1; }

while read -r tool version url sha256; do
    case "$tool" in ''|'#'*) continue ;; esac

    if [ "$sha256" = "-" ]; then
        # A toolchain-provided tool: assert it resolves and report the toolchain it came from.
        case "$url" in
            xcrun:*)
                resolved="$(xcrun --find "${url#xcrun:}" 2>/dev/null || true)"
                [ -n "$resolved" ] || fail "$tool is not provided by this Xcode toolchain ($version)"
                echo "$tool: $resolved ($version)"
                ;;
            *) fail "no checksum and no resolution rule for $tool" ;;
        esac
        continue
    fi

    archive="$CACHE_DIR/$(basename "$url")"
    if [ ! -f "$archive" ]; then
        curl -fsSL --retry 3 -o "$archive" "$url" || fail "could not download $url"
    fi

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
    chmod +x "$bin_dir"/* 2>/dev/null || true
    echo "$tool: $bin_dir ($version, sha256 verified)"
done < "$LOCK"
