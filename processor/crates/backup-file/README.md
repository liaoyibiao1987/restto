# backup_file 工具

将指定的**文件或目录**打包为 `.tar.gz` 备份产物，并计算 sha256 校验值。

## 功能

- 递归打包目录（或单个文件）为 tar 归档，再 gzip 压缩。
- 自动创建产物父目录。
- 返回产物绝对路径、字节数、sha256。
- 源路径不存在时返回明确的参数错误（不会 panic）。

## 参数（JSON）

| 字段   | 类型   | 必填 | 说明                              |
| ------ | ------ | ---- | --------------------------------- |
| `path` | string | 是   | 待备份的源文件 / 目录绝对路径     |
| `dest` | string | 是   | 产物路径，建议以 `.tar.gz` 结尾   |

参数 JSON Schema 可通过 `rustto-client tool schema backup_file` 实时获取。

## 调用方式

```bash
# 1) 通过 agent 友好的 tool run（推荐）
rustto-client tool run backup_file \
  --args '{"path":"/data","dest":"/backup/data.tar.gz"}'

# 2) 兼容旧命令
rustto-client backup-file --path /data --dest /backup/data.tar.gz

# 3) 参数从 stdin（--args -）
echo '{"path":"/data","dest":"/backup/data.tar.gz"}' | \
  rustto-client tool run backup_file --args -
```

### 输出（结构化 JSON，便于 agent 解析）

成功：
```json
{ "ok": true, "tool": "backup_file",
  "output": { "file_path": "/backup/data.tar.gz", "size": 1234, "checksum": "…" } }
```
失败：`ok=false`，`error` 字段给出原因，退出码 `1`。

## 注意事项

- 产物会被**覆盖写入**（同名直接覆盖）。
- 大目录打包为同步 IO，内部已用 `spawn_blocking` 隔离，不会阻塞 daemon 的异步 runtime。
- 无需任何外部命令依赖（纯 Rust tar + flate2）。

## 编译

```bash
# 本 crate 单独构建 + 单测
./crates/backup-file/build.sh

# 或手动
cargo build -p rustto-backup-file
cargo test  -p rustto-backup-file
```

> 多平台可执行文件（`rustto-client` 整体）的交叉编译见仓库根 `scripts/cross.sh`。
