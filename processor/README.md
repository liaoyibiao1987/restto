# rustto client（Rust）

分布式服务器数据备份系统的客户端。Cargo workspace，纯 Rust，支持静态编译。
本端是一套 **agent 友好的 CLI**：所有备份能力以"工具（Tool）"形式按需注册、按名调度。

## 架构

```
cli ──► tools(注册中心) ──► backup-file / backup-mysql / …(各工具 crate)
   └► common(Tool 契约 / 产物 / 错误) ◄─────────────────────┘
   └► core(协议 / 配置 / 日志 / 校验，★受保护不可修改)
```

- `common`：`Tool` trait、`ToolOutput`、`ToolError`、`ToolInfo` —— 工具公共基座。
- 每个工具是**独立 crate**，单独成目录（`Cargo.toml` + `src/` + `README.md` + `build.sh`），互不耦合。
- `tools`：注册中心，把各工具 crate 聚合为 `registry()` / `lookup(name)`。
- `cli`：只做发现 / 调度 / 输出，daemon 与 `tool run` 同源（都走 `tools::lookup`）。

## 目录结构

```
processor/
├── crates/
│   ├── core/          # ★核心工具类（协议/配置/日志/校验）—— AGENT.MD 不可修改边界
│   ├── common/        # Tool 公共框架（trait / 产物 / 错误）
│   ├── backup-file/   # 工具：文件/目录 tar.gz 备份
│   ├── backup-mysql/  # 工具：mysqldump 导出 + gzip 备份
│   ├── tools/         # 工具注册中心
│   └── cli/           # 主二进制 rustto-client（agent 友好 CLI + daemon）
├── scripts/           # 构建 / 打包 / 多平台交叉编译脚本
├── .env               # 环境变量（不得提交）
├── logs/              # 按日期滚动日志
└── data/              # 备份产物 / 接收的二进制
```

## 命令行（agent 友好）

```bash
# 常驻：连接 Manager，注册/心跳，接收并执行任务
cargo run -p rustto-cli -- daemon

# —— agent 调用入口（按名调度 + JSON 参数）——
cargo run -p rustto-cli -- tool list                                  # 发现可用工具
cargo run -p rustto-cli -- tool schema backup_file                    # 查参数格式
cargo run -p rustto-cli -- tool run backup_file \
  --args '{"path":"/data","dest":"/backup/data.tar.gz"}'             # JSON 参数运行
# 参数来源： --args '<json>' / --args-file ./a.json / --args -（stdin）/ 不给则 {}

# —— 兼容旧命令 ——
cargo run -p rustto-cli -- backup-file --path /data --dest /backup/data.tar.gz
cargo run -p rustto-cli -- backup-mysql --host 127.0.0.1 --port 3306 \
  --user root --password *** --database mydb --dest /backup/mydb.sql.gz
cargo run -p rustto-cli -- run-module backup_file \
  --args '{"path":"/data","dest":"/backup/data.tar.gz"}'
```

`tool run` 统一输出结构化 JSON 信封，成功退出码 `0`、失败 `1`：
```json
{ "ok": true, "tool": "backup_file",
  "output": { "file_path": "/backup/data.tar.gz", "size": 1234, "checksum": "…" } }
```

## 编译

```bash
cargo build                # 全量开发构建
cargo test --workspace     # 全部单测（协议编解码、打包、Tool 契约、调度等）
./scripts/build.sh         # release 构建
./scripts/package.sh       # 打包到 dist/
./scripts/cross.sh         # 多平台交叉编译（详见 scripts/README.md）
```

### 多平台交叉编译

```bash
cargo install cross        # 一次性安装（基于 Docker，开箱即用）
./scripts/cross.sh         # 构建 linux(musl)/windows/macOS 多平台二进制并归档到 dist/
```
默认目标含 `x86_64/aarch64-unknown-linux-musl`、`x86_64-pc-windows-gnu`、
`x86_64/aarch64-apple-darwin`，详见 [`scripts/README.md`](scripts/README.md)。

## 新增一个工具

1. 在 `crates/<your-tool>/` 新建 crate，实现 `rustto_common::Tool`（带 `README.md` + `build.sh`）。
2. 在 `crates/tools/Cargo.toml` 加依赖，在 `registry()` 里 `push` 一行。
3. 在本 `Cargo.toml` 的 `members` 追加该 crate 目录。

cli / daemon 代码无需改动 —— `tool list` / `tool schema` / `tool run` 立即可用，
daemon 也能通过 `TASK_COMMAND.module = "<your-tool>"` 调度。

## Netty 通信协议（与 manager/web 对齐）

```
| magic 4B "RUST"(0x52555354) | length 4B BE | type 1B | payload JSON(length-1) |
```
消息类型：REGISTER / REGISTER_ACK / HEARTBEAT / TASK_COMMAND / TASK_RESULT / BINARY_PUSH / BINARY_ACK。
daemon 收到 `TASK_COMMAND{module, args}` 后按 `module` 名调度到对应工具执行并回传 `TASK_RESULT`。

## 边界（AGENT.MD）

- `crates/core` 为核心工具类，**不得修改**。
- `.env` 为密钥文件，**不得修改 / 提交**（用 `.env.example` 做模板）。
- 新增依赖前先检查现有依赖，禁止重复引入同类库。
