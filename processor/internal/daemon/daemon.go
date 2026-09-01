// Package daemon 实现常驻 daemon：连接 Manager，注册 / 心跳 / 接收并执行任务 /
// 接收二进制下发（自升级与第三方扩展包）。
//
// 任务调度经 internal/runner 以子进程执行 clis/ 下的外部 CLI 可执行程序
// （restto 主程序的 tool run 同源），CLI 的 stdout 信封被解析为统一的产物 /
// 错误模型后回传 TASK_RESULT —— 高内聚、行为一致。
//
// 二进制下发路由（version 为服务端管理员上传时的自由文本，无需改服务端）：
//
//	backup_file@1.2.0  → 扩展包：装入 <DATA_DIR>/clis/backup_file，立即可调度
//	1.0.2              → 自升级：替换自身可执行文件并自动重启
package daemon

import (
	"context"
	"encoding/base64"
	"encoding/json"
	"fmt"
	"log/slog"
	"net"
	"os"
	"path/filepath"
	"sync"
	"sync/atomic"
	"time"

	"restto-client/internal/common"
	"restto-client/internal/core/config"
	"restto-client/internal/core/crypto"
	"restto-client/internal/core/protocol"
	"restto-client/internal/runner"
	"restto-client/internal/upgrade"
)

// reconnectDelay 断线重连间隔。
const reconnectDelay = 5 * time.Second

// writerBufferSize 写出 channel 缓冲深度。
const writerBufferSize = 128

// restartFlushDelay 发送升级 ACK 后、重启前的等待：让 writer 协程把 ACK 帧
// 真正刷到 socket（sendFrame 只入队，写 socket 是异步的）。
// 包级 var 为测试注入缝（缩短等待），缺省 2s。
var restartFlushDelay = 2 * time.Second

// defaultRestartWait 升级重启前等待在途任务清零的上限。
const defaultRestartWait = 30 * time.Second

// drainPollInterval 等待在途任务清零的轮询间隔。
const drainPollInterval = 100 * time.Millisecond

// Run daemon 入口：断线 5s 自动重连。
//
// runner 与自身可执行文件路径在入口解析一次，跨重连会话复用；
// 自身路径解析失败仅禁用自升级（不影响任务执行）。
func Run(ctx context.Context, cfg config.ClientConfig) error {
	slog.Info("starting daemon",
		"manager", fmt.Sprintf("%s:%d", cfg.ManagerHost, cfg.ManagerPort),
		"node", cfg.NodeName)

	exePath, err := os.Executable()
	if err != nil {
		slog.Warn("resolve self executable failed; self-upgrade disabled", "err", err)
		exePath = ""
	}
	run := runner.NewRunner(runner.DefaultDirs(cfg.DataDir)...)

	for {
		if err := connectAndServe(ctx, &cfg, run, exePath); err != nil {
			slog.Error("session ended with error", "err", err)
		}
		select {
		case <-ctx.Done():
			return ctx.Err()
		case <-time.After(reconnectDelay):
			slog.Info("reconnecting", "delay", reconnectDelay.String())
		}
	}
}

// session 一次连接会话的运行时状态。
type session struct {
	frameCh chan []byte
	// binaryMu 保护 binaryCache。
	binaryMu    sync.Mutex
	binaryCache map[string][]byte
	dataDir     string
	// runner 外部 CLI 调度器（跨会话复用）。
	runner *runner.Runner
	// cliDir 扩展包安装目录（也是 runner 的最高优先级查找目录）。
	cliDir string
	// exePath 自身可执行文件路径；空串表示禁用自升级。
	exePath string
	// restarter 重启动作（默认 execSelf；测试注入记录器）。
	restarter func() error
	// restartWait 等待在途任务清零的上限。
	restartWait time.Duration
	// tasks 在途任务计数（升级重启前等待其清零）。
	tasks atomic.Int64
}

// newSession 构造会话：装配 runner、扩展包目录与重启动作。
func newSession(cfg *config.ClientConfig, run *runner.Runner, exePath string) *session {
	s := &session{
		frameCh:     make(chan []byte, writerBufferSize),
		binaryCache: map[string][]byte{},
		dataDir:     cfg.DataDir,
		runner:      run,
		cliDir:      filepath.Join(cfg.DataDir, "clis"),
		exePath:     exePath,
		restartWait: defaultRestartWait,
	}
	s.restarter = func() error { return execSelf(exePath) }
	return s
}

