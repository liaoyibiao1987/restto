# backup_mysql CLI（独立可执行程序）

调用系统 `mysqldump` 导出指定数据库，再 gzip 压缩为 `.sql.gz` 备份产物，并计算 sha256。
`clis/backupmysql` 是独立第三方程序：可单独编译、单独执行，
由 restto-client 经 runner 以子进程调度（约定见 [`../README.md`](../README.md)）。

## 功能

- 以 `--single-transaction --quick` 在线导出（对 InnoDB 友好，不锁表）。
- dump 输出经 gzip 压缩落盘，自动创建产物父目录。
- 返回产物绝对路径、字节数、sha256。
- `mysqldump` 缺失或执行失败时返回结构化错误（不 panic）。
- 通过 `exec.CommandContext` 执行子进程，context 取消时自动终止 mysqldump。

## 参数（JSON）

| 字段       | 类型    | 必填 | 说明                        |
| ---------- | ------- | ---- | --------------------------- |
| `host`     | string  | 是   | 数据库主机（默认 127.0.0.1）|
| `port`     | integer | 否   | 端口，缺省 3306             |
| `user`     | string  | 是   | 账号                        |
| `password` | string  | 否   | 密码（明文），缺省为空      |
| `database` | string  | 是   | 目标数据库名                |
| `dest`     | string  | 是   | 产物路径，建议 `.sql.gz`    |

> `ParseParams` 校验要求 `host/user/database/dest` 必填；`port` 可省略，`password` 缺省为空。
> 参数 JSON Schema 可通过 `restto-client tool schema backup_mysql` 或 `./backupmysql info` 实时获取。

## 调用方式

```bash
# 1) 直接执行（独立程序）
./backupmysql run --args '{
  "host":"127.0.0.1","port":3306,"user":"root","password":"***",
  "database":"mydb","dest":"/backup/mydb.sql.gz"
}'

# 2) 经 restto-client 调度（服务端 TASK_COMMAND 走同一链路）
restto-client tool run backup_mysql --args '{ …同上… }'

# 3) 参数从 stdin（--args -）
cat args.json | ./backupmysql run --args -
```

### 输出（结构化 JSON）

成功：
```json
{ "ok": true, "tool": "backup_mysql",
  "output": { "file_path": "/backup/mydb.sql.gz", "size": 5678, "checksum": "…" } }
```
失败：`ok=false`，`error` 含 `mysqldump` 的 stderr，退出码 `1`；用法错误退出码 `2`。
stdout 只输出单个 JSON 文档，日志一律写 stderr（密码绝不进日志）。

## 注意事项

- 依赖目标机已安装 **`mysqldump`** 并在 `PATH` 中可用。
- 密码以命令行 `--password=...` 传递；生产环境建议配合受限账号与最小权限。
- 子进程输出先全量缓存再压缩，超大库（>数 GB）建议改用流式管道。

## 编译 / 测试

```bash
go build -o backupmysql ./clis/backupmysql   # 单独编译本 CLI
go test ./clis/backupmysql/                  # 本包单测（不依赖真实 mysqldump）
go build ./...                               # 全量构建
```

> 多平台可执行文件（主程序 + 全部 CLI）的交叉编译见仓库根 `scripts/cross.sh`。
