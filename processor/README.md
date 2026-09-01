# restto client（Go）

分布式服务器数据备份系统的客户端。Go 单 module 多 package，CGO 关闭，支持静态编译。
主程序是**中间人**：自身不含备份逻辑，负责 CLI 输出处理、结果上报服务端、
自升级、按服务端指令调度独立第三方程序、接收第三方扩展包。

## 架构

```
client/restto（中间人主程序）
   ──► internal/runner（子进程执行器）──► clis/backupfile、clis/backupmysql、…（独立第三方 CLI）
   │        └─ 内部/外部 CLI 统一信封契约 ◄── internal/common（envelope/错误/产物）
   ──► internal/cliargs（--args 三种来源解析，主程序与 CLI 共享）
   ──► internal/core（协议 / 配置 / 日志 / 校验，★受保护不可修改）
   ──► internal/daemon（常驻进程：注册 / 心跳 / 任务调度 / 二进制下发路由 / 升级重启）
             └─ internal/upgrade（原子落盘可执行文件）
```

- `clis/*`：**独立可编译、独立执行**的第三方备份程序（子进程被 runner 调度，
  也可人工直接运行）。主程序与其完全解耦。
- `internal/` 只放共用模块：`common`（Tool 契约 / 信封）、`cliargs`（参数解析）、
  `runner`（外部 CLI 执行器）、`upgrade`（原子安装）、`daemon`（常驻进程）、
  `core`（受保护基础设施）。
- daemon 与 `tool run` 走**同一个 runner** 调度外部 CLI，行为完全一致。

## 目录结构

```
processor/
├── client/restto/            # 中间人主程序（agent 友好 CLI + daemon 调度）
├── clis/                     # 第三方可执行程序（可单独编译 / 执行）
│   ├── backupfile/           #   backup_file：文件/目录 tar.gz 备份
│   └── backupmysql/          #   backup_mysql：mysqldump 导出 + gzip 备份
├── internal/                 # 仅共用模块
│   ├── core/                 # ★核心工具类（协议/配置/日志/校验）—— AGENT.MD 不可修改边界
│   │   ├── protocol/         #   Netty 协议编解码（帧 + JSON）
│   │   ├── config/           #   .env / 环境变量配置加载
│   │   ├── crypto/           #   sha256（字节 / 文件）
│   │   └── logger/           #   按日期滚动日志 + stdout
│   ├── cliargs/              # --args / --args-file / --args - 参数来源解析（共享）
│   ├── common/               # Tool 公共框架（接口 / 产物 / 错误 / 信封）
│   ├── runner/               # 外部 CLI 子进程执行器（定位 / spawn / 信封解析）
│   ├── upgrade/              # 原子落盘可执行文件（临时文件 + chmod + rename）
│   └── daemon/               # 常驻 daemon（连接 Manager / 推送路由 / 升级重启）
├── scripts/                  # 构建 / 打包 / 多平台交叉编译脚本
├── Dockerfile                # scratch 镜像（主程序 + clis 装入 PATH）
├── go.mod                    # Go module 定义（restto-client）
├── .env                      # 环境变量（不得提交）
├── logs/                     # 按日期滚动日志
└── data/                     # 备份产物 / 接收的二进制 / clis 扩展包目录
```

## 命令行（agent 友好）

```bash
# 常驻：连接 Manager，注册/心跳，接收并执行任务
go run ./client/restto daemon

# —— agent 调用入口（按名调度外部 CLI + JSON 参数）——
go run ./client/restto tool list                                  # 发现可用 CLI
go run ./client/restto tool schema backup_file                    # 查参数格式
go run ./client/restto tool run backup_file \
  --args '{"path":"/data","dest":"/backup/data.tar.gz"}'             # JSON 参数运行
# 参数来源： --args '<json>' / --args-file ./a.json / --args -（stdin）/ 不给则 {}

# —— 直接执行第三方 CLI（不经主程序，行为一致）——
./bin/clis/backupfile run --args '{"path":"/data","dest":"/backup/data.tar.gz"}'
./bin/clis/backupfile info
```

CLI 查找目录（优先级）：`$DATA_DIR/clis` → `bin/clis` → `$RESTTO_CLIS_DIR` → `PATH`。
`tool run` 统一输出结构化 JSON 信封，成功退出码 `0`、CLI 失败 `1`、CLI 不存在 `2`：
```json
{ "ok": true, "tool": "backup_file",
  "output": { "file_path": "/backup/data.tar.gz", "size": 1234, "checksum": "…" } }
```

## 编译

```bash
go build ./...                  # 全量构建
go test ./...                   # 全部单测（协议编解码、打包、信封、runner、推送路由等）
go vet ./...                    # 静态检查
./scripts/build.sh              # 优化构建：bin/restto-client + bin/clis/*
./scripts/package.sh            # 打包到 dist/（归档保留 clis/ 目录树）
./scripts/cross.sh              # 多平台交叉编译（详见 scripts/README.md）
```

### 多平台交叉编译

```bash
./scripts/cross.sh              # 构建 linux/windows/macOS 多平台二进制并归档到 dist/
./scripts/cross.sh linux/arm64  # 只编译指定 GOOS/GOARCH
```
Go 原生交叉编译（`CGO_ENABLED=0 GOOS=… GOARCH=… go build`），无需 Docker 与外部工具链。
每个归档含主程序与 `clis/` 全部 CLI（保留目录树，解压即得完整部署目录）。
默认目标含 `linux/amd64`、`linux/arm64`、`linux/386`、`windows/amd64`、
`darwin/amd64`、`darwin/arm64`，详见 [`scripts/README.md`](scripts/README.md)。

## 新增一个第三方 CLI

1. 在 `clis/<name>/` 新建 package main，实现 `common.Tool` 接口 + CLI 外壳
   （子命令 / 信封 / stderr 日志，约定见 [`clis/README.md`](clis/README.md)）。
2. 放进任一查找目录（或重新跑 `build.sh`）即被 `tool list` / `tool run` /
   daemon 调度发现，**无需改动 restto 主程序**。
3. 也可由服务端以 `BINARY_PUSH{version:"<name>@<ver>"}` 在线推送安装。

## Netty 通信协议（与 manager/web 对齐）

```
| magic 4B "RUST"(0x52555354) | length 4B BE | type 1B | payload JSON(length-1) |
```
消息类型：REGISTER / REGISTER_ACK / HEARTBEAT / TASK_COMMAND / TASK_RESULT / BINARY_PUSH / BINARY_ACK。
daemon 收到 `TASK_COMMAND{module, args}` 后经 runner 以子进程执行 `clis/<module>`
并回传 `TASK_RESULT`；`BINARY_PUSH` 按 version 路由（`module@version` → 扩展包
安装；纯版本号 → 自升级 + 自动重启，详见 [`internal/daemon/README.md`](internal/daemon/README.md)）。

## 边界（AGENT.MD）

- `internal/core` 为核心工具类，**不得修改**。
- `.env` 为密钥文件，**不得修改 / 提交**（用 `.env.example` 做模板）。
- 新增依赖前先检查现有依赖，禁止重复引入同类库。
