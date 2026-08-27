# Graph Report - rustto  (2026-08-27)

## Corpus Check
- Large corpus: 579 files · ~135,304 words. Semantic extraction will be expensive (many Claude tokens). Consider running on a subfolder.

## Summary
- 1493 nodes · 4280 edges · 70 communities (63 shown, 7 thin omitted)
- Extraction: 93% EXTRACTED · 7% INFERRED · 0% AMBIGUOUS · INFERRED: 307 edges (avg confidence: 0.82)
- Token cost: 16,000 input · 7,000 output

## Community Hubs (Navigation)
- Auth & REST Controllers
- Scheduler & Cache Internals
- Role & RBAC Entities
- Workflow Execution Engine
- Workflow DTOs & Views
- Frontend Dependencies & Tooling
- Go Processor Unit Tests
- User Seeding & Initialization
- Backup Tool Implementations
- User Management Frontend
- System Spec & Constraint Docs
- Request DTOs & Annotations
- Menu Tree Assembly
- Workflow Canvas Designer
- Binary Push Service
- Go Protocol Codec
- Role Management Frontend
- Workflow Persistence Entities
- Go Daemon Runtime
- Task Management Frontend
- Netty Server Handler
- Binary Upload Frontend
- Go CLI Entrypoint
- Operation Log Frontend
- Token Interceptor Internals
- Configuration Properties
- Error Handling Types
- Menu Management Frontend
- Protocol Message Types
- Task Scheduler & Distributor
- Go Logger & Rotation
- Netty Codec & Connections
- Workflow List Frontend
- Backup Task Persistence
- Node Management Frontend
- Workflow History Frontend
- Login & Auth Flow
- Protocol Message Payloads
- Frontend API Client
- Vue App Bootstrap
- Go Client Config
- Permission Management Frontend
- Backup Record Persistence
- Permission Lookup Service
- Frame Decoder & Tests
- Login Page & Auth Store
- Global Exception Handler
- Flyway & MyBatis Config
- Tool Helpers & Tests
- Spring Boot Bootstrap
- Role API Module
- Backup Record Frontend
- Node Registry Service
- Operation Log Aspect
- Tool Registry Core
- Log Masking Tests
- Initializer Tests
- Layout & Side Menu
- Flyway Migration Tests
- Web MVC Configuration
- Go Crypto Utilities
- Workflow API Module
- Canvas Drag Interactions
- Cross Compilation Scripts
- Build Script
- Package Script
- Manager POM
- Go Module Manifest

## God Nodes (most connected - your core abstractions)
1. `Result` - 75 edges
2. `RequirePermission` - 66 edges
3. `PageResult` - 65 edges
4. `BusinessException` - 47 edges
5. `OperLog` - 40 edges
6. `WorkflowGraphTest` - 40 edges
7. `WorkflowExecutionServiceImpl` - 39 edges
8. `ResultCode` - 29 edges
9. `WorkflowGraph` - 28 edges
10. `BackupTask` - 26 edges

## Surprising Connections (you probably didn't know these)
- `internal/core/protocol（Go 端 Netty 帧编解码）` --semantically_similar_to--> `ProtocolCodec（Netty 帧编解码，snake_case JSON MAPPER）`  [INFERRED] [semantically similar]
  processor/README.md → manager/server/README.md
- `application.yml（restto-manager 配置：Hikari / Flyway / JWT / Netty / init）` --conceptually_related_to--> `Netty 自定义二进制帧协议（magic RUST + length BE + type + JSON payload）`  [INFERRED]
  manager/server/src/main/resources/application.yml → docs/系统说明书v1.1.md
- `daemon 常驻进程（注册 / 心跳 / 任务执行 / 二进制接收，Netty 客户端）` --references--> `二进制热下发（BINARY_PUSH base64 分片 + sha256 校验落盘）`  [EXTRACTED]
  processor/internal/daemon/README.md → docs/系统说明书v1.1.md
- `application.yml（restto-manager 配置：Hikari / Flyway / JWT / Netty / init）` --conceptually_related_to--> `JWT 鉴权（TokenInterceptor + @RequirePermission，admin bypass）`  [INFERRED]
  manager/server/src/main/resources/application.yml → docs/系统说明书v1.1.md