// connectAndServe 建立一次会话：注册 → 心跳 → 接收并执行任务，直到连接断开。
func connectAndServe(ctx context.Context, cfg *config.ClientConfig, run *runner.Runner, exePath string) error {
	dialer := net.Dialer{}
	conn, err := dialer.DialContext(ctx, "tcp", net.JoinHostPort(cfg.ManagerHost, fmt.Sprintf("%d", cfg.ManagerPort)))
	if err != nil {
		return fmt.Errorf("connect manager failed: %w", err)
	}
	defer conn.Close() //nolint:errcheck // 会话收尾，关闭失败仅记日志
	slog.Info("connected to manager")

	sess := newSession(cfg, run, exePath)

	// 会话级 context：写协程 / 心跳协程随连接一起退出。
	sessCtx, cancel := context.WithCancel(ctx)
	defer cancel()

	// 写出 goroutine：从 channel 取帧写入 socket。
	written := make(chan struct{})
	go func() {
		defer close(written)
		writeFrames(sessCtx, conn, sess.frameCh)
	}()

	// 注册。
	register := protocol.RegisterMessage{
		NodeName:  cfg.NodeName,
		NodeToken: cfg.NodeToken,
		Version:   cfg.Version,
	}
	if err := sess.sendFrame(protocol.TypeRegister, &register); err != nil {
		return err
	}

	// 心跳 goroutine。
	go spawnHeartbeat(sessCtx, sess.frameCh, cfg.HeartbeatIntervalSecs)

	// 主读循环。
	for {
		msgType, payload, err := protocol.ReadFrame(conn)
		if err != nil {
			cancel() // 通知写 / 心跳协程退出
			<-written
			return fmt.Errorf("read frame failed: %w", err)
		}
		switch msgType {
		case protocol.TypeRegisterAck:
			if err := handleRegisterAck(payload); err != nil {
				cancel()
				<-written
				return err
			}
		case protocol.TypeHeartbeat:
			slog.Debug("heartbeat from manager")
		case protocol.TypeTaskCommand:
			var cmd protocol.TaskCommand
			if err := json.Unmarshal(payload, &cmd); err != nil {
				slog.Warn("bad task command payload", "err", err)
				continue
			}
			go sess.handleTaskCommand(sessCtx, cmd)
		case protocol.TypeBinaryPush:
			var push protocol.BinaryPush
			if err := json.Unmarshal(payload, &push); err != nil {
				slog.Warn("bad binary push payload", "err", err)
				continue
			}
			go sess.handleBinaryPush(sessCtx, push)
		default:
			slog.Warn("ignoring unexpected message type", "type", msgType.String())
		}
	}
}

// writeFrames 消费 frameCh 并写入连接，直到 ctx 取消或写失败。
func writeFrames(ctx context.Context, conn net.Conn, frameCh <-chan []byte) {
	for {
		select {
		case <-ctx.Done():
			return
		case frame, ok := <-frameCh:
			if !ok {
				return
			}
			if _, err := conn.Write(frame); err != nil {
				slog.Debug("writer stopped", "err", err)
				return
			}
		}
	}
}

// spawnHeartbeat 周期发送心跳帧。
func spawnHeartbeat(ctx context.Context, frameCh chan<- []byte, intervalSecs uint64) {
	interval := time.Duration(max64(intervalSecs, 1)) * time.Second
	ticker := time.NewTicker(interval)
	defer ticker.Stop()
	for {
		select {
		case <-ctx.Done():
			return
		case <-ticker.C:
			hb := protocol.Heartbeat{Timestamp: time.Now().Unix()}
			frame, err := protocol.Encode(protocol.TypeHeartbeat, &hb)
			if err != nil {
				continue
			}
			select {
			case frameCh <- frame:
			case <-ctx.Done():
				return
			}
		}
	}
}

// sendFrame 编码并以帧形式投递到写出 channel。
func (s *session) sendFrame(messageType protocol.MessageType, payload any) error {
	frame, err := protocol.Encode(messageType, payload)
	if err != nil {
		return fmt.Errorf("encode frame failed: %w", err)
	}
	select {
	case s.frameCh <- frame:
		return nil
	default:
		return fmt.Errorf("writer channel full")
	}
}

