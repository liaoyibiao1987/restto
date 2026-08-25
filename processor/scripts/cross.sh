#!/usr/bin/env bash
# 交叉编译：为多个目标平台构建优化二进制并打成归档到 dist/。
#
# Go 原生支持交叉编译：GOOS/GOARCH 直接切换，无需外部交叉工具链（CGO 关闭），
# 产物为对应平台的静态可执行文件。
#
# 用法：
#   ./scripts/cross.sh                              # 编译全部默认目标
#   ./scripts/cross.sh linux/amd64                  # 只编译指定目标（GOOS/GOARCH）
# 环境变量：
#   TARGETS="linux/amd64 linux/arm64"               # 覆盖默认目标列表
set -euo pipefail
cd "$(dirname "$0")/.."

# 版本号读取 .env.example 的 CLIENT_VERSION（与运行时默认一致）。
VERSION="$(grep -m1 '^CLIENT_VERSION=' .env.example 2>/dev/null | cut -d= -f2)"
VERSION="${VERSION:-0.1.0}"
DIST="dist"
mkdir -p "$DIST"

DEFAULT_TARGETS=(
  linux/amd64
  linux/arm64
  linux/386
  windows/amd64
  darwin/amd64
  darwin/arm64
)
TARGETS=("${@:-${TARGETS:-${DEFAULT_TARGETS[@]}}}")

# 把 GOOS/GOARCH 映射为归档命名（对齐原 Rust target 风格可读性）。
target_name() {
  local goos="$1" goarch="$2"
  case "$goos:$goarch" in
    linux:amd64)  echo "x86_64-unknown-linux-musl" ;;
    linux:arm64)  echo "aarch64-unknown-linux-musl" ;;
    linux:386)    echo "i686-unknown-linux-musl" ;;
    windows:amd64) echo "x86_64-pc-windows-gnu" ;;
    darwin:amd64)  echo "x86_64-apple-darwin" ;;
    darwin:arm64)  echo "aarch64-apple-darwin" ;;
    *)             echo "${goos}-${goarch}" ;;
  esac
}

ok=0; fail=0
for t in "${TARGETS[@]}"; do
  goos="${t%%/*}"
  goarch="${t##*/}"
  name="$(target_name "$goos" "$goarch")"
  bin="rustto-client"
  [[ "$goos" == "windows" ]] && bin="rustto-client.exe"
  out="dist-bin/${goos}-${goarch}"

  echo "==> [$t] ($name)"
  if CGO_ENABLED=0 GOOS="$goos" GOARCH="$goarch" \
      go build -ldflags "-s -w" -trimpath -o "$out/$bin" ./cmd/rustto-client; then
    pkg="$DIST/rustto-client-$VERSION-$name"
    if [[ "$goos" == "windows" ]] && command -v zip >/dev/null 2>&1; then
      zip -j "$pkg.zip" "$out/$bin" >/dev/null
      echo "    → $pkg.zip"
    else
      [[ "$goos" == "windows" ]] && echo "    ! 未安装 zip，改打 .tar.gz（Windows 10+ 自带 tar 可解）"
      tar -czf "$pkg.tar.gz" -C "$out" "$bin"
      echo "    → $pkg.tar.gz"
    fi
    ok=$((ok+1))
  else
    echo "    !! [$t] 构建失败，已跳过"
    fail=$((fail+1))
  fi
done

echo "完成：成功 $ok，失败 $fail。归档目录： $DIST"
# 仅当真正构建失败时以非零退出
[[ "$fail" -gt 0 ]] && exit 1 || exit 0
