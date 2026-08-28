# Graph Report - restto  (2026-08-28)

## Corpus Check
- Large corpus: 581 files · ~142,393 words. Semantic extraction will be expensive (many Claude tokens). Consider running on a subfolder.

## Summary
- 3487 nodes · 11334 edges · 141 communities (108 shown, 33 thin omitted)
- Extraction: 99% EXTRACTED · 1% INFERRED · 0% AMBIGUOUS · INFERRED: 161 edges (avg confidence: 0.86)
- Token cost: 44,000 input · 14,200 output

## Community Hubs (Navigation)
- Backup Task REST API
- Go CLI Tests
- Java Result/Message Tests
- Data Initializer
- Workflow Domain Model
- Exception & Result Handling
- File Backup Tool (Go)
- Role Request DTOs
- Permission Persistence
- Config & Result Snapshots
- User Request DTOs
- Go Daemon Runtime
- Auth Controller & Paging
- Task/Node/Binary Controllers
- Workflow DTOs (snapshot)
- Client Entry & Docs
- Backup Task Model
- Go CLI Envelope
- Role Management UI
- Workflow Graph Tests A
- Workflow Graph Tests B
- Workflow Graph Tests C
- Protocol Messages & Paging
- Client Dependencies
- Go CLI Entry & Flags
- Backup Entity Models
- Client Deps Snapshot (10831)
- Client Deps Snapshot (44433)
- Workflow DTOs (live)
- Auth & Workflow Controllers
- Workflow Node States
- Operation Log Aspect
- Binary/Record API Layer
- User Management UI
- Auth DTOs (snapshot)
- Menu Management Model
- Prompt Specs & Constraints
- JWT Utility
- Node Service (snapshot)
- Record Persistence
- Binary Distribution
- Workflow Execution Core
- Workflow Designer Canvas
- Role DTOs (live)
- User DTOs (live)
- MyBatis-Plus Config
- Permission Interceptor
- Netty Frame Decoder
- Workflow Execution Views
- Workflow Graph Engine
- Workflow Execution (snapshot)
- Spring Boot Application
- Go Protocol Codec
- Netty TCP Server
- Web MVC Config
- Binary Service (snapshot)
- Workflow Engine States
- Oper Log Persistence
- Auth API Layer
- Task Management UI
- App Shell & Permissions
- Task Scheduler
- Global Exception Handler
- Menu Model (snapshot)
- Binary Controller
- Binary Push & Codec
- Netty Channel Handler
- Menu Tree Builder
- Menu Service (snapshot)
- Oper Log UI
- Go Daily Logger
- Workflow Scheduler
- Workflow List UI
- Inbound Message Model
- Interceptor Annotation Tests A
- Interceptor Annotation Tests B
- Tool Registry (Go)
- Menu Management UI
- Node Management UI
- Interceptor Annotation Tests C
- Workflow History UI
- Node Service (live)
- Record Service
- Auth DTOs (live)
- Binary Service (live)
- Go Config Loader
- Oper Log Service
- Initializer Tests
- Workflow Execution Tests
- Netty Handler (snapshot)
- Execution Detail Views
- Go Tool Contract Tests
- Message Type (snapshot)
- Menu Service
- Record History UI
- Permission Management UI
- Flyway Migration Tests
- Workflow Node Tests
- Login Page (snapshot 44411)
- Login Page (snapshot 44424)
- Node State (snapshot 44425)
- Log Mask Tests (snapshot)
- Role Service Tests A
- Role Service Tests B
- Node State (live)
- Log Mask Tests
- Role Service Tests C
- Layout (snapshot 44411)
- Layout (snapshot 44423)
- Menu Tree Tests (snapshot)
- Permission Cache Tests
- Menu Tree Tests
- Workflow API Layer
- Designer Drag Interactions
- Task Scheduler Tests
- Go Crypto Utils
- JWT Auth Concepts
- Result Tests (snapshot 44424)
- Message Type Tests (snapshot)
- Protocol Codec Tests
- Permission Cache A
- Permission Cache B
- Dotenv Loader Tests
- Permission Cache C
- Cross Build Script (44411)
- Cross Build Script (44426)
- Cross Build Script
- Build Script (snapshot 44412)
- Build Script (snapshot 44426)
- Package Script (snapshot 44411)
- Package Script (snapshot 44426)
- Build Scripts Concepts
- Repo Constraints & System
- Build Script
- Package Script
- Vite Config
- Restto Client Binary

## God Nodes (most connected - your core abstractions)
1. `Result` - 217 edges
2. `RequirePermission` - 192 edges
3. `PageResult` - 185 edges
4. `OperLog` - 114 edges
5. `PageResult` - 95 edges
6. `PageResult` - 95 edges
7. `ResultCode` - 69 edges
8. `ResultCode` - 57 edges
9. `ResultCode` - 57 edges
10. `NodeState` - 54 edges

