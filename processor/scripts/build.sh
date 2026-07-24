#!/usr/bin/env bash
# 本地 release 构建：编译 rustto-client 并打印产物路径。
# 用法： ./scripts/build.sh [--dev]
#   --dev  走 debug profile（默认 release）
set -euo pipefail
cd "$(dirname "$0")/.."

PROFILE="release"
CARGO_FLAGS=(--release)
if [[ "${1:-}" == "--dev" ]]; then
  PROFILE="debug"
  CARGO_FLAGS=()
fi

echo "==> cargo build ${CARGO_FLAGS[*]:-} -p rustto-cli"
cargo build "${CARGO_FLAGS[@]}" -p rustto-cli

BIN="target/$PROFILE/rustto-client"
[[ -x "$BIN" ]] || BIN="target/debug/rustto-client"
echo "构建完成： $BIN"
"$BIN" --version || true