- `application.yml（restto-manager 配置：Hikari / Flyway / JWT / Netty / init）` --conceptually_related_to--> `Flyway 数据库迁移（V1–V3 共 17 张表，checksum repair-retry）`  [INFERRED]
  manager/server/src/main/resources/application.yml → docs/系统说明书v1.1.md

## Import Cycles
- 3-file cycle: `manager/client/src/api/auth.js -> manager/client/src/api/request.js -> manager/client/src/stores/auth.js -> manager/client/src/api/auth.js`
- 4-file cycle: `manager/client/src/api/auth.js -> manager/client/src/api/request.js -> manager/client/src/router/index.js -> manager/client/src/stores/auth.js -> manager/client/src/api/auth.js`

## Hyperedges (group relationships)
- **Netty 自定义帧协议两端字节级对齐（Go codec ↔ Java ProtocolCodec ↔ daemon/服务端）** — docs______v1_1_netty_protocol, processor_readme_protocol, manager_server_readme_protocolcodec, processor_internal_daemon_readme_daemon, manager_server_readme_spring_boot_backend [INFERRED 0.95]
- **Tool 注册与调度体系（agent 友好 CLI，cli 与 daemon 同源）** — processor_internal_daemon_readme_restto_cli, processor_internal_daemon_readme_daemon, processor_internal_tools_readme_registry, processor_internal_tools_readme_lookup, processor_internal_tools_readme_tool_contract, processor_internal_backupfile_readme_backup_file, processor_internal_backupmysql_readme_backup_mysql [EXTRACTED 1.00]
- **restto 三端架构（Vue 前端 + Spring Boot 后端 + Go 客户端）** — docs______v1_1_restto, manager_client_readme_vue_frontend, manager_server_readme_spring_boot_backend, processor_readme_go_client [EXTRACTED 1.00]

## Communities (70 total, 7 thin omitted)

### Community 0 - "Auth & REST Controllers"
Cohesion: 0.07
Nodes (33): java.lang.annotation.Retention, java.lang.annotation.Target, lombok.RequiredArgsConstructor, PageResult, Result, AuthController, BackupRecordController, BackupTaskController (+25 more)

### Community 1 - "Scheduler & Cache Internals"
Cohesion: 0.05
Nodes (22): Entry, NodeState, FAILED, RUNNING, SKIPPED, SUCCESS, WAITING, Decision (+14 more)

### Community 2 - "Role & RBAC Entities"
Cohesion: 0.08
Nodes (11): SysRoleCreateRequest, SysRoleUpdateRequest, SysRole, SysRoleMenu, SysRoleMapper, SysRoleMenuMapper, Override, SysRoleServiceImpl (+3 more)

### Community 3 - "Workflow Execution Engine"
Cohesion: 0.10
Nodes (11): java.util.concurrent.ThreadPoolExecutor, javax.annotation.PreDestroy, ExecContext, GraphBundle, Override, RunningNode, WorkflowExecutionServiceImpl, WorkflowExecutionServiceImplTest (+3 more)

### Community 4 - "Workflow DTOs & Views"
Cohesion: 0.10
Nodes (13): ExecutionView, NodeResult, WorkflowEdgeDto, WorkflowNodeDto, WorkflowSaveRequest, WorkflowView, Workflow, WorkflowScheduler (+5 more)

### Community 5 - "Frontend Dependencies & Tooling"
Cohesion: 0.05
Nodes (39): axios, element-plus, eslint, eslint-config-airbnb-base, eslint-plugin-import, eslint-plugin-vue, lodash-es, dependencies (+31 more)

### Community 6 - "Go Processor Unit Tests"
Cohesion: 0.10
Nodes (37): testing.T, TestEnvelopeSuccessShape(), TestParseBackupFileFlags(), TestParseBackupFileFlagsMissing(), TestParseBackupMysqlFlags(), TestParseJSONOrEmpty(), TestParseRunModule(), TestParseRunModuleDefaults() (+29 more)

