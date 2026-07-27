#!/usr/bin/env bash
# 交叉编译：为多个目标平台构建 release 二进制并打成归档到 dist/。
#
# 后端选择：
#   - 优先 `cross`（基于 Docker，链路最省心，能编译所有目标）。
#     仅当 cross 已装 **且** Docker daemon 在线时启用。
#   - 否则回退 `cargo build --target`：此时只有"本机链接器能处理"的目标可编译，
#     不满足条件的目标会被优雅跳过并打印可操作的修复提示（不刷链接错误）。
#
# 用法：
#   ./scripts/cross.sh                              # 编译全部默认目标（不可行的自动跳过）
#   ./scripts/cross.sh x86_64-unknown-linux-musl    # 只编译指定目标
# 环境变量：
#   TARGETS="a b c"                                 # 覆盖默认目标列表
#   FORCE=1                                         # 跳过预检，强制尝试每个目标
set -euo pipefail
cd "$(dirname "$0")/.."

VERSION="$(grep -m1 '^version' Cargo.toml | sed -E 's/.*=[[:space:]]*"([^"]+)".*/\1/')"
DIST="dist"
mkdir -p "$DIST"

DEFAULT_TARGETS=(
  x86_64-unknown-linux-musl
  aarch64-unknown-linux-musl
  x86_64-pc-windows-gnu
)
TARGETS=("${@:-${TARGETS:-${DEFAULT_TARGETS[@]}}}")
FORCE="${FORCE:-0}"

# 选构建后端：cross 需要 cross 且 docker daemon 在线，否则回退 cargo。
USE_CROSS=0
if command -v cross >/dev/null 2>&1 && docker info >/dev/null 2>&1; then
  USE_CROSS=1
  BUILD=(cross build --release -p rustto-cli)
  echo "==> 使用 cross（Docker），可编译全部目标"
else
  BUILD=(cargo build --release -p rustto-cli)
  if command -v cross >/dev/null 2>&1; then
    echo "==> 发现 cross 但 Docker daemon 不可用，回退宿主 cargo build --target"
  else
    echo "==> 未发现 cross，使用宿主 cargo build --target"
  fi
  echo "    仅本机链接器能处理的目标可编译，其余将预检跳过（FORCE=1 可强制尝试）"
  echo "    想一次编译所有目标：启动 Docker 后用 cross"
fi

# 返回某 target 所需的外部链接器名；为空表示无需外部链接器（本机 lld self-contained 即可）。
# 注：*-linux-musl 系列由 .cargo/config.toml 走 rust-lld + bundled crt，无需系统交叉 gcc；
#     产物为静态 ELF，可直接入 docker / 任意 Linux 运行。
target_linker() {
  case "$1" in
    x86_64-pc-windows-gnu) printf '%s\n' "x86_64-w64-mingw32-gcc" ;;
    *-apple-darwin)        printf '%s\n' "osxcross" ;;   # Linux 上交叉到 macOS 需 osxcross + SDK
    *)                     printf '\n' ;;                # 含 *-linux-musl（self-contained）
  esac
}

# 目标可行性预检（仅回退 cargo 时执行；cross 走 docker 无需预检）。
# 返回 0=可编译，1=跳过；跳过时打印可操作的修复提示。
preflight() {
  local t="$1"

  # 1) rustup target std 是否已安装
  if ! rustup target list --installed 2>/dev/null | grep -qx "$t"; then
    echo "    !! [$t] 跳过：未安装 target std → rustup target add $t"
    return 1
  fi

  # 2) 外部链接器是否存在
  local linker; linker="$(target_linker "$t")"
  if [[ -n "$linker" ]] && ! command -v "$linker" >/dev/null 2>&1; then
    echo "    !! [$t] 跳过：缺少链接器 '$linker'"
    case "$t" in
      x86_64-pc-windows-gnu) echo "       apt install gcc-mingw-w64-x86-64（稳定版 rust self-contained 缺完整 mingw 运行时库，需系统 mingw）" ;;
      *-apple-darwin)        echo "       Linux 上交叉到 macOS 需 osxcross + macOS SDK；建议在 macOS 上构建或用 cross+Docker" ;;
    esac
    return 1
  fi

  return 0
}

archive() {
  local target="$1" bin="rustto-client"
  [[ "$target" == *pc-windows* ]] && bin="rustto-client.exe"
  local src="target/$target/release"
  local pkg="$DIST/rustto-client-$VERSION-$target"
  # 用 tar -C / zip -j 直接指定源目录，不切 cwd（避免输出相对路径找不到 dist/）
  if [[ "$target" == *pc-windows* ]] && command -v zip >/dev/null 2>&1; then
    zip -j "$pkg.zip" "$src/$bin" >/dev/null
    echo "    → $pkg.zip"
  else
    [[ "$target" == *pc-windows* ]] && echo "    ! 未安装 zip，改打 .tar.gz（Windows 10+ 自带 tar 可解）"
    tar -czf "$pkg.tar.gz" -C "$src" "$bin"
    echo "    → $pkg.tar.gz"
  fi
}

ok=0; skip=0; fail=0
for t in "${TARGETS[@]}"; do
  echo "==> [$t]"

  # cross 走 docker，不做本机预检；回退 cargo 时先预检（除非 FORCE=1）
  if [[ "$USE_CROSS" -eq 0 && "$FORCE" -ne 1 ]]; then
    if ! preflight "$t"; then
      skip=$((skip+1)); continue
    fi
  fi

  if "${BUILD[@]}" --target "$t"; then
    archive "$t"
    ok=$((ok+1))
  else
    echo "    !! [$t] 构建失败，已跳过"
    fail=$((fail+1))
  fi
done

echo "完成：成功 $ok，预检跳过 $skip，构建失败 $fail。归档目录： $DIST"
# 仅当真正构建失败时以非零退出；预检跳过属于优雅降级，不影响退出码
[[ "$fail" -gt 0 ]] && exit 1 || exit 0