## Surprising Connections (you probably didn't know these)
- `系统说明书 v1.1（docx 转换副本）` --semantically_similar_to--> `restto 系统说明书 v1.1`  [INFERRED] [semantically similar]
  graphify-out/converted/系统说明书v1.1_02c5255b.md → docs/系统说明书v1.1.md
- `flow 提示词：备份管理工作流（DAG 编排/条件/并行串行/cron/拖拽设计器）需求` --rationale_for--> `工作流 DAG 编排（v1.1 新增）`  [INFERRED]
  .prompt/.improve/flow.md → docs/系统说明书v1.1.md
- `rustto Rust 客户端（Cargo workspace，v1.0）` --semantically_similar_to--> `restto-client（Go 备份客户端 / agent 友好 CLI）`  [EXTRACTED] [semantically similar]
  graphify-out/converted/系统说明书_0d7ba229.md → processor/README.md
- `DataInitializerTest` --references--> `DataInitializer`  [EXTRACTED]
  manager/server/src/test/java/com/restto/manager/bootstrap/DataInitializerTest.java → .history/manager/web/src/main/java/com/rustto/manager/bootstrap/DataInitializer_20260825144411.java
- `BinaryServiceImpl` --references--> `BackupProperties`  [EXTRACTED]
  manager/server/src/main/java/com/restto/manager/service/impl/backup/binary/BinaryServiceImpl.java → .history/manager/web/src/main/java/com/rustto/manager/config/BackupProperties_20260825144411.java

## Import Cycles
- 3-file cycle: `manager/client/src/api/auth.js -> manager/client/src/api/request.js -> manager/client/src/stores/auth.js -> manager/client/src/api/auth.js`
- 4-file cycle: `manager/client/src/api/auth.js -> manager/client/src/api/request.js -> manager/client/src/router/index.js -> manager/client/src/stores/auth.js -> manager/client/src/api/auth.js`

## Hyperedges (group relationships)
- **restto 三端分布式架构（Vue 前端 + SpringBoot 管理端 + Go 客户端，REST/JWT + Netty 协议互联）** — history_readme_20260825144424_restto_system, history_manager_client_readme_20260825144424_api_layer, history_manager_web_readme_20260825144424_rest_api, history_readme_20260825144424_jwt_auth, history_manager_web_readme_20260825144424_netty_server, history_readme_20260825144424_netty_protocol, history_processor_readme_20260825144426_go_client_daemon [EXTRACTED 1.00]
- **备份任务执行链路（scheduler 经 Netty 下发 TASK_COMMAND，daemon 按 module 经 Lookup 调度工具执行并回传 TASK_RESULT）** — history_processor_readme_20260825144426_task_command, history_processor_readme_20260825144426_task_result, history_readme_20260825144424_netty_protocol, history_processor_readme_20260825144426_go_client_daemon, history_processor_internal_tools_readme_20260825144426_lookup, history_processor_internal_backupfile_readme_20260825144426_backup_file_tool, history_processor_internal_backupmysql_readme_20260825144426_backup_mysql_tool [EXTRACTED 1.00]
- **agent 友好的工具注册与扩展机制（common.Tool 契约 + Registry/Lookup 注册中心，新增工具 cli/daemon 零改动）** — history_processor_readme_20260825144426_tool_contract, history_processor_internal_tools_readme_20260825144426_registry, history_processor_internal_tools_readme_20260825144426_lookup, history_processor_internal_backupfile_readme_20260825144426_backup_file_tool, history_processor_internal_backupmysql_readme_20260825144426_backup_mysql_tool, history_processor_readme_20260825144426_agent_friendly_cli, history_processor_internal_tools_readme_20260825144426_tool_registration_pattern [EXTRACTED 1.00]
- **备份任务执行链路（TASK_COMMAND → 工具执行 → TASK_RESULT 落库）** — manager_server_readme_spring_boot_backend, docs_____v1_1_netty_frame_protocol, processor_internal_daemon_readme_daemon, processor_internal_tools_readme_tool_registry, processor_internal_backupfile_readme_backup_file, processor_internal_backupmysql_readme_backup_mysql [EXTRACTED 1.00]
- **RBAC 鉴权授权体系（后端拦截 + 前端指令 + 动态菜单）** — docs_____v1_1_rbac, docs_____v1_1_jwt_auth, manager_client_readme_vue_frontend, manager_server_readme_spring_boot_backend [EXTRACTED 1.00]
- **restto 三端架构（Vue 前端 / Spring Boot 管理端 / Go 客户端）** — readme_restto, manager_client_readme_vue_frontend, manager_server_readme_spring_boot_backend, processor_readme_restto_client [EXTRACTED 1.00]

