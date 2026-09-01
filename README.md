# restto — 分布式服务器数据备份系统

基于 **Go 客户端 + SpringBoot 管理端 + Vue 前端** 的分布式服务器数据备份系统。

## 架构

```
┌─────────────────┐   REST + Token   ┌──────────────────┐
│  Vue 前端        │ ◄──────────────► │  SpringBoot Mgr  │
│  manager/client │                  │  manager/web     │ └ MySQL(Flyway)
└─────────────────┘                  │  └ Netty Server  │
                                     └────────┬─────────┘
                                              │ Netty TCP（自定义协议）
                                              ▼
                                     ┌──────────────────┐
                                     │  Go Client       │ 部署在目标机，常驻
                                     │  processor/      │ 中间人主程序 + 独立 clis/* + daemon
                                     └──────────────────┘
```

## 目录

| 目录              | 角色                 | 技术栈                                      |
| ----------------- | -------------------- | ------------------------------------------- |
| `processor/`      | 备份客户端（目标机） | Go（中间人主程序 client/restto + 独立 clis/* + internal 共用模块） |
| `manager/web/`    | 管理端后端           | Spring Boot 2.7 + Netty + MyBatis-Plus + MySQL |
| `manager/client/` | 管理端前端           | Vue 3 + Vite + Element Plus                 |

## 核心链路

1. 前端登录拿 JWT → 调 RESTful 接口创建节点（返回一次性 Token）。
2. Go 客户端带 Token 通过 Netty 长连接注册到管理端，周期心跳。
3. 创建备份任务（模块 `backup_file` / `backup_mysql` + cron），到点经 Netty 下发 `TASK_COMMAND`。
4. 客户端主程序（中间人）经 runner 以子进程调度 clis/ 下对应第三方 CLI 执行备份，解析信封后回传 `TASK_RESULT`，落库为备份记录。
5. 上传二进制经 Netty `BINARY_PUSH` 分片下发：version 形如 `module@version` → 第三方扩展包，原子安装到 `DATA_DIR/clis` 立即可调度；纯版本号 → 客户端自升级（替换自身后自动重启）。

## Netty 协议（两端字节级对齐）

```
| magic 4B "RUST"(0x52555354) | length 4B BE(=1+payloadLen) | type 1B | payload JSON |
```
JSON 字段使用 snake_case。详见 `processor/internal/core/protocol/protocol.go` 与 `manager/web/.../netty/ProtocolCodec.java`。

## 快速开始

```bash
# 1) 数据库
mysql -uroot -p -e "CREATE DATABASE restto DEFAULT CHARSET utf8mb4;"

# 2) 后端（Flyway 自动建表，内置 admin/admin123；Netty 9600，HTTP 8080）
cd manager/web && mvn spring-boot:run

# 3) 前端
cd manager/client && npm install && npm run dev   # http://127.0.0.1:5173

# 4) 客户端（另起终端，先把 .env 的 NODE_TOKEN 改为节点创建时返回的 Token）
cd processor && ./scripts/build.sh   # 产出 bin/restto-client + bin/clis/*（tool run 需要）
bin/restto-client daemon
```

## 约束（AGENT.MD）

- 所有接口 Token 校验、RESTful、数据库变更走 migrations（只增不删）。
- 不可修改：各项目 `.env`、Go `internal/core`、`db/migration/` 历史文件。
- 日志按日期分区（`logs/`）。
- 代码风格：前端 Prettier + ESLint(Airbnb)；后端 Lombok + 驼峰；Go `gofmt` + `go vet`。
- 新增依赖前先检查现有依赖，禁止重复引入同类库。
