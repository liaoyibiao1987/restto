// Package daemon 实现常驻 daemon：连接 Manager，注册 / 心跳 / 接收并执行任务 / 接收二进制下发。
//
// 任务调度经 tools.Lookup 按 module 名解析为对应 Tool 执行，
// 与 cli 的 tool run 共用同一套工具实现 —— 高内聚、行为一致。
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
	"time"

	"rustto-client/internal/common"
	"rustto-client/internal/core/config"
	"rustto-client/internal/core/crypto"
	"rustto-client/internal/core/protocol"
	"rustto-client/internal/tools"
)

// reconnectDelay 断线重连间隔。
const reconnectDelay = 5 * time.Second

// writerBufferSize 写出 channel 缓冲深度。
const writerBufferSize = 128

// Run daemon 入口：断线 5s 自动重连。
func Run(ctx context.Context, cfg config.ClientConfig) error {
	slog.Info("starting daemon",
		"manager", fmt.Sprintf("%s:%d", cfg.ManagerHost, cfg.ManagerPort),
		"node", cfg.NodeName)
	for {
		if err := connectAndServe(ctx, &cfg); err != nil {
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
}

// connectAndServe 建立一次会话：注册 → 心跳 → 接收并执行任务，直到连接断开。
func connectAndServe(ctx context.Context, cfg *config.ClientConfig) error {
	dialer := net.Dialer{}
	conn, err := dialer.DialContext(ctx, "tcp", net.JoinHostPort(cfg.ManagerHost, fmt.Sprintf("%d", cfg.ManagerPort)))
	if err != nil {
		return fmt.Errorf("connect manager failed: %w", err)
	}
	defer conn.Close() //nolint:errcheck // 会话收尾，关闭失败仅记日志
	slog.Info("connected to manager")

	sess := &session{
		frameCh:     make(chan []byte, writerBufferSize),
		binaryCache: map[string][]byte{},
		dataDir:     cfg.DataDir,
	}

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

// handleTaskCommand 执行单个任务指令并回传 TaskResult（按 module 名调度到注册的工具）。
func (s *session) handleTaskCommand(ctx context.Context, cmd protocol.TaskCommand) {
	slog.Info("task executing", "task_id", cmd.TaskID, "module", cmd.Module)
	var result *protocol.TaskResult
	if out, runErr := runModule(ctx, cmd.Module, cmd.Args); runErr != "" {
		slog.Error("task failed", "task_id", cmd.TaskID, "err", runErr)
		result = failedResult(cmd.TaskID, runErr)
	} else {
		result = outputToTaskResult(out, cmd.TaskID, "success")
	}
	if err := s.sendFrame(protocol.TypeTaskResult, result); err != nil {
		slog.Error("failed to report task result", "task_id", cmd.TaskID, "err", err)
	}
}

// runModule 按 module 名查找并运行工具（与 cli 的 tool run 同源）。
func runModule(ctx context.Context, name string, args json.RawMessage) (*common.ToolOutput, string) {
	tool := tools.Lookup(name)
	if tool == nil {
		return nil, fmt.Sprintf("unknown module: %s", name)
	}
	out, err := tool.Run(ctx, args)
	if err != nil {
		return nil, err.Error()
	}
	return out, ""
}

// handleBinaryPush 累积二进制分片，收齐后校验 sha256 并落盘，回传 BinaryAck。
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
	ok := actual == push.Checksum
	if ok {
		s.persistReceivedBinary(assembled, push.Version)
	} else {
		slog.Error("binary checksum mismatch",
			"version", push.Version, "expected", push.Checksum, "got", actual)
	}
	var ackError *string
	if !ok {
		msg := "checksum mismatch"
		ackError = &msg
	}
	ack := protocol.BinaryAck{Version: push.Version, Success: ok, Error: ackError}
	if err := s.sendFrame(protocol.TypeBinaryAck, &ack); err != nil {
		slog.Warn("failed to send binary ack", "err", err)
	}
}

// persistReceivedBinary 将收齐并通过校验的二进制写入 dataDir。
func (s *session) persistReceivedBinary(data []byte, version string) {
	if err := os.MkdirAll(s.dataDir, 0o755); err != nil {
		slog.Error("create data dir failed", "err", err)
		return
	}
	dest := filepath.Join(s.dataDir, fmt.Sprintf("rustto-client-%s.bin", version))
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
