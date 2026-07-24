#!/usr/bin/env bash
# 打包：本地 release 构建，再把二进制 + 文档 + .env.example 打成版本化归档到 dist/。
# 用法： ./scripts/package.sh
set -euo pipefail
cd "$(dirname "$0")/.."

VERSION="$(grep -m1 '^version' Cargo.toml | sed -E 's/.*=[[:space:]]*"([^"]+)".*/\1/')"
TARGET="$(rustc -vV | awk '/^host:/ {print $2}')"
DIST="dist"
STAGE="$DIST/stage"

echo "==> release 构建（host=$TARGET, version=$VERSION）"
cargo build --release -p rustto-cli

echo "==> 收集产物"
rm -rf "$DIST" "$STAGE"
mkdir -p "$STAGE"
cp "target/release/rustto-client" "$STAGE/"
[[ -f .env.example ]] && cp .env.example "$STAGE/"
cp README.md "$STAGE/README.md"
cp AGENT_NOTES.md "$STAGE/" 2>/dev/null || true

PKG="$DIST/rustto-client-$VERSION-$TARGET"
case "$TARGET" in
  *pc-windows*) zip -rj "$PKG.zip" "$STAGE";;
  *) tar -czf "$PKG.tar.gz" -C "$STAGE" .;;
esac
rm -rf "$STAGE"

echo "打包完成："
ls -1 "$DIST"
