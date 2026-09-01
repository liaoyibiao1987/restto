# clis — 第三方备份 CLI 可执行程序

每个子目录是一个**独立可编译、独立执行**的第三方备份程序，与 `restto-client`
主程序解耦：主程序不集成任何备份逻辑，仅作为**中间人**经
[`internal/runner`](../internal/runner/) 以子进程调度这些 CLI。

```
clis/
├── backupfile/     # backup_file：文件 / 目录 tar.gz 备份
└── backupmysql/    # backup_mysql：mysqldump 导出 + gzip 备份
```

## CLI 约定（新增第三方 CLI 必须遵守）

### 子命令

```text
<cli> run [--args '<json>' | --args-file FILE | --args -]   # 执行备份，stdout 输出信封
<cli> info                                                  # 输出工具元信息 JSON
<cli> version                                               # 输出版本
<cli> --help                                                # 帮助
```

restto-client 调度时固定执行 `<cli> run --args -`，参数 JSON 经 **stdin** 传入。

### stdout 纯净性（关键契约）

- stdout **只允许输出单个 JSON 文档**（信封或 info 元信息），任何日志一律写 **stderr**。
  上游 runner 依赖 stdout 整体可 `json.Unmarshal`；混入一行日志即破坏解析。
- 实现方式：CLI 内使用私有 `slog.NewTextHandler(os.Stderr, …)`，**不要**用
  `internal/core/logger`（其会双写 stdout）。

### 信封格式（与 `common.BuildEnvelope` 一致）

```json
{ "ok": true,  "tool": "backup_file", "output": { "file_path": "…", "size": 1234, "checksum": "…" } }
{ "ok": false, "tool": "backup_file", "error": "params error: …" }
```

- 成功退出码 `0`，工具失败 `1`，用法错误 `2`。
- `ok=false` 时 `error` 文本会被 runner 原文透传到服务端（`KindExternal`），
  请保证文案可读、可定位。

### info 元信息

```json
{ "name": "backup_file", "description": "…", "params_schema": { … } }
```

`tool list` / `tool schema <name>` 即来自各 CLI 的 `info` 输出。

## 模块名规则

模块名 = CLI 文件名，需匹配 `^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$`
（见 `runner.ValidModuleName`，防路径穿越）。查找时允许**去下划线别名**：
按 `backup_file` 调度可命中名为 `backupfile` 的可执行文件（反向亦然）。

## 查找优先级（`runner.DefaultDirs`）

```
$DATA_DIR/clis  →  bin/clis  →  $RESTTO_CLIS_DIR  →  PATH
```

- `$DATA_DIR/clis` 同时是服务端**扩展包安装目录**（见下），优先级最高。
- Docker 镜像把 clis 装入 `/usr/local/bin/`，靠 PATH 兜底发现。

## 服务端二进制下发（`module@version` 约定）

`BINARY_PUSH.version` 是服务端管理员上传时的自由文本，客户端按约定路由：

| version 形态           | 含义     | 客户端行为                                        |
| ---------------------- | -------- | ------------------------------------------------- |
| `backup_file@1.2.0`   | 扩展包   | 校验模块名 → 原子安装到 `$DATA_DIR/clis/backup_file` → 立即可调度（无需重启） |
| `1.0.2`               | 自升级   | 原子替换自身可执行文件 → ACK → 等在途任务清零后自动重启 |

## 如何新增 / 发布一个第三方 CLI

1. 在 `clis/<name>/` 新建包（package main），实现 `common.Tool` 接口 +
   CLI 外壳（子命令 / 信封 / stderr 日志，可参照 `clis/backupfile/main.go`）。
2. `go build ./... && go test ./clis/<name>/` 通过即可；
   `build.sh` / `cross.sh` / `package.sh` 会自动把 `clis/*/` 纳入构建与归档。
3. **无需改动 restto 主程序**：放进任一查找目录即可被 `tool list` 发现、
   被服务端 `TASK_COMMAND.module = "<name>"` 调度；或经服务端
   `<name>@<version>` 推送在线安装。

## 编译 / 测试

```bash
go build ./clis/...            # 全部 CLI
go test ./clis/...             # 单测
./scripts/build.sh             # 主程序 + bin/clis/ 全部 CLI
```
