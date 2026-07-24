#!/usr/bin/env bash
# backup_mysql 工具构建脚本：构建并运行本 crate 的单元测试。
# 用法： ./crates/backup-mysql/build.sh
set -euo pipefail
cd "$(dirname "$0")/../.."

echo "==> cargo build -p rustto-backup-mysql"
cargo build -p rustto-backup-mysql

echo "==> cargo test -p rustto-backup-mysql"
cargo test -p rustto-backup-mysql

echo "backup_mysql 构建完成。"
