#!/usr/bin/env bash
# 本地构建：编译 rustto-client 并打印产物路径。
# 用法： ./scripts/build.sh [--dev]
#   --dev  关闭优化、保留调试信息（默认优化并剥离符号表）
set -euo pipefail
cd "$(dirname "$0")/.."

FLAGS=(-ldflags "-s -w")
if [[ "${1:-}" == "--dev" ]]; then
  FLAGS=()
fi

echo "==> go build ${FLAGS[*]:-} ./cmd/rustto-client"
go build "${FLAGS[@]}" -o bin/rustto-client ./cmd/rustto-client

echo "构建完成： bin/rustto-client"
./bin/rustto-client --version || true
