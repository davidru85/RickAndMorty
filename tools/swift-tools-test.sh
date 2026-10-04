#!/usr/bin/env bash
# `TEST-UNIT-046` — the Swift quality gate's own regression suite (`TASK-099`, `B2-R02`).
#
# `GAP-023` recorded that the gate failed open: with neither tool resolvable it printed warnings
# and exited **0** on a nonempty input set, provisioning accepted any nonempty `xcrun` answer
# against the Xcode lock, and an unquoted `$FILES` expansion broke paths containing whitespace.
#
# The suite drives both scripts with controlled fake executables, so routing, the pins, the exit
# codes and path handling are proved without downloading anything or touching the real toolchain.
#
# Usage: tools/swift-tools-test.sh
# Exit: 0 every case behaves as specified, 1 at least one case did not.
set -u

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$SCRIPT_DIR/.."
LINT="$SCRIPT_DIR/swift-lint.sh"
SETUP="$SCRIPT_DIR/swift-tools-setup.sh"

WORK="$(mktemp -d)"
FAKE="$WORK/fake"
SRC="$WORK/src with space"
mkdir -p "$FAKE" "$SRC/Sources"
printf 'let a = 1\n' > "$SRC/Sources/A.swift"
printf 'let b = 2\n' > "$SRC/Sources/B.swift"

pass=0
fail=0

# A fake tool that answers `--version` with the given version and records the files it was handed,
# one per line, so a test can prove what the real invocation received.
fake_tool() {
    _path="$1"; _version="$2"
    cat > "$_path" <<EOF
#!/usr/bin/env bash
if [ "\${1:-}" = "--version" ]; then printf '%s\n' "$_version"; exit 0; fi
for _arg in "\$@"; do printf '%s\n' "\$_arg" >> "${_path}.args"; done
exit 0
EOF
    chmod +x "$_path"
}

check() {
    _name="$1"; _expected="$2"; _actual="$3"
    if [ "$_expected" = "$_actual" ]; then
        printf 'ok   %s\n' "$_name"
        pass=$((pass + 1))
    else
        printf 'FAIL %s (expected exit %s, got %s)\n' "$_name" "$_expected" "$_actual"
        fail=$((fail + 1))
    fi
}

run_lint() { # run_lint <description> <expected-exit> <env-assignments…>
    _name="$1"; _expected="$2"; shift 2
    ( cd "$ROOT_DIR" && env "$@" bash "$LINT" "$SRC" >/dev/null 2>&1 )
    check "$_name" "$_expected" "$?"
}

# --- exit codes ------------------------------------------------------------------------------

fake_tool "$FAKE/swiftlint-ok" "0.65.1"

# The reproduced `GAP-023` case: SwiftLint resolves nowhere and the input set is nonempty. The cache
# is pointed at an empty directory and PATH is stripped, so the seed is deterministic and does not
# depend on what the developer has provisioned. The gate must fail closed rather than warn its way
# to a green run (the shipped script exited 0 here).
( cd "$ROOT_DIR" && env -u SWIFTLINT "PATH=/usr/bin:/bin" "SWIFT_TOOLS_CACHE=$WORK/cache-empty" \
    bash "$LINT" "$SRC" >/dev/null 2>&1 )
check "an unresolvable SwiftLint on a nonempty set fails" 1 "$?"

# A pinned tool at the wrong version must not be accepted.
fake_tool "$FAKE/swiftlint-old" "0.60.0"
run_lint "an unpinned SwiftLint version fails" 1 \
    "SWIFTLINT=$FAKE/swiftlint-old"

# A fake at the pinned version passes both halves.
mkdir -p "$WORK/xcode/Toolchains/XcodeDefault.xctoolchain/usr/bin"
fake_tool "$WORK/xcode/Toolchains/XcodeDefault.xctoolchain/usr/bin/swift-format" "main"
run_lint "the pinned versions pass" 0 \
    "SWIFT_FORMAT=$WORK/xcode/Toolchains/XcodeDefault.xctoolchain/usr/bin/swift-format" \
    "SWIFTLINT=$FAKE/swiftlint-ok"

# The `xcode-27` runner installs `Xcode_27.0.app` as a **symlink** to `Xcode_27.app`, so
# `xcode-select -s /Applications/Xcode_27.0.app` records the link while `xcrun --find` reports the
# physical path. A membership test naming only the selected spelling rejected the runner's own
# formatter, which failed the `ios` job on every B7-B9 head (observed 2026-10-04). The fake
# toolchain below reproduces the condition exactly: `DEVELOPER_DIR` is the symlink, and the fake
# `xcrun` resolves it before printing, as the real one does.
SYMLINKED="$WORK/xcode-symlink"
mkdir -p "$SYMLINKED/Xcode_27.app/Contents/Developer/usr/bin" \
    "$SYMLINKED/Xcode_27.app/Contents/Developer/Toolchains/XcodeDefault.xctoolchain/usr/bin"