## Communities (141 total, 33 thin omitted)

### Community 0 - "Backup Task REST API"
Cohesion: 0.06
Nodes (21): Result, BackupTaskController, BackupTaskController, NodeController, NodeController, SysMenuController, SysRoleController, SysUserController (+13 more)

### Community 1 - "Go CLI Tests"
Cohesion: 0.03
Nodes (108): testing.T, TestEnvelopeErrorShape(), TestEnvelopeSuccessShape(), TestParseBackupFileFlags(), TestParseBackupFileFlagsMissing(), TestParseBackupMysqlFlags(), TestParseJSONOrEmpty(), TestParseRunModule() (+100 more)

### Community 2 - "Java Result/Message Tests"
Cohesion: 0.03
Nodes (21): ResultTest, MessageTypeTest, ProtocolCodecTest, BackupTaskSchedulerTest, BackupTaskSchedulerTest, WorkflowSchedulerTest, WorkflowSchedulerTest, JwtUtilTest (+13 more)

### Community 3 - "Data Initializer"
Cohesion: 0.09
Nodes (40): DataInitializer, Override, DataInitializer, Override, SysRole, SysUser, SysUser, SysUserRole (+32 more)

### Community 4 - "Workflow Domain Model"
Cohesion: 0.10
Nodes (46): com.baomidou.mybatisplus.core.mapper.BaseMapper, WorkflowSaveRequest, WorkflowView, Workflow, Workflow, WorkflowEdge, WorkflowEdge, WorkflowExecution (+38 more)

### Community 5 - "Exception & Result Handling"
Cohesion: 0.08
Nodes (48): com.baomidou.mybatisplus.extension.service.impl.ServiceImpl, BusinessException, BusinessException, PageResult, ResultCode, BUSINESS_ERROR, FORBIDDEN, INTERNAL_ERROR (+40 more)

### Community 6 - "File Backup Tool (Go)"
Cohesion: 0.08
Nodes (52): ToolErrorKind, archive/tar.Writer, os.FileInfo, appendDirAll(), appendFile(), BackupPath(), copyFileContent(), describeOutput() (+44 more)

### Community 7 - "Role Request DTOs"
Cohesion: 0.06
Nodes (13): SysRoleCreateRequest, SysRoleCreateRequest, SysRoleUpdateRequest, SysRoleUpdateRequest, SysRole, Override, SysRole, SysRoleServiceImpl (+5 more)

### Community 8 - "Permission Persistence"
Cohesion: 0.06
Nodes (23): SysPermission, SysPermission, SysPermissionMapper, SysPermissionMapper, Override, SysPermissionServiceImpl, Override, SysPermissionServiceImpl (+15 more)

### Community 9 - "Config & Result Snapshots"
Cohesion: 0.05
Nodes (21): AssignMenusRequest, AssignPermissionsRequest, AssignRolesRequest, BinaryPushRequest, ExecutionView, NodeResult, ExecutionView, NodeResult (+13 more)

### Community 10 - "User Request DTOs"
Cohesion: 0.06
Nodes (16): SysUserCreateRequest, SysUserCreateRequest, SysUserUpdateRequest, SysUserUpdateRequest, SysUserView, SysUserView, Override, SysUser (+8 more)

### Community 11 - "Go Daemon Runtime"
Cohesion: 0.08
Nodes (43): context.Context, net.Conn, runDaemon(), runDaemon(), connectAndServe(), failedResult(), session, handleRegisterAck() (+35 more)

### Community 12 - "Auth Controller & Paging"
Cohesion: 0.10
Nodes (17): PageResult, AuthController, BackupRecordController, BackupRecordController, SysPermissionController, SysPermissionController, SysRoleController, WorkflowExecutionController (+9 more)

### Community 13 - "Task/Node/Binary Controllers"
Cohesion: 0.10
Nodes (17): Result, OperLogController, OperLogController, AssignMenusRequest, BinaryPushRequest, NodeCreateRequest, OperLog, RequirePermission (+9 more)

### Community 14 - "Workflow DTOs (snapshot)"
Cohesion: 0.08
Nodes (18): WorkflowEdgeDto, WorkflowNodeDto, WorkflowSaveRequest, WorkflowView, Override, Workflow, WorkflowEdgeDto, WorkflowNodeDto (+10 more)

### Community 15 - "Client Entry & Docs"
Cohesion: 0.05
Nodes (45): manager/client index.html 入口（历史快照 44411）, manager/client index.html 入口（历史快照 44424）, manager/client src/main.js（Vue 应用入口）, restto manager 前端 README（历史快照 44411）, restto manager 前端 README（历史快照 44424）, restto manager README（历史快照 44411）, restto manager README（历史快照 44424）, restto manager/web Spring Boot README（历史快照 44411） (+37 more)