// handleRegisterAck 处理注册应答：失败则返回错误终止当前会话。
func handleRegisterAck(payload json.RawMessage) error {
	var ack protocol.RegisterAck
	if err := json.Unmarshal(payload, &ack); err != nil {
		slog.Error("register rejected: invalid ack")
		return fmt.Errorf("register rejected: invalid ack")
	}
	if ack.Success {
		slog.Info("registered ok", "message", ack.Message)
		return nil
	}
	slog.Error("register rejected", "message", ack.Message)
	return fmt.Errorf("register rejected: %s", ack.Message)
}

// handleTaskCommand 执行单个任务指令并回传 TaskResult（经 runner 调度外部 CLI）。
func (s *session) handleTaskCommand(ctx context.Context, cmd protocol.TaskCommand) {
	slog.Info("task executing", "task_id", cmd.TaskID, "module", cmd.Module)
	s.tasks.Add(1)
	defer s.tasks.Add(-1)
	var result *protocol.TaskResult
	if out, runErr := s.runModule(ctx, cmd.Module, cmd.Args); runErr != "" {
		slog.Error("task failed", "task_id", cmd.TaskID, "err", runErr)
		result = failedResult(cmd.TaskID, runErr)
	} else {
		result = outputToTaskResult(out, cmd.TaskID, "success")
	}
	if err := s.sendFrame(protocol.TypeTaskResult, result); err != nil {
		slog.Error("failed to report task result", "task_id", cmd.TaskID, "err", err)
	}
}

// runModule 按 module 名经 runner 执行外部 CLI（与主程序 tool run 同源）。
//
// 未安装对应 CLI 时返回与历史进程内版本完全一致的 "unknown module: %s" 文案。
func (s *session) runModule(ctx context.Context, name string, args json.RawMessage) (*common.ToolOutput, string) {
	out, terr := s.runner.Run(ctx, name, args)
	if terr != nil {
		if terr.Kind == common.KindNotFound {
			return nil, fmt.Sprintf("unknown module: %s", name)
		}
		return nil, terr.Error()
	}
	return out, ""
}

// handleBinaryPush 累积二进制分片，收齐后校验 sha256 并按 version 路由：
// module@version → 扩展包安装；纯版本号 → 自升级（替换 + 自动重启）；回传 BinaryAck。
func (s *session) handleBinaryPush(ctx context.Context, push protocol.BinaryPush) {
	chunk, err := base64.StdEncoding.DecodeString(push.Data)
	if err != nil {
		slog.Warn("binary chunk decode failed", "err", err)
		return
	}

	s.binaryMu.Lock()
	s.binaryCache[push.Version] = append(s.binaryCache[push.Version], chunk...)
	if push.ChunkIndex+1 < push.TotalChunks {
		s.binaryMu.Unlock()
		return // 还有后续分片。
	}
	assembled := s.binaryCache[push.Version]
	delete(s.binaryCache, push.Version)
	s.binaryMu.Unlock()

	actual := crypto.Sha256Hex(assembled)
	if actual != push.Checksum {
		slog.Error("binary checksum mismatch",
			"version", push.Version, "expected", push.Checksum, "got", actual)
		s.sendBinaryAck(push.Version, false, "checksum mismatch")
		return
	}

	module, ver, isExtension := splitPushVersion(push.Version)
	if isExtension {
		s.installExtension(module, ver, assembled, push.Version)
		return
	}
	s.selfUpgrade(ver, assembled, push.Version)
}

// installExtension 把扩展包装入 cliDir/<module>，安装即生效（无需重启）。
func (s *session) installExtension(module, ver string, data []byte, ackVersion string) {
	if !runner.ValidModuleName(module) {
		slog.Warn("extension push rejected: invalid module name", "module", module)
		s.sendBinaryAck(ackVersion, false, "invalid module name")
		return
	}
	dest := filepath.Join(s.cliDir, module)
	if err := upgrade.InstallExecutable(data, dest); err != nil {
		slog.Error("install extension failed", "module", module, "err", err)
		s.sendBinaryAck(ackVersion, false, "install failed: "+err.Error())
		return
	}
	slog.Info("extension installed", "module", module, "version", ver, "path", dest, "bytes", len(data))
	s.sendBinaryAck(ackVersion, true, "")
}

