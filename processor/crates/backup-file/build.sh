#!/usr/bin/env bash
# backup_file 工具构建脚本：构建并运行本 crate 的单元测试。
# 用法： ./crates/backup-file/build.sh
set -euo pipefail
cd "$(dirname "$0")/../.."

echo "==> cargo build -p rustto-backup-file"
cargo build -p rustto-backup-file

echo "==> cargo test -p rustto-backup-file"
cargo test -p rustto-backup-file

echo "backup_file 构建完成。"
