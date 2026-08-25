# rustto manager（Spring Boot）

分布式服务器数据备份系统的管理端：RESTful 接口 + Netty 通信服务端 + MySQL。

## 技术栈

- Spring Boot 2.7.18 / Java 8
- MyBatis-Plus（ORM + 分页）
- Flyway（数据库迁移，对应 AGENT.MD 的 migrations/）
- Netty（与 Go 客户端通信）
- MySQL 8.0+
- jjwt（Token 鉴权）、BCrypt（密码哈希）

## 目录结构

```
manager/web/
├── src/main/java/com/rustto/manager/
│   ├── ManagerApplication.java       # 启动类（加载 .env、@MapperScan、开启调度）
│   ├── support/                      # .env 加载器等支撑工具
│   ├── common/                       # 统一响应 / 异常 / 分页
│   ├── config/                       # 配置属性 + Web/MyBatis 配置
│   ├── security/                     # JWT、Token 拦截器、用户上下文
│   ├── netty/                        # 协议编解码 + 服务端 + 连接注册表 + 二进制分发
│   │   └── message/                  # 协议消息 DTO
│   ├── entity/  mapper/  dto/        # 实体 / Mapper / 请求响应对象
│   ├── service/  service/impl/       # 业务层
│   ├── scheduler/                    # cron 任务调度
│   └── controller/                   # RESTful 接口
├── src/main/resources/
│   ├── application.yml
│   ├── logback-spring.xml            # 按日期滚动日志
│   └── db/migration/V1__init.sql     # ★Flyway 迁移（只增不改不删）
├── .env                              # 环境变量（密钥，勿改勿提交）
└── logs/  data/                      # 日志 / 接收的产物与二进制
```

## 开发运行

```bash
# 1. 准备 MySQL，创建库
mysql -uroot -p -e "CREATE DATABASE rustto DEFAULT CHARSET utf8mb4;"

# 2. 编译
mvn clean compile

# 3. 运行（Flyway 启动时自动建表，内置 admin/admin123）
mvn spring-boot:run

# 4. 测试
mvn test
```

Netty 默认监听 `9600`（与 Go client 默认一致）。HTTP 默认 `8080`。

## 主要接口（均需 Token，除 /api/auth/login）

| 方法   | 路径                       | 说明                 |
| ------ | -------------------------- | -------------------- |
| POST   | /api/auth/login            | 登录，返回 JWT       |
| GET    | /api/auth/info             | 当前用户             |
| POST   | /api/nodes                 | 创建节点（返回 Token）|
| GET    | /api/nodes                 | 节点分页             |
| DELETE | /api/nodes/{id}            | 删除节点             |
| POST   | /api/tasks                 | 创建备份任务         |
| PUT    | /api/tasks/{id}            | 更新任务             |
| GET    | /api/tasks                 | 任务分页             |
| POST   | /api/tasks/{id}/run        | 立即触发任务         |
| GET    | /api/records?taskId=&page= | 备份执行记录         |
| POST   | /api/binaries/upload       | 上传客户端二进制     |
| POST   | /api/binaries/{id}/push    | 下发二进制到节点     |

## Netty 协议

与 Go client `internal/core/protocol` 字节级对齐：

```
| magic 4B "RUST"(0x52555354) | length 4B BE(=1+payloadLen) | type 1B | payload JSON |
```
JSON 字段命名使用 snake_case（`ProtocolCodec.MAPPER`），与 RESTful 层的驼峰互不影响。

## 边界（AGENT.MD）

- `.env` 为密钥文件，**不得修改/提交**。
- `src/main/resources/db/migration/` 为迁移目录，历史文件**只增不删**。
- 修改鉴权 / 加密逻辑前须出方案并标记“需人类审核”。