// selfUpgrade 自升级：先落档备份，再原子替换自身可执行文件，ACK 后自动重启。
func (s *session) selfUpgrade(ver string, data []byte, ackVersion string) {
	s.persistReceivedBinary(data, ver)

	if s.exePath == "" {
		slog.Warn("self-upgrade skipped: self path unavailable", "version", ver)
		s.sendBinaryAck(ackVersion, false, "self path unavailable")
		return
	}
	if err := upgrade.InstallExecutable(data, s.exePath); err != nil {
		slog.Error("self-upgrade install failed", "version", ver, "err", err)
		s.sendBinaryAck(ackVersion, false, "upgrade failed: "+err.Error())
		return
	}
	slog.Info("self-upgrade applied", "version", ver, "path", s.exePath, "bytes", len(data))
	// 先 ACK 再重启：ACK 语义是「已收到且新版本已就位」。
	s.sendBinaryAck(ackVersion, true, "")
	s.scheduleRestart()
}

// scheduleRestart 后台执行重启前序：等在途任务清零（上限 restartWait）
// → 等 ACK 帧刷出（restartFlushDelay）→ 重启。
func (s *session) scheduleRestart() {
	go func() {
		deadline := time.Now().Add(s.restartWait)
		for s.tasks.Load() > 0 && time.Now().Before(deadline) {
			time.Sleep(drainPollInterval)
		}
		if s.tasks.Load() > 0 {
			slog.Warn("restart wait timeout with tasks in flight", "tasks", s.tasks.Load())
		}
		time.Sleep(restartFlushDelay)
		s.doRestart()
	}()
}

// doRestart 执行重启动作；失败仅记日志，旧进程继续服务
// （磁盘上已是新版本，下次外部重启自然生效）。
func (s *session) doRestart() {
	if s.restarter == nil {
		return
	}
	slog.Info("restarting to apply upgrade")
	if err := s.restarter(); err != nil {
		slog.Error("restart failed; keep serving with old image (new binary already on disk)", "err", err)
	}
}

// splitPushVersion 按首个 "@" 拆分下发的 version 字符串。
//
//	"backup_file@1.2.0" → ("backup_file", "1.2.0", true)   扩展包
//	"1.0.2"             → ("",           "1.0.2",  false)  自升级
//	"a@b@c"             → ("a",           "b@c",   true)    版本部分原样保留
func splitPushVersion(version string) (module, ver string, isExtension bool) {
	for i := 0; i < len(version); i++ {
		if version[i] == '@' {
			return version[:i], version[i+1:], true
		}
	}
	return "", version, false
}

// sendBinaryAck 回传二进制下发应答（失败时 msg 为原因）。
func (s *session) sendBinaryAck(version string, success bool, msg string) {
	var ackError *string
	if !success {
		ackError = &msg
	}
	ack := protocol.BinaryAck{Version: version, Success: success, Error: ackError}
	if err := s.sendFrame(protocol.TypeBinaryAck, &ack); err != nil {
		slog.Warn("failed to send binary ack", "err", err)
	}
}

// persistReceivedBinary 将收齐并通过校验的二进制存档到 dataDir（回滚 / 审计用）。
func (s *session) persistReceivedBinary(data []byte, version string) {
	if err := os.MkdirAll(s.dataDir, 0o755); err != nil {
		slog.Error("create data dir failed", "err", err)
		return
	}
	dest := filepath.Join(s.dataDir, fmt.Sprintf("restto-client-%s.bin", version))
	if err := os.WriteFile(dest, data, 0o755); err != nil {
		slog.Error("write received binary failed", "err", err)
		return
	}
	slog.Info("received new binary", "version", version, "path", dest, "bytes", len(data))
}

// failedResult 构造失败任务结果。
func failedResult(taskID int64, errMsg string) *protocol.TaskResult {
	return &protocol.TaskResult{
		TaskID: taskID,
		Status: "failed",
		Error:  &errMsg,
	}
}

// outputToTaskResult 把工具产物映射为成功 TaskResult。
func outputToTaskResult(out *common.ToolOutput, taskID int64, status string) *protocol.TaskResult {
	return &protocol.TaskResult{
		TaskID:   taskID,
		Status:   status,
		FilePath: &out.FilePath,
		Size:     &out.Size,
		Checksum: &out.Checksum,
	}
}

// max64 返回两值较大者（避免引入 math/max 泛型差异）。
func max64(a, b uint64) uint64 {
	if a > b {
		return a
	}
	return b
}