### Community 16 - "Backup Task Model"
Cohesion: 0.11
Nodes (12): TaskSaveRequest, TaskSaveRequest, BackupTask, BackupTask, BackupTaskService, BackupTaskService, BackupTaskServiceImpl, BackupTask (+4 more)

### Community 17 - "Go CLI Envelope"
Cohesion: 0.16
Nodes (40): encoding/json.RawMessage, io.Reader, io.Writer, buildEnvelope(), main(), marshalArgs(), parseBackupFileFlags(), parseBackupMysqlFlags() (+32 more)

### Community 18 - "Role Management UI"
Cohesion: 0.07
Nodes (37): fetchMenuTree(), fetchPermissions(), assignRoleMenus(), assignRolePermissions(), createRole(), deleteRole(), fetchRoleMenus(), fetchRolePermissions() (+29 more)

### Community 22 - "Protocol Messages & Paging"
Cohesion: 0.14
Nodes (25): BinaryAck, BinaryPush, Heartbeat, ProtocolMessages, RegisterAck, RegisterMessage, TaskCommand, BinaryAck (+17 more)

### Community 23 - "Client Dependencies"
Cohesion: 0.05
Nodes (39): lodash-es, dependencies, axios, element-plus, lodash-es, pinia, three, vue (+31 more)

### Community 24 - "Go CLI Entry & Flags"
Cohesion: 0.10
Nodes (38): buildEnvelope(), main(), marshalArgs(), parseBackupFileFlags(), parseBackupMysqlFlags(), parseJSONOrEmpty(), parseRunModule(), resolveArgs() (+30 more)

### Community 25 - "Backup Entity Models"
Cohesion: 0.06
Nodes (7): com.baomidou.mybatisplus.annotation.TableName, ClientBinary, SysRoleMenu, SysRoleMenu, SysRolePermission, SysRolePermission, WorkflowExecutionNode

### Community 26 - "Client Deps Snapshot (10831)"
Cohesion: 0.05
Nodes (37): dependencies, axios, element-plus, pinia, three, vue, vue-router, devDependencies (+29 more)

### Community 27 - "Client Deps Snapshot (44433)"
Cohesion: 0.05
Nodes (37): dependencies, axios, element-plus, pinia, three, vue, vue-router, devDependencies (+29 more)

### Community 28 - "Workflow DTOs (live)"
Cohesion: 0.11
Nodes (13): WorkflowEdgeDto, WorkflowNodeDto, WorkflowSaveRequest, WorkflowView, Workflow, WorkflowScheduler, WorkflowService, Override (+5 more)

### Community 29 - "Auth & Workflow Controllers"
Cohesion: 0.09
Nodes (8): AuthController, CurrentUser, UserContext, CurrentUser, UserContext, AuthController, CurrentUser, UserContext

### Community 30 - "Workflow Node States"
Cohesion: 0.09
Nodes (13): NodeState, FAILED, RUNNING, SKIPPED, SUCCESS, WAITING, Decision, READY (+5 more)

### Community 31 - "Operation Log Aspect"
Cohesion: 0.13
Nodes (6): OperLogAspect, OperLogAspect, OperLogAspect, org.aspectj.lang.annotation.Around, org.aspectj.lang.annotation.Aspect, org.aspectj.lang.ProceedingJoinPoint

### Community 32 - "Binary/Record API Layer"
Cohesion: 0.09
Nodes (28): fetchBinaries(), pushBinary(), uploadBinary(), fetchNodes(), fetchRecords(), fetchTasks(), activeBinaryId, file (+20 more)

### Community 33 - "User Management UI"
Cohesion: 0.08
Nodes (31): fetchAllRoles(), assignUserRoles(), createUser(), deleteUser(), fetchUsers(), resetUserPassword(), updateUser(), activeUser (+23 more)

### Community 34 - "Auth DTOs (snapshot)"
Cohesion: 0.11
Nodes (15): LoginRequest, LoginRequest, LoginResponse, LoginResponse, UserInfoResponse, UserInfoResponse, AuthService, AuthService (+7 more)

### Community 35 - "Menu Management Model"
Cohesion: 0.13
Nodes (9): MenuTreeNode, SysMenuRequest, SysMenu, MenuTreeNode, MenuTreeBuilder, Override, SysMenu, SysMenuServiceImpl (+1 more)

