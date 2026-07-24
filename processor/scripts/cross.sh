#!/usr/bin/env bash
# 交叉编译：为多个目标平台构建 release 二进制并打成归档到 dist/。
#
# 优先使用 `cross`（基于 Docker，链路最省心）；未安装则回退 `cargo build --target`
# （需自行 `rustup target add <triple>` 与对应 linker，失败的目标会跳过而非中断）。
#
# 用法：
#   ./scripts/cross.sh                 # 编译全部默认目标
#   ./scripts/cross.sh x86_64-unknown-linux-musl   # 只编译指定目标
# 环境变量：
#   TARGETS=“a b c”                    # 覆盖默认目标列表
set -euo pipefail
cd "$(dirname "$0")/.."

VERSION="$(grep -m1 '^version' Cargo.toml | sed -E 's/.*=[[:space:]]*"([^"]+)".*/\1/')"
DIST="dist"
mkdir -p "$DIST"

DEFAULT_TARGETS=(
  x86_64-unknown-linux-musl
  aarch64-unknown-linux-musl
  x86_64-pc-windows-gnu
  x86_64-apple-darwin
  aarch64-apple-darwin
)
TARGETS=("${@:-${TARGETS:-${DEFAULT_TARGETS[@]}}}")

if command -v cross >/dev/null 2>&1; then
  BUILD=(cross build --release -p rustto-cli)
  echo "==> 使用 cross（Docker）"
else
  BUILD=(cargo build --release -p rustto-cli)
  echo "==> 未发现 cross，回退 cargo build --target（需自行安装 target 与 linker）"
  echo "    安装 cross 可获得更省心的交叉编译： cargo install cross"
fi

archive() {
  local target="$1" src="target/$target/release/rustto-client"
  local pkg="$DIST/rustto-client-$VERSION-$target"
  if [[ "$target" == *pc-windows* ]]; then
    (cd "$(dirname "$src")" && zip -j "$pkg.zip" "$(basename "$src")") >/dev/null
    echo "    → $pkg.zip"
  else
    (cd "$(dirname "$src")" && tar -czf "$pkg.tar.gz" "$(basename "$src")")
    echo "    → $pkg.tar.gz"
  fi
}

ok=0; fail=0
for t in "${TARGETS[@]}"; do
  echo "==> [$t] 构建"
  if "${BUILD[@]}" --target "$t"; then
    archive "$t"
    ok=$((ok+1))
  else
    echo "    !! [$t] 构建失败，已跳过（可能缺少 rustup target / linker / macOS SDK）"
    fail=$((fail+1))
  fi
done

echo "完成：成功 $ok，跳过 $fail。归档目录： $DIST"
