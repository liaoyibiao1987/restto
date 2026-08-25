# rustto-cli（`rustto-client` 二进制）

rustto 客户端主程序：**agent 友好的 CLI** + **常驻 daemon**。所有备份能力由
[`internal/tools`](../../internal/tools/) 注册的工具提供，cli 仅做发现 / 调度 / 输出。

## 命令一览

```text
rustto-client daemon                          # 常驻：连接 Manager，接收并执行任务
rustto-client tool list                       # 列出所有工具（agent 发现）
rustto-client tool schema <name>              # 打印某工具的参数 JSON Schema
rustto-client tool run <name> [参数来源]      # 以 JSON 参数运行工具

# 兼容旧命令
rustto-client backup-file   --path P --dest D
rustto-client backup-mysql  --host .. --user .. --password .. --database .. --dest ..
rustto-client run-module <name> --args '<json>'
```

### `tool run` 参数来源（三选一）

| 来源           | 用法                          | 说明                          |
| -------------- | ----------------------------- | ----------------------------- |
| 内联 JSON      | `--args '{"path":"…"}'`       | 最常用                        |
| 文件           | `--args-file ./args.json`     | 大参数 / 含特殊字符           |
| stdin          | `--args -`                    | 管道：`cat a.json \| … --args -` |
| 都不给         | ——                            | 视作空对象 `{}`               |

### 输出（结构化 JSON 信封）

```json
{ "ok": true,  "tool": "backup_file", "output": { "file_path": "…", "size": 1234, "checksum": "…" } }
{ "ok": false, "tool": "backup_file", "error": "missing args.path" }
```

- 成功退出码 `0`，工具失败退出码 `1`，用法错误退出码 `2`，便于 agent / 脚本解析。
- daemon 模式下不向 stdout 打印业务结果（走 Netty 回传 + 文件日志）。

## 面向 agent 的典型流程

```bash
# 1) 发现可用工具
rustto-client tool list
# 2) 查某个工具的入参格式
rustto-client tool schema backup_file
# 3) 用 JSON 参数执行
rustto-client tool run backup_file --args '{"path":"/data","dest":"/backup/data.tar.gz"}'
```

## 运行 / 编译

```bash
go run ./cmd/rustto-client tool list    # 直接运行
go build ./cmd/rustto-client            # 构建
go test ./cmd/rustto-client/            # 单测
```

## 架构关系

```
cmd/rustto-client ──► tools(注册中心) ──► backupfile / backupmysql / …（各工具包）
                 └► common(Tool 契约) ◄─────────────────┘
                 └► core(协议/配置/日志/校验，受保护)
                 └► daemon(常驻进程，与 tool run 同源调度)
```

daemon 接收 `TASK_COMMAND{module, args}` 后，按 `module` 名经 `tools.Lookup`
解析到对应工具并执行 —— 与 `tool run <module>` 完全同源，保证行为一致。

## 边界（AGENT.MD）

- `internal/core` 不得修改；`.env` 不得修改 / 提交。
- 新增工具不改本包（只需到 `internal/tools` 的 `Registry()` 注册一行）。