### Community 36 - "Prompt Specs & Constraints"
Cohesion: 0.14
Nodes (31): RBAC 权限管理系统提示词模板（五大模块通用模板）, fixinitdb 提示词：修复已有数据库连接报错 + 连接稳定性优化, flow 提示词：备份管理工作流（DAG 编排/条件/并行串行/cron/拖拽设计器）需求, manager 提示词：角色权限管理（用户/角色/权限/菜单/日志）+ 高科技风 UI 需求, processes 提示词：process 工具改造为 agent 友好 CLI（JSON 参数/子目录隔离/独立 README/编译脚本）, AGENT.MD 仓库级约束（受保护边界）, 二进制热下发（BINARY_PUSH 分片）, JWT 鉴权（HS256，默认 720 分钟） (+23 more)

### Community 37 - "JWT Utility"
Cohesion: 0.13
Nodes (9): JwtProperties, JwtProperties, JwtUtil, JwtUtil, io.jsonwebtoken.Claims, java.security.Key, JwtProperties, JwtUtil (+1 more)

### Community 38 - "Node Service (snapshot)"
Cohesion: 0.12
Nodes (9): BackupNode, BackupNode, Override, NodeServiceImpl, BackupNode, Override, NodeServiceImpl, NodeService (+1 more)

### Community 39 - "Record Persistence"
Cohesion: 0.16
Nodes (13): BackupRecord, BackupRecord, BackupRecordMapper, BackupRecordMapper, TaskResult, BackupRecordService, BackupRecordService, BackupRecordServiceImpl (+5 more)

### Community 40 - "Binary Distribution"
Cohesion: 0.10
Nodes (6): BinaryDistributor, ConnectionRegistry, ConnectionRegistry, io.netty.channel.Channel, ConnectionRegistry, org.springframework.stereotype.Component

### Community 41 - "Workflow Execution Core"
Cohesion: 0.15
Nodes (8): ExecContext, GraphBundle, ExecutionView, Override, WorkflowExecution, WorkflowExecutionNode, RunningNode, WorkflowExecutionServiceImpl

### Community 42 - "Workflow Designer Canvas"
Cohesion: 0.08
Nodes (22): addNode(), animate(), bezier(), bgCanvas, canvasWrap, dragEdge, dragEdgePath, edgePaths (+14 more)

### Community 43 - "Role DTOs (live)"
Cohesion: 0.12
Nodes (7): SysRoleCreateRequest, SysRoleUpdateRequest, SysRole, Override, SysRole, SysRoleServiceImpl, SysRoleService

### Community 44 - "User DTOs (live)"
Cohesion: 0.12
Nodes (8): SysUserCreateRequest, SysUserUpdateRequest, SysUserView, Override, SysUser, SysUserView, SysUserServiceImpl, SysUserService

### Community 45 - "MyBatis-Plus Config"
Cohesion: 0.13
Nodes (15): com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor, MybatisPlusInterceptor, MyBatisPlusConfig, MybatisPlusInterceptor, MyBatisPlusConfig, BackupTaskScheduler, BackupTaskScheduler, lombok.extern.slf4j.Slf4j (+7 more)

### Community 46 - "Permission Interceptor"
Cohesion: 0.19
Nodes (12): com.fasterxml.jackson.databind.ObjectMapper, Override, PermissionInterceptor, Override, TokenInterceptor, javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse, Override (+4 more)

### Community 47 - "Netty Frame Decoder"
Cohesion: 0.11
Nodes (13): FrameDecoder, Override, FrameDecoder, Override, InboundMessage, ProtocolCodec, InboundMessage, ProtocolCodec (+5 more)

### Community 48 - "Workflow Execution Views"
Cohesion: 0.17
Nodes (7): ExecContext, ExecutionView, Override, WorkflowExecution, WorkflowExecutionNode, RunningNode, WorkflowExecutionServiceImpl

### Community 49 - "Workflow Graph Engine"
Cohesion: 0.10
Nodes (8): GraphBundle, Decision, READY, SKIP, WAIT, Edge, Override, WorkflowGraph

### Community 50 - "Workflow Execution (snapshot)"
Cohesion: 0.17
Nodes (7): ExecContext, GraphBundle, Override, WorkflowExecution, WorkflowExecutionNode, RunningNode, WorkflowExecutionServiceImpl

### Community 51 - "Spring Boot Application"
Cohesion: 0.15
Nodes (10): ManagerApplication, ManagerApplication, DotenvLoader, DotenvLoader, ManagerApplication, DotenvLoader, org.mybatis.spring.annotation.MapperScan, org.springframework.boot.autoconfigure.SpringBootApplication (+2 more)

### Community 52 - "Go Protocol Codec"
Cohesion: 0.12
Nodes (21): bytes.Buffer, Encode(), MessageType, TaskCommand, MessageTypeFromByte(), ReadFrame(), TestEncodeThenDecodeRoundtrip(), TestRejectsBadMagic() (+13 more)

### Community 53 - "Netty TCP Server"
Cohesion: 0.15
Nodes (9): NettyProperties, NettyProperties, NettyServer, NettyServer, io.netty.channel.EventLoopGroup, javax.annotation.PostConstruct, javax.annotation.PreDestroy, NettyProperties (+1 more)

