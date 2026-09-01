# daemon — 常驻进程

连接 Manager 的常驻客户端：注册 / 心跳 / 接收并执行任务 / 接收二进制下发
（自升级与第三方扩展包）。命令行用法见
[`client/restto/README.md`](../../client/restto/README.md)。

## 会话生命周期

```
connectAndServe（单次会话）
  ├─ 拨号 → 发 REGISTER → 等 REGISTER_ACK（失败即断开）
  ├─ 心跳 goroutine：按 HEARTBEAT_INTERVAL_SECS 周期发 HEARTBEAT
  ├─ 写出 goroutine：消费 frameCh 写 socket
  └─ 读循环：分发 TASK_COMMAND / BINARY_PUSH / 其他（记日志忽略）
Run（外层）
  └─ 会话结束 → 5s 后自动重连，直到 ctx 取消
```

## 任务执行（与 tool run 同源）

收到 `TASK_COMMAND{task_id, module, args}` 后：

1. 在途任务计数 +1；
2. 经 [`internal/runner`](../runner/) 以子进程执行 `clis/<module>`
   （`<cli> run --args -`，参数 JSON 走 stdin）；
3. 解析 CLI stdout 信封：成功 → 产物（file_path/size/checksum）映射为
   `TASK_RESULT{status:"success"}`；失败 → `error` 原文映射为
   `TASK_RESULT{status:"failed"}`；
4. 未安装对应 CLI 时错误文案为 `unknown module: <name>`（与历史版本一致）。

`tool run <module>` 走同一 runner，行为完全一致。

## 二进制下发路由（BINARY_PUSH）

`BINARY_PUSH.version` 为服务端管理员上传时的自由文本，客户端按约定路由
（`splitPushVersion`，取首个 `@` 前后）：

| version 形态         | 路由     | 行为                                                         |
| -------------------- | -------- | ------------------------------------------------------------ |
| `backup_file@1.2.0` | 扩展包   | `ValidModuleName` 校验（防路径穿越）→ sha256 校验 → `upgrade.InstallExecutable` 原子装入 `$DATA_DIR/clis/<module>` → ACK 成功后**立即可调度**（无需重启） |
| `1.0.2`             | 自升级   | sha256 校验 → 存档到 `$DATA_DIR/restto-client-<version>.bin` → 原子替换自身可执行文件 → ACK → 自动重启 |

分片累积：按 version 缓存 base64 解码后的字节，收齐（chunk_index+1 ==
total_chunks）后整体 sha256 校验，失败 NACK `checksum mismatch` 且不落盘。

## 自升级重启时序

```
替换自身成功 → ACK（成功）→ scheduleRestart（后台 goroutine）
  1. 等待在途任务清零（上限 restartWait，默认 30s；超时放行并告警）
  2. 固定延迟（默认 2s）：让 writer 协程把 ACK 帧刷到 socket
  3. doRestart → restarter()（默认 execSelf）
```

- `execSelf`：Unix 用 `syscall.Exec` 原地重启（同 PID，对 systemd / 容器编排
  透明，Go 的 fd 带 O_CLOEXEC 不泄漏连接）；Windows spawn 新进程后退出旧的。
- 重启失败仅记 Error 日志，旧进程继续服务（磁盘上已是新版本）。
- 测试经 `restarter` / `restartWait` / `restartFlushDelay` 注入缝模拟，绝不真正 exec。

## 关键注入缝（测试）

| 缝                     | 用途                                   |
| ---------------------- | -------------------------------------- |
| `session.runner`       | 指向临时目录的 runner（假 CLI）        |
| `session.exePath`      | 指向临时文件，验证替换行为             |
| `session.restarter`    | 记录器，验证重启恰好一次               |
| `restartFlushDelay`    | 包级 var，缩短 ACK 刷出等待            |

## 单测

```bash
go test ./internal/daemon/ -cover
```

覆盖：结果映射、snake_case 序列化、推送路由（扩展包/自升级/校验失败/非法名）、
重启时序（在途任务阻塞 / 超时放行 / 失败继续服务）、进程内假 Manager 的
端到端线路测试（注册 / 拒绝 / 任务 / 心跳 / 坏帧忽略）。