### Community 7 - "User Seeding & Initialization"
Cohesion: 0.14
Nodes (16): DataInitializer, SysUser, SysUserRole, SysUserMapper, SysUserRoleMapper, PermissionCache, AuthServiceImpl, Override (+8 more)

### Community 8 - "Backup Tool Implementations"
Cohesion: 0.13
Nodes (29): Tool, MysqlParams, Tool, ToolErrorKind, archive/tar.Writer, os.FileInfo, buildEnvelope(), TestEnvelopeErrorShape() (+21 more)

### Community 9 - "User Management Frontend"
Cohesion: 0.08
Nodes (31): fetchAllRoles(), assignUserRoles(), createUser(), deleteUser(), fetchUsers(), resetUserPassword(), updateUser(), activeUser (+23 more)

### Community 10 - "System Spec & Constraint Docs"
Cohesion: 0.10
Nodes (35): AGENT.MD 仓库级约束, 受保护核心边界（.env / internal/core / migrations 不可修改）, 接口 Token 校验 + RESTful + 迁移只增不删约束, 备份任务（module + params_json + cron，BackupTaskScheduler 每分钟扫描）, 二进制热下发（BINARY_PUSH base64 分片 + sha256 校验落盘）, Flyway 数据库迁移（V1–V3 共 17 张表，checksum repair-retry）, JWT 鉴权（TokenInterceptor + @RequirePermission，admin bypass）, Netty 自定义二进制帧协议（magic RUST + length BE + type + JSON payload） (+27 more)

### Community 11 - "Request DTOs & Annotations"
Cohesion: 0.11
Nodes (10): com.baomidou.mybatisplus.annotation.TableName, lombok.Data, BinaryPushRequest, NodeCreateRequest, AssignMenusRequest, AssignPermissionsRequest, AssignRolesRequest, ResetPasswordRequest (+2 more)

### Community 12 - "Menu Tree Assembly"
Cohesion: 0.16
Nodes (7): MenuTreeNode, SysMenu, SysMenuMapper, MenuTreeBuilder, Override, SysMenuServiceImpl, MenuTreeBuilderTest

### Community 13 - "Workflow Canvas Designer"
Cohesion: 0.08
Nodes (22): addNode(), animate(), bezier(), bgCanvas, canvasWrap, dragEdge, dragEdgePath, edgePaths (+14 more)

### Community 14 - "Binary Push Service"
Cohesion: 0.13
Nodes (10): ClientBinary, SysOperLog, SysOperLogMapper, BinaryService, BinaryServiceImpl, Override, Override, OperLogServiceImpl (+2 more)

### Community 15 - "Go Protocol Codec"
Cohesion: 0.12
Nodes (21): bytes.Buffer, Encode(), MessageType, TaskCommand, MessageTypeFromByte(), ReadFrame(), TestEncodeThenDecodeRoundtrip(), TestRejectsBadMagic() (+13 more)

### Community 16 - "Role Management Frontend"
Cohesion: 0.08
Nodes (24): assignRoleMenus(), assignRolePermissions(), fetchRoleMenus(), activeRole, allPerms, editingId, form, formVisible (+16 more)

### Community 17 - "Workflow Persistence Entities"
Cohesion: 0.22
Nodes (12): com.baomidou.mybatisplus.core.mapper.BaseMapper, WorkflowEdge, WorkflowExecution, WorkflowNode, ClientBinaryMapper, SysRolePermissionMapper, WorkflowEdgeMapper, WorkflowExecutionMapper (+4 more)

### Community 18 - "Go Daemon Runtime"
Cohesion: 0.16
Nodes (20): session, context.Context, net.Conn, runDaemon(), BinaryPush, TaskResult, connectAndServe(), failedResult() (+12 more)

### Community 19 - "Task Management Frontend"
Cohesion: 0.12
Nodes (22): createTask(), deleteTask(), runTask(), updateTask(), dialogVisible, editingId, emptyForm(), form (+14 more)