### Community 54 - "Web MVC Config"
Cohesion: 0.16
Nodes (13): Override, WebMvcConfig, Override, WebMvcConfig, Override, PermissionInterceptor, Override, TokenInterceptor (+5 more)

### Community 55 - "Binary Service (snapshot)"
Cohesion: 0.11
Nodes (8): ClientBinary, BinaryService, BinaryService, ClientBinary, Override, ClientBinary, Override, org.springframework.web.multipart.MultipartFile

### Community 56 - "Workflow Engine States"
Cohesion: 0.11
Nodes (7): Decision, READY, SKIP, WAIT, Edge, Override, WorkflowGraph

### Community 57 - "Oper Log Persistence"
Cohesion: 0.17
Nodes (10): SysOperLog, SysOperLog, SysOperLogMapper, SysOperLogMapper, Override, OperLogServiceImpl, Override, OperLogServiceImpl (+2 more)

### Community 58 - "Auth API Layer"
Cohesion: 0.13
Nodes (11): fetchInfo(), fetchMenus(), login(), request, auth, form, formRef, loading (+3 more)

### Community 59 - "Task Management UI"
Cohesion: 0.13
Nodes (21): createTask(), deleteTask(), runTask(), updateTask(), dialogVisible, editingId, emptyForm(), form (+13 more)

### Community 60 - "App Shell & Permissions"
Cohesion: 0.11
Nodes (14): evaluate(), permissionDirective, app, registerDynamicRoutes(), resolveView(), router, routes, viewModules (+6 more)

### Community 61 - "Task Scheduler"
Cohesion: 0.18
Nodes (7): TaskSaveRequest, BackupTask, BackupTaskScheduler, BackupTaskService, BackupTaskServiceImpl, BackupTask, Override

### Community 62 - "Global Exception Handler"
Cohesion: 0.21
Nodes (8): GlobalExceptionHandler, GlobalExceptionHandler, GlobalExceptionHandler, org.springframework.http.ResponseEntity, org.springframework.validation.BindException, org.springframework.web.bind.annotation.ExceptionHandler, org.springframework.web.bind.annotation.RestControllerAdvice, org.springframework.web.bind.MethodArgumentNotValidException

### Community 63 - "Menu Model (snapshot)"
Cohesion: 0.13
Nodes (5): MenuTreeNode, SysMenuRequest, SysMenu, SysMenu, SysMenuService

### Community 64 - "Binary Controller"
Cohesion: 0.12
Nodes (5): BinaryController, BinaryController, SysMenuController, BinaryController, org.springframework.web.bind.annotation.PostMapping

### Community 65 - "Binary Push & Codec"
Cohesion: 0.13
Nodes (11): BinaryDistributor, ProtocolCodec, fromCode(), MessageType, BINARY_ACK, BINARY_PUSH, HEARTBEAT, REGISTER (+3 more)

### Community 66 - "Netty Channel Handler"
Cohesion: 0.24
Nodes (6): InboundMessage, Override, ServerChannelHandler, Override, ServerChannelHandler, io.netty.channel.ChannelHandlerContext

### Community 67 - "Menu Tree Builder"
Cohesion: 0.22
Nodes (5): MenuTreeNode, MenuTreeNode, MenuTreeBuilder, MenuTreeNode, MenuTreeBuilder

### Community 68 - "Menu Service (snapshot)"
Cohesion: 0.18
Nodes (5): SysMenuRequest, Override, SysMenu, SysMenuServiceImpl, SysMenuService

### Community 69 - "Oper Log UI"
Cohesion: 0.15
Nodes (17): clearLogs(), deleteLog(), fetchLogs(), detail, detailVisible, load(), loading, onClear() (+9 more)

### Community 70 - "Go Daily Logger"
Cohesion: 0.17
Nodes (13): log/slog.Level, os.File, sync.Mutex, time.Duration, time.Time, dailyWriter, Init(), LevelFromEnv() (+5 more)

### Community 71 - "Workflow Scheduler"
Cohesion: 0.14
Nodes (4): WorkflowScheduler, WorkflowScheduler, WorkflowExecutionService, WorkflowExecutionService

### Community 72 - "Workflow List UI"
Cohesion: 0.14
Nodes (14): deleteWorkflow(), fetchWorkflows(), runWorkflow(), onRun(), load(), loading, onDelete(), onPage() (+6 more)

### Community 73 - "Inbound Message Model"
Cohesion: 0.15
Nodes (11): com.fasterxml.jackson.databind.JsonNode, InboundMessage, fromCode(), MessageType, BINARY_ACK, BINARY_PUSH, HEARTBEAT, REGISTER (+3 more)

