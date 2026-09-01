#!/usr/bin/env bash
# 本地构建：编译 restto-client 并打印产物路径。
# 用法： ./scripts/build.sh [--dev]
#   --dev  关闭优化、保留调试信息（默认优化并剥离符号表）
set -euo pipefail
cd "$(dirname "$0")/.."

FLAGS=(-ldflags "-s -w")
if [[ "${1:-}" == "--dev" ]]; then
  FLAGS=()
fi

echo "==> go build ${FLAGS[*]:-} ./client/restto"
go build "${FLAGS[@]}" -o bin/restto-client ./client/restto

echo "构建完成： bin/restto-client"
./bin/restto-client --version || true