### Community 20 - "Netty Server Handler"
Cohesion: 0.16
Nodes (7): com.baomidou.mybatisplus.extension.service.IService, io.netty.channel.ChannelHandlerContext, io.netty.channel.SimpleChannelInboundHandler, InboundMessage, Override, ServerChannelHandler, NodeService

### Community 21 - "Binary Upload Frontend"
Cohesion: 0.10
Nodes (20): pushBinary(), uploadBinary(), activeBinaryId, file, load(), loading, loadNodes(), nodes (+12 more)

### Community 22 - "Go CLI Entrypoint"
Cohesion: 0.30
Nodes (20): encoding/json.RawMessage, io.Reader, io.Writer, main(), marshalArgs(), parseBackupFileFlags(), parseBackupMysqlFlags(), parseJSONOrEmpty() (+12 more)

### Community 23 - "Operation Log Frontend"
Cohesion: 0.14
Nodes (17): clearLogs(), deleteLog(), fetchLogs(), detail, detailVisible, load(), loading, onClear() (+9 more)

### Community 24 - "Token Interceptor Internals"
Cohesion: 0.22
Nodes (5): HandlerMethod, Override, ClassAnnotated, MethodAnnotated, PermissionInterceptorTest

### Community 25 - "Configuration Properties"
Cohesion: 0.18
Nodes (8): io.jsonwebtoken.Claims, java.security.Key, BackupProperties, JwtProperties, NettyProperties, JwtUtil, JwtUtilTest, org.springframework.boot.context.properties.ConfigurationProperties

### Community 26 - "Error Handling Types"
Cohesion: 0.13
Nodes (12): lombok.Getter, BusinessException, ResultCode, BUSINESS_ERROR, FORBIDDEN, INTERNAL_ERROR, NOT_FOUND, PARAM_INVALID (+4 more)

### Community 27 - "Menu Management Frontend"
Cohesion: 0.16
Nodes (16): createMenu(), deleteMenu(), fetchMenuTree(), updateMenu(), editingId, emptyForm(), form, formVisible (+8 more)

### Community 28 - "Protocol Message Types"
Cohesion: 0.12
Nodes (10): com.fasterxml.jackson.databind.JsonNode, fromCode(), MessageType, BINARY_ACK, BINARY_PUSH, HEARTBEAT, REGISTER, REGISTER_ACK (+2 more)

### Community 29 - "Task Scheduler & Distributor"
Cohesion: 0.25
Nodes (10): javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse, lombok.extern.slf4j.Slf4j, BinaryDistributor, BackupTaskScheduler, PermissionInterceptor, Override, TokenInterceptor (+2 more)

### Community 30 - "Go Logger & Rotation"
Cohesion: 0.17
Nodes (13): log/slog.Level, os.File, sync.Mutex, time.Duration, time.Time, dailyWriter, Init(), LevelFromEnv() (+5 more)

### Community 31 - "Netty Codec & Connections"
Cohesion: 0.20
Nodes (6): io.netty.channel.Channel, io.netty.channel.EventLoopGroup, javax.annotation.PostConstruct, ProtocolCodec, ConnectionRegistry, NettyServer

### Community 32 - "Workflow List Frontend"
Cohesion: 0.14
Nodes (14): deleteWorkflow(), fetchWorkflows(), runWorkflow(), onRun(), load(), loading, onDelete(), onPage() (+6 more)

### Community 33 - "Backup Task Persistence"
Cohesion: 0.26
Nodes (6): TaskSaveRequest, BackupTask, BackupTaskMapper, BackupTaskService, BackupTaskServiceImpl, Override

### Community 34 - "Node Management Frontend"
Cohesion: 0.15
Nodes (15): createNode(), deleteNode(), createdToken, dialogVisible, form, load(), loading, onCreate() (+7 more)

### Community 35 - "Workflow History Frontend"
Cohesion: 0.15
Nodes (13): fetchExecutions(), getExecution(), detail, detailVisible, load(), loading, onPage(), openDetail() (+5 more)

### Community 36 - "Login & Auth Flow"
Cohesion: 0.20
Nodes (5): LoginRequest, LoginResponse, UserInfoResponse, Override, AuthService