### Community 74 - "Interceptor Annotation Tests A"
Cohesion: 0.21
Nodes (5): ClassAnnotated, HandlerMethod, PermissionInterceptor, MethodAnnotated, PermissionInterceptorTest

### Community 75 - "Interceptor Annotation Tests B"
Cohesion: 0.21
Nodes (5): ClassAnnotated, HandlerMethod, PermissionInterceptor, MethodAnnotated, PermissionInterceptorTest

### Community 76 - "Tool Registry (Go)"
Cohesion: 0.20
Nodes (13): Lookup(), Registry(), Lookup(), Registry(), Tool, Lookup(), Registry(), TestLookupBackupFile() (+5 more)

### Community 77 - "Menu Management UI"
Cohesion: 0.16
Nodes (15): createMenu(), deleteMenu(), updateMenu(), editingId, emptyForm(), form, formVisible, load() (+7 more)

### Community 78 - "Node Management UI"
Cohesion: 0.15
Nodes (15): createNode(), deleteNode(), createdToken, dialogVisible, form, load(), loading, onCreate() (+7 more)

### Community 79 - "Interceptor Annotation Tests C"
Cohesion: 0.21
Nodes (5): ClassAnnotated, HandlerMethod, PermissionInterceptor, MethodAnnotated, PermissionInterceptorTest

### Community 80 - "Workflow History UI"
Cohesion: 0.15
Nodes (13): fetchExecutions(), getExecution(), detail, detailVisible, load(), loading, onPage(), openDetail() (+5 more)

### Community 81 - "Node Service (live)"
Cohesion: 0.23
Nodes (5): BackupNode, NodeService, BackupNode, Override, NodeServiceImpl

### Community 82 - "Record Service"
Cohesion: 0.25
Nodes (7): BackupRecord, BackupRecordMapper, TaskResult, BackupRecordService, BackupRecordServiceImpl, BackupRecord, Override

### Community 83 - "Auth DTOs (live)"
Cohesion: 0.18
Nodes (7): LoginRequest, LoginResponse, UserInfoResponse, LoginResponse, Override, UserInfoResponse, AuthService

### Community 84 - "Binary Service (live)"
Cohesion: 0.23
Nodes (5): ClientBinary, BinaryService, BinaryServiceImpl, ClientBinary, Override

### Community 85 - "Go Config Loader"
Cohesion: 0.25
Nodes (12): cut(), Default(), FromEnv(), LoadDotEnv(), splitLines(), TestDefaultValues(), TestFromEnvInvalidPortFallsBack(), TestFromEnvOverridesDefaults() (+4 more)

### Community 86 - "Oper Log Service"
Cohesion: 0.28
Nodes (6): com.baomidou.mybatisplus.extension.service.IService, SysOperLog, SysOperLogMapper, Override, OperLogServiceImpl, OperLogService

### Community 87 - "Initializer Tests"
Cohesion: 0.23
Nodes (5): DataInitializer, DataInitializerTest, SysRole, SysUser, org.springframework.boot.DefaultApplicationArguments

### Community 88 - "Workflow Execution Tests"
Cohesion: 0.23
Nodes (4): WorkflowEdge, WorkflowExecutionServiceImpl, WorkflowNode, WorkflowExecutionServiceImplTest

### Community 89 - "Netty Handler (snapshot)"
Cohesion: 0.31
Nodes (3): InboundMessage, Override, ServerChannelHandler

### Community 90 - "Execution Detail Views"
Cohesion: 0.20
Nodes (4): ExecutionView, NodeResult, WorkflowExecutionService, ExecutionView

### Community 91 - "Go Tool Contract Tests"
Cohesion: 0.24
Nodes (8): echoTool, TestRequireStrEmptyStringReturnsParamsError(), TestRequireStrInvalidJSONReturnsParamsError(), TestRequireStrMissingReturnsParamsError(), TestRequireStrPresent(), TestRequireStrWrongTypeReturnsParamsError(), TestToolInterfaceDispatchPropagatesError(), TestToolInterfaceDispatchRuns()

### Community 92 - "Message Type (snapshot)"
Cohesion: 0.20
Nodes (9): fromCode(), MessageType, BINARY_ACK, BINARY_PUSH, HEARTBEAT, REGISTER, REGISTER_ACK, TASK_COMMAND (+1 more)

### Community 93 - "Menu Service"
Cohesion: 0.35
Nodes (3): Override, SysMenu, SysMenuServiceImpl

### Community 94 - "Record History UI"
Cohesion: 0.22
Nodes (9): load(), loading, onPage(), onSearch(), page, rows, size, taskId (+1 more)

### Community 95 - "Permission Management UI"
Cohesion: 0.22
Nodes (10): load(), loading, moduleOptions, onPage(), onSearch(), page, query, rows (+2 more)

