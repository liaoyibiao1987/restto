# rustto client（Rust）

分布式服务器数据备份系统的客户端。Cargo workspace，纯 Rust，支持静态编译。

## 目录结构

```
processor/
├── crates/
│   ├── core/      # ★核心工具类（协议/配置/日志/校验），对应 AGENT.MD 不可修改边界
│   ├── modules/   # 功能模块（backup_file / backup_mysql），独立、可扩展
│   └── cli/       # 主二进制 rustto-client（daemon + 模块调度）
├── .env           # 环境变量
├── logs/          # 按日期滚动日志
└── data/          # 备份产物 / 接收的二进制
```

## 命令行（AI-CLI 架构：按模块名调度）

```bash
# 常驻：连接 Manager，注册/心跳，接收并执行任务
cargo run -p rustto-cli -- daemon

# 单次文件/目录备份
cargo run -p rustto-cli -- backup-file --path /data --dest /backup/data.tar.gz

# 单次 MySQL 备份（依赖目标机 mysqldump）
cargo run -p rustto-cli -- backup-mysql --host 127.0.0.1 --port 3306 \
  --user root --password *** --database mydb --dest /backup/mydb.sql.gz

# 通用模块调度（服务端按模块名调用）
cargo run -p rustto-cli -- run-module backup_file \
  --args '{"path":"/data","dest":"/backup/data.tar.gz"}'
```

## 编译

```bash
cargo build               # 开发
cargo test                # 运行单测（协议编解码、打包等）
```

### 静态编译（可选，纯静态二进制）

```bash
rustup target add x86_64-unknown-linux-musl
RUSTFLAGS='-C target-feature=+crt-static' \
  cargo build --release --target x86_64-unknown-linux-musl
```

## Netty 通信协议（与 manager/web 对齐）

```
| magic 4B "RUST"(0x52555354) | length 4B BE | type 1B | payload JSON(length-1) |
```
消息类型：REGISTER / REGISTER_ACK / HEARTBEAT / TASK_COMMAND / TASK_RESULT / BINARY_PUSH / BINARY_ACK。

## 边界（AGENT.MD）

- `crates/core` 为核心工具类，**不得修改**。
- `.env` 为密钥文件，**不得修改/提交**（用 `.env.example` 做模板）。