### Community 37 - "Protocol Message Payloads"
Cohesion: 0.32
Nodes (10): lombok.AllArgsConstructor, lombok.NoArgsConstructor, BinaryAck, BinaryPush, Heartbeat, ProtocolMessages, RegisterAck, RegisterMessage (+2 more)

### Community 38 - "Frontend API Client"
Cohesion: 0.26
Nodes (8): fetchBinaries(), fetchNodes(), fetchRecords(), request, fetchTasks(), cards, loadStats(), stats

### Community 39 - "Vue App Bootstrap"
Cohesion: 0.18
Nodes (9): evaluate(), permissionDirective, app, registerDynamicRoutes(), resolveView(), router, routes, viewModules (+1 more)

### Community 40 - "Go Client Config"
Cohesion: 0.25
Nodes (13): cut(), Default(), FromEnv(), ClientConfig, LoadDotEnv(), splitLines(), TestDefaultValues(), TestFromEnvInvalidPortFallsBack() (+5 more)

### Community 41 - "Permission Management Frontend"
Cohesion: 0.18
Nodes (13): fetchPermissions(), fetchRolePermissions(), load(), loading, moduleOptions, onPage(), onSearch(), page (+5 more)

### Community 42 - "Backup Record Persistence"
Cohesion: 0.30
Nodes (5): BackupRecord, BackupRecordMapper, BackupRecordService, BackupRecordServiceImpl, Override

### Community 43 - "Permission Lookup Service"
Cohesion: 0.29
Nodes (6): com.baomidou.mybatisplus.extension.service.impl.ServiceImpl, SysPermission, SysPermissionMapper, Override, SysPermissionServiceImpl, SysPermissionService

### Community 44 - "Frame Decoder & Tests"
Cohesion: 0.26
Nodes (5): io.netty.buffer.ByteBuf, io.netty.handler.codec.MessageToMessageDecoder, FrameDecoder, Override, ProtocolCodecTest

### Community 45 - "Login Page & Auth Store"
Cohesion: 0.22
Nodes (10): fetchInfo(), fetchMenus(), login(), auth, form, formRef, loading, onSubmit() (+2 more)

### Community 46 - "Global Exception Handler"
Cohesion: 0.29
Nodes (6): GlobalExceptionHandler, org.springframework.http.ResponseEntity, org.springframework.validation.BindException, org.springframework.web.bind.annotation.ExceptionHandler, org.springframework.web.bind.annotation.RestControllerAdvice, org.springframework.web.bind.MethodArgumentNotValidException

### Community 47 - "Flyway & MyBatis Config"
Cohesion: 0.30
Nodes (8): com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor, FlywayConfig, RepairAndRetryStrategy, MyBatisPlusConfig, MybatisPlusInterceptor, org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy, org.springframework.context.annotation.Bean, org.springframework.context.annotation.Configuration

### Community 48 - "Tool Helpers & Tests"
Cohesion: 0.29
Nodes (9): echoTool, RequireStr(), TestRequireStrEmptyStringReturnsParamsError(), TestRequireStrInvalidJSONReturnsParamsError(), TestRequireStrMissingReturnsParamsError(), TestRequireStrPresent(), TestRequireStrWrongTypeReturnsParamsError(), TestToolInterfaceDispatchPropagatesError() (+1 more)

### Community 49 - "Spring Boot Bootstrap"
Cohesion: 0.27
Nodes (6): ManagerApplication, DotenvLoader, org.mybatis.spring.annotation.MapperScan, org.springframework.boot.autoconfigure.SpringBootApplication, org.springframework.boot.context.properties.ConfigurationPropertiesScan, org.springframework.scheduling.annotation.EnableScheduling

### Community 50 - "Role API Module"
Cohesion: 0.24
Nodes (9): createRole(), deleteRole(), fetchRoles(), updateRole(), load(), onDelete(), onPage(), onSave() (+1 more)

### Community 51 - "Backup Record Frontend"
Cohesion: 0.22
Nodes (9): load(), loading, onPage(), onSearch(), page, rows, size, taskId (+1 more)

