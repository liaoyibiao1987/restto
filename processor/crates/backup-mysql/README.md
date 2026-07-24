# backup_mysql 工具

调用系统 `mysqldump` 导出指定数据库，再 gzip 压缩为 `.sql.gz` 备份产物，并计算 sha256。

## 功能

- 以 `--single-transaction --quick` 在线导出（对 InnoDB 友好，不锁表）。
- dump 输出经 gzip 压缩落盘，自动创建产物父目录。
- 返回产物绝对路径、字节数、sha256。
- `mysqldump` 缺失或执行失败时返回结构化错误（不 panic）。

## 参数（JSON）

| 字段       | 类型    | 必填 | 说明                       |
| ---------- | ------- | ---- | -------------------------- |
| `host`     | string  | 是   | 数据库主机（默认 127.0.0.1）|
| `port`     | integer | 否   | 端口，缺省 3306            |
| `user`     | string  | 是   | 账号                       |
| `password` | string  | 否   | 密码（明文），缺省空       |
| `database` | string  | 是   | 目标数据库名               |
| `dest`     | string  | 是   | 产物路径，建议 `.sql.gz`   |

> `MysqlParams` 反序列化要求 `host/user/database/dest` 必填；`port` 可省略，`password` 缺省为空。
> 参数 JSON Schema 可通过 `rustto-client tool schema backup_mysql` 实时获取。

## 调用方式

```bash
# 1) agent 友好的 tool run（推荐）
rustto-client tool run backup_mysql --args '{
  "host":"127.0.0.1","port":3306,"user":"root","password":"***",
  "database":"mydb","dest":"/backup/mydb.sql.gz"
}'

# 2) 兼容旧命令
rustto-client backup-mysql --host 127.0.0.1 --port 3306 \
  --user root --password *** --database mydb --dest /backup/mydb.sql.gz

# 3) 参数从 stdin（--args -）
cat args.json | rustto-client tool run backup_mysql --args -
```

### 输出（结构化 JSON）

成功：
```json
{ "ok": true, "tool": "backup_mysql",
  "output": { "file_path": "/backup/mydb.sql.gz", "size": 5678, "checksum": "…" } }
```
失败：`ok=false`，`error` 含 `mysqldump` 的 stderr，退出码 `1`。

## 注意事项

- 依赖目标机已安装 **`mysqldump`** 并在 `PATH` 中可用。
- 密码以命令行 `--password=...` 传递；生产环境建议配合受限账号与最小权限。
- 大库导出为流式子进程，主流程不阻塞 daemon 的其它任务。

## 编译

```bash
./crates/backup-mysql/build.sh          # 构建 + 单测
# 或手动
cargo build -p rustto-backup-mysql
cargo test  -p rustto-backup-mysql
```

> 多平台可执行文件（`rustto-client` 整体）的交叉编译见仓库根 `scripts/cross.sh`。