ln -sfn "$SYMLINKED/Xcode_27.app" "$SYMLINKED/Xcode_27.0.app"
cat > "$SYMLINKED/Xcode_27.app/Contents/Developer/usr/bin/xcodebuild" <<'XCB'
#!/usr/bin/env bash
printf '%s\n' "Xcode 27.0" "Build version 27A266a"
XCB
cat > "$SYMLINKED/Xcode_27.app/Contents/Developer/usr/bin/xcrun" <<'XCRUN'
#!/usr/bin/env bash
# `--find <tool>` in the active developer dir, resolved to its physical path first.
if [ "${1:-}" = "--find" ]; then
    _resolved="$(cd "${DEVELOPER_DIR:-/Applications/Xcode.app/Contents/Developer}" && pwd -P)"
    _candidate="$_resolved/Toolchains/XcodeDefault.xctoolchain/usr/bin/${2:-}"
    [ -x "$_candidate" ] || exit 1
    printf '%s\n' "$_candidate"
    exit 0
fi
exit 1
XCRUN
chmod +x "$SYMLINKED/Xcode_27.app/Contents/Developer/usr/bin/xcodebuild" \
    "$SYMLINKED/Xcode_27.app/Contents/Developer/usr/bin/xcrun"
fake_tool "$SYMLINKED/Xcode_27.app/Contents/Developer/Toolchains/XcodeDefault.xctoolchain/usr/bin/swift-format" "main"
( cd "$ROOT_DIR" && env "DEVELOPER_DIR=$SYMLINKED/Xcode_27.0.app/Contents/Developer" \
    "SWIFTLINT=$FAKE/swiftlint-ok" "SWIFT_TOOLS_CACHE=$WORK/cache-symlink" \
    "PATH=$SYMLINKED/Xcode_27.app/Contents/Developer/usr/bin:$PATH" \
    bash "$LINT" "$SRC" >/dev/null 2>&1 )
check "a symlinked toolchain spelling is accepted as the locked one" 0 "$?"

# A violation reported by a tool must be a failure of the gate. The tool that reports it is a
# fake whose lint invocation exits 1; `--version` still answers the pinned version.
cat > "$FAKE/swiftlint-violating" <<'EOF'
#!/usr/bin/env bash
if [ "${1:-}" = "--version" ]; then printf '%s\n' "0.65.1"; exit 0; fi
printf '%s\n' "Style violation" >&2
exit 1
EOF
chmod +x "$FAKE/swiftlint-violating"
( cd "$ROOT_DIR" && env "SWIFT_FORMAT=$WORK/xcode/Toolchains/XcodeDefault.xctoolchain/usr/bin/swift-format" \
    "SWIFTLINT=$FAKE/swiftlint-violating" bash "$LINT" "$SRC" >/dev/null 2>&1 )
check "a tool that reports a violation fails the gate" 1 "$?"

# An empty input set is its own failure, distinct from a violation.
EMPTY="$WORK/empty"
mkdir -p "$EMPTY"
( cd "$ROOT_DIR" && env "SWIFT_FORMAT=$WORK/xcode/Toolchains/XcodeDefault.xctoolchain/usr/bin/swift-format" \
    "SWIFTLINT=$FAKE/swiftlint-ok" bash "$LINT" "$EMPTY" >/dev/null 2>&1 )
check "an empty input set exits 2" 2 "$?"

# --- paths with whitespace -------------------------------------------------------------------

rm -f "$FAKE/swiftlint-ok.args"
( cd "$ROOT_DIR" && env "SWIFT_FORMAT=$WORK/xcode/Toolchains/XcodeDefault.xctoolchain/usr/bin/swift-format" \
    "SWIFTLINT=$FAKE/swiftlint-ok" bash "$LINT" "$SRC" >/dev/null 2>&1 )
expected_files="$(find "$SRC" -name '*.swift' -type f | sort | wc -l | tr -d ' ')"
actual_files="$(grep -c '\.swift$' "$FAKE/swiftlint-ok.args" 2>/dev/null | tr -d ' ')"
check "every file reaches the tool as its own argument" "$expected_files" "$actual_files"
if grep -q "$SRC/Sources/A.swift" "$FAKE/swiftlint-ok.args" 2>/dev/null; then
    printf 'ok   a path containing a space survives collection intact\n'
    pass=$((pass + 1))
else
    printf 'FAIL a path containing a space was split or lost\n'
    fail=$((fail + 1))
fi

# --- provisioning ----------------------------------------------------------------------------

# The lock pins Xcode 27.0; a different toolchain must be refused before any tool is accepted.
( cd "$ROOT_DIR" && env "DEVELOPER_DIR=$FAKE/absent-xcode" "SWIFT_TOOLS_CACHE=$WORK/cache" \
    bash "$SETUP" >/dev/null 2>&1 )
check "a toolchain other than the pinned Xcode fails provisioning" 1 "$?"

# A tampered checksum must fail rather than extract. The lock is copied so the real one is untouched,
# and the script's own directory is what it reads, so the copy is driven through a fake script root.
TAMPER="$WORK/tamper"
mkdir -p "$TAMPER"
cp "$SETUP" "$TAMPER/swift-tools-setup.sh"
sed 's/c1e429b0599cf1b516f369a2d9ec04eaf0e436f3c12b637df8851fa52ff694d0/'"$(printf '0%.0s' $(seq 64))"'/' \
    "$SCRIPT_DIR/swift-tools.lock" > "$TAMPER/swift-tools.lock"
( cd "$ROOT_DIR" && env "SWIFT_TOOLS_CACHE=$WORK/cache-tamper" bash "$TAMPER/swift-tools-setup.sh" >/dev/null 2>&1 )
check "a checksum mismatch fails provisioning" 1 "$?"

rm -rf "$WORK"
printf '\n%s passed, %s failed\n' "$pass" "$fail"
[ "$fail" -eq 0 ]