### Community 52 - "Node Registry Service"
Cohesion: 0.40
Nodes (4): BackupNode, BackupNodeMapper, Override, NodeServiceImpl

### Community 53 - "Operation Log Aspect"
Cohesion: 0.35
Nodes (4): OperLogAspect, org.aspectj.lang.annotation.Around, org.aspectj.lang.annotation.Aspect, org.aspectj.lang.ProceedingJoinPoint

### Community 54 - "Tool Registry Core"
Cohesion: 0.31
Nodes (9): Tool, Lookup(), Registry(), TestLookupBackupFile(), TestLookupBackupMysql(), TestLookupUnknownReturnsNil(), TestRegistryCopyIsSafe(), TestRegistryIsNotEmpty() (+1 more)

### Community 57 - "Layout & Side Menu"
Cohesion: 0.22
Nodes (5): active, auth, initial, route, router

### Community 58 - "Flyway Migration Tests"
Cohesion: 0.36
Nodes (3): Override, FlywayConfigTest, org.flywaydb.core.Flyway

### Community 59 - "Web MVC Configuration"
Cohesion: 0.39
Nodes (5): Override, WebMvcConfig, org.springframework.web.servlet.config.annotation.CorsRegistry, org.springframework.web.servlet.config.annotation.InterceptorRegistry, org.springframework.web.servlet.config.annotation.WebMvcConfigurer

### Community 60 - "Go Crypto Utilities"
Cohesion: 0.43
Nodes (5): Sha256File(), Sha256Hex(), TestSha256FileMatchesBytes(), TestSha256FileMissingPath(), TestSha256KnownVector()

### Community 61 - "Workflow API Module"
Cohesion: 0.40
Nodes (5): createWorkflow(), getWorkflow(), updateWorkflow(), loadWorkflow(), onSave()

### Community 62 - "Canvas Drag Interactions"
Cohesion: 0.60
Nodes (5): startDrag(), onUp(), startEdge(), onMove(), onUp()

## Knowledge Gaps
- **197 isolated node(s):** `name`, `version`, `private`, `dev`, `build` (+192 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **7 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `PageResult` connect `Auth & REST Controllers` to `Backup Task Persistence`, `Role & RBAC Entities`, `Workflow Execution Engine`, `Workflow DTOs & Views`, `Protocol Message Payloads`, `User Seeding & Initialization`, `Backup Record Persistence`, `Request DTOs & Annotations`, `Permission Lookup Service`, `Binary Push Service`, `Workflow Persistence Entities`, `Netty Server Handler`, `Node Registry Service`, `Error Handling Types`, `Netty Codec & Connections`?**
  _High betweenness centrality (0.024) - this node is a cross-community bridge._
- **Why does `Result` connect `Auth & REST Controllers` to `Request DTOs & Annotations`, `Login & Auth Flow`, `Task Scheduler & Distributor`, `Global Exception Handler`?**
  _High betweenness centrality (0.022) - this node is a cross-community bridge._
- **Why does `BusinessException` connect `Error Handling Types` to `Auth & REST Controllers`, `Backup Task Persistence`, `Role & RBAC Entities`, `Workflow Execution Engine`, `Workflow DTOs & Views`, `Login & Auth Flow`, `Scheduler & Cache Internals`, `User Seeding & Initialization`, `Menu Tree Assembly`, `Global Exception Handler`, `Binary Push Service`, `Workflow Persistence Entities`, `Node Registry Service`, `Protocol Message Types`, `Netty Codec & Connections`?**
  _High betweenness centrality (0.018) - this node is a cross-community bridge._
- **What connects `name`, `version`, `private` to the rest of the system?**
  _197 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Auth & REST Controllers` be split into smaller, more focused modules?**
  _Cohesion score 0.07197121151539385 - nodes in this community are weakly interconnected._
- **Should `Scheduler & Cache Internals` be split into smaller, more focused modules?**
  _Cohesion score 0.052023988005997 - nodes in this community are weakly interconnected._
- **Should `Role & RBAC Entities` be split into smaller, more focused modules?**
  _Cohesion score 0.08408163265306122 - nodes in this community are weakly interconnected._