### Community 96 - "Flyway Migration Tests"
Cohesion: 0.36
Nodes (3): Override, FlywayConfigTest, org.flywaydb.core.Flyway

### Community 98 - "Login Page (snapshot 44411)"
Cohesion: 0.25
Nodes (6): auth, form, formRef, loading, router, rules

### Community 99 - "Login Page (snapshot 44424)"
Cohesion: 0.25
Nodes (6): auth, form, formRef, loading, router, rules

### Community 100 - "Node State (snapshot 44425)"
Cohesion: 0.25
Nodes (6): NodeState, FAILED, RUNNING, SKIPPED, SUCCESS, WAITING

### Community 104 - "Node State (live)"
Cohesion: 0.25
Nodes (6): NodeState, FAILED, RUNNING, SKIPPED, SUCCESS, WAITING

### Community 107 - "Layout (snapshot 44411)"
Cohesion: 0.29
Nodes (5): active, auth, initial, route, router

### Community 108 - "Layout (snapshot 44423)"
Cohesion: 0.29
Nodes (5): active, auth, initial, route, router

### Community 112 - "Workflow API Layer"
Cohesion: 0.40
Nodes (5): createWorkflow(), getWorkflow(), updateWorkflow(), loadWorkflow(), onSave()

### Community 113 - "Designer Drag Interactions"
Cohesion: 0.60
Nodes (5): startDrag(), onUp(), startEdge(), onMove(), onUp()

### Community 115 - "Go Crypto Utils"
Cohesion: 0.40
Nodes (4): Sha256Hex(), TestSha256FileMatchesBytes(), TestSha256FileMissingPath(), TestSha256KnownVector()

### Community 116 - "JWT Auth Concepts"
Cohesion: 0.40
Nodes (5): manager/client api/ 层（Axios 封装 + Token 拦截器，dev 代理 /api -> 8080）, stores/auth.js（Pinia token 持久化）, manager/web RESTful 接口层（/api/auth、nodes、tasks、records、binaries）, restto.jwt 配置（JWT_SECRET / expire-minutes=720）, JWT Token 鉴权（登录签发、REST 接口校验、一次性节点 Token）

## Knowledge Gaps
- **332 isolated node(s):** `name`, `version`, `private`, `dev`, `build` (+327 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **33 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `PageResult` connect `Auth Controller & Paging` to `Backup Task REST API`, `Data Initializer`, `Workflow Domain Model`, `Exception & Result Handling`, `Role Request DTOs`, `Permission Persistence`, `Config & Result Snapshots`, `User Request DTOs`, `Task/Node/Binary Controllers`, `Workflow DTOs (snapshot)`, `Backup Task Model`, `Protocol Messages & Paging`, `Workflow DTOs (live)`, `Auth & Workflow Controllers`, `Node Service (snapshot)`, `Record Persistence`, `Workflow Execution Core`, `Role DTOs (live)`, `User DTOs (live)`, `Workflow Execution Views`, `Workflow Execution (snapshot)`, `Binary Service (snapshot)`, `Oper Log Persistence`, `Task Scheduler`, `Binary Controller`, `Workflow Scheduler`, `Node Service (live)`, `Record Service`, `Binary Service (live)`, `Oper Log Service`, `Execution Detail Views`?**
  _High betweenness centrality (0.044) - this node is a cross-community bridge._
- **Why does `Result` connect `Backup Task REST API` to `Binary Controller`, `Exception & Result Handling`, `Config & Result Snapshots`, `Auth Controller & Paging`, `Task/Node/Binary Controllers`, `Permission Interceptor`, `Auth & Workflow Controllers`, `Global Exception Handler`?**
  _High betweenness centrality (0.022) - this node is a cross-community bridge._
- **Why does `ResultCode` connect `Exception & Result Handling` to `Backup Task REST API`, `Auth DTOs (snapshot)`, `Data Initializer`, `Workflow Domain Model`, `Permission Persistence`, `Task/Node/Binary Controllers`, `Permission Interceptor`, `Web MVC Config`, `Result Code (snapshot 44411)`?**
  _High betweenness centrality (0.018) - this node is a cross-community bridge._
- **What connects `name`, `version`, `private` to the rest of the system?**
  _332 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Backup Task REST API` be split into smaller, more focused modules?**
  _Cohesion score 0.055006096734859775 - nodes in this community are weakly interconnected._
- **Should `Go CLI Tests` be split into smaller, more focused modules?**
  _Cohesion score 0.03288425322323627 - nodes in this community are weakly interconnected._
- **Should `Java Result/Message Tests` be split into smaller, more focused modules?**
  _Cohesion score 0.02868039664378337 - nodes in this community are weakly interconnected._