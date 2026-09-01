# restto-cli（`restto-client` 二进制）

restto 客户端主程序：**中间人** + **agent 友好的 CLI** + **常驻 daemon 入口**。
本程序不集成任何备份逻辑；所有备份能力由 [`clis/`](../../clis/) 下的独立第三方
CLI 提供，主程序经 [`internal/runner`](../../internal/runner/) 以子进程调度。

## 命令一览

```text
restto-client daemon                          # 常驻：连接 Manager，接收并执行任务
restto-client tool list                       # 列出所有可用 CLI（agent 发现）
restto-client tool schema <name>              # 打印某 CLI 的参数 JSON Schema
restto-client tool run <name> [参数来源]      # 以 JSON 参数运行外部 CLI
restto-client --version / --help
```

> 旧版兼容命令 `backup-file` / `backup-mysql` / `run-module` 已移除，
> 统一走 `tool run`。

### `tool run` 参数来源（三选一）

| 来源           | 用法                          | 说明                          |
| -------------- | ----------------------------- | ----------------------------- |
| 内联 JSON      | `--args '{"path":"…"}'`       | 最常用                        |
| 文件           | `--args-file ./args.json`     | 大参数 / 含特殊字符           |
| stdin          | `--args -`                    | 管道：`cat a.json \| … --args -` |
| 都不给         | ——                            | 视作空对象 `{}`               |

参数来源解析在 [`internal/cliargs`](../../internal/cliargs/) 共享，
CLI 侧（`clis/*/main.go`）与主程序同源复用。

### 输出（结构化 JSON 信封）

```json
{ "ok": true,  "tool": "backup_file", "output": { "file_path": "…", "size": 1234, "checksum": "…" } }
{ "ok": false, "tool": "backup_file", "error": "missing args.path" }
```

- 成功退出码 `0`，CLI 执行失败退出码 `1`，CLI 不存在 / 用法错误退出码 `2`，
  便于 agent / 脚本解析。
- 信封由 CLI 的 stdout 解析而来（[`internal/common/envelope.go`](../../internal/common/envelope.go)），
  `error` 文本原文透传。
- daemon 模式下不向 stdout 打印业务结果（走 Netty 回传 + 文件日志）。

## CLI 查找目录

`tool run <name>` / `tool list` 按以下优先级查找可执行文件（见
`runner.DefaultDirs`）：

```
$DATA_DIR/clis  →  bin/clis  →  $RESTTO_CLIS_DIR  →  PATH
```

允许去下划线别名（`backup_file` 可命中 `backupfile`）。
`tool list` 在无任何 CLI 时输出 `{"tools":[]}` 且退出码 `0`。

## 面向 agent 的典型流程

```bash
# 1) 发现可用工具
restto-client tool list
# 2) 查某个工具的入参格式
restto-client tool schema backup_file
# 3) 用 JSON 参数执行
restto-client tool run backup_file --args '{"path":"/data","dest":"/backup/data.tar.gz"}'
```

## 运行 / 编译

```bash
go run ./client/restto tool list    # 直接运行
go build ./client/restto            # 构建
go test ./client/restto/            # 单测
```

## 架构关系

```
client/restto ──► runner(子进程执行器) ──► clis/backupfile、clis/backupmysql、…（外部 CLI）
               └► common(信封契约) ◄──────────────┘（CLI 输出信封）
               └► cliargs(参数来源解析，与 CLI 共享)
               └► core(协议/配置/日志/校验，受保护)
               └► daemon(常驻进程，与 tool run 同源调度)
```

daemon 接收 `TASK_COMMAND{module, args}` 后，按 `module` 名经 runner 解析到
对应 CLI 并执行 —— 与 `tool run <module>` 完全同源，保证行为一致。

## 边界（AGENT.MD）

- `internal/core` 不得修改；`.env` 不得修改 / 提交。
- 新增备份能力不改本包（新增 CLI 或服务端推送扩展包即可，见
  [`clis/README.md`](../../clis/README.md)）。
