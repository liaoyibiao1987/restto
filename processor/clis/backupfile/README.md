# backup_file CLI（独立可执行程序）

将指定的**文件或目录**打包为 `.tar.gz` 备份产物，并计算 sha256 校验值。
`clis/backupfile` 是独立第三方程序：可单独编译、单独执行，
由 restto-client 经 runner 以子进程调度（约定见 [`../README.md`](../README.md)）。

## 功能

- 递归打包目录（或单个文件）为 tar 归档，再 gzip 压缩（标准库 `archive/tar` + `compress/gzip`）。
- 自动创建产物父目录。
- 返回产物绝对路径、字节数、sha256。
- 源路径不存在时返回明确的参数错误（不会 panic）。
- 打包在独立 goroutine 中执行，且响应 context 取消。

## 参数（JSON）

| 字段   | 类型   | 必填 | 说明                              |
| ------ | ------ | ---- | --------------------------------- |
| `path` | string | 是   | 待备份的源文件 / 目录绝对路径     |
| `dest` | string | 是   | 产物路径，建议以 `.tar.gz` 结尾   |

参数 JSON Schema 可通过 `restto-client tool schema backup_file` 或
`./backupfile info` 实时获取。

## 调用方式

```bash
# 1) 直接执行（独立程序）
./backupfile run --args '{"path":"/data","dest":"/backup/data.tar.gz"}'

# 2) 经 restto-client 调度（服务端 TASK_COMMAND 走同一链路）
restto-client tool run backup_file \
  --args '{"path":"/data","dest":"/backup/data.tar.gz"}'

# 3) 参数从 stdin（--args -）
echo '{"path":"/data","dest":"/backup/data.tar.gz"}' | ./backupfile run --args -
```

### 输出（结构化 JSON，便于 agent / runner 解析）

成功：
```json
{ "ok": true, "tool": "backup_file",
  "output": { "file_path": "/backup/data.tar.gz", "size": 1234, "checksum": "…" } }
```
失败：`ok=false`，`error` 字段给出原因，退出码 `1`；用法错误退出码 `2`。
stdout 只输出单个 JSON 文档，日志一律写 stderr。

## 注意事项

- 产物会被**覆盖写入**（同名直接覆盖）。
- 无需任何外部命令依赖（纯 Go 标准库 tar + gzip）。
- 归档内的软链保留为软链条目（不解引用），socket / 设备文件会被跳过。

## 编译 / 测试

```bash
go build -o backupfile ./clis/backupfile   # 单独编译本 CLI
go test ./clis/backupfile/                 # 本包单测
go build ./...                             # 全量构建
```

> 多平台可执行文件（主程序 + 全部 CLI）的交叉编译见仓库根 `scripts/cross.sh`。
