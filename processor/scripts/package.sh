#!/usr/bin/env bash
# 打包：本地优化构建（主程序 + 全部 CLI），再把 bin + clis + 文档 + .env.example
# 打成版本化归档到 dist/（保留目录树，解压即得完整部署目录）。
# 用法： ./scripts/package.sh
set -euo pipefail
cd "$(dirname "$0")/.."

# 版本号读取 .env.example 的 CLIENT_VERSION（与运行时默认一致）。
VERSION="$(grep -m1 '^CLIENT_VERSION=' .env.example 2>/dev/null | cut -d= -f2)"
VERSION="${VERSION:-0.1.0}"
TARGET="$(go env GOOS)-$(go env GOARCH)"
DIST="dist"
STAGE="$DIST/stage"
BIN_NAME="restto-client"
[[ "$(go env GOOS)" == "windows" ]] && BIN_NAME="restto-client.exe"

echo "==> 优化构建（host=$TARGET, version=$VERSION）"
go build -ldflags "-s -w" -o "bin/$BIN_NAME" ./client/restto
mkdir -p bin/clis
for dir in clis/*/; do
  name="$(basename "$dir")"
  [[ "$(go env GOOS)" == "windows" ]] && name="${name}.exe"
  go build -ldflags "-s -w" -o "bin/clis/$name" "./$dir"
done

echo "==> 收集产物"
rm -rf "$DIST" "$STAGE"
mkdir -p "$STAGE"
cp "bin/$BIN_NAME" "$STAGE/"
cp -r bin/clis "$STAGE/clis"
[[ -f .env.example ]] && cp .env.example "$STAGE/"
cp README.md "$STAGE/README.md"

PKG="$DIST/restto-client-$VERSION-$TARGET"
case "$TARGET" in
  windows-*) ( cd "$STAGE" && zip -qr "$OLDPWD/$PKG.zip" . );;
  *) tar -czf "$PKG.tar.gz" -C "$STAGE" .;;
esac
rm -rf "$STAGE"

echo "打包完成："
ls -1 "$DIST"
