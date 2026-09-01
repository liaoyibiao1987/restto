package daemon

// 进程内假 Manager 的端到端线路测试：覆盖 connectAndServe / writeFrames /
// handleRegisterAck / handleTaskCommand（经 runner 调度外部 CLI 的真实链路）。

import (
	"context"
	"encoding/binary"
	"encoding/json"
	"net"
	"os"
	"path/filepath"
	"runtime"
	"strconv"
	"strings"
	"testing"
	"time"

	"restto-client/internal/core/config"
	"restto-client/internal/core/protocol"
	"restto-client/internal/runner"
)

// newFakeManager 在 127.0.0.1 随机端口监听一次连接；
// 返回监听地址与已接受连接的 channel。
func newFakeManager(t *testing.T) (string, chan net.Conn) {
	t.Helper()
	ln, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatalf("listen: %v", err)
	}
	connCh := make(chan net.Conn, 1)
	go func() {
		conn, err := ln.Accept()
		if err != nil {
			return
		}
		connCh <- conn
	}()
	t.Cleanup(func() {
		ln.Close()
		select {
		case c := <-connCh:
			c.Close()
		default:
		}
	})
	return ln.Addr().String(), connCh
}

// waitConn 等待客户端连入（带超时）。
func waitConn(t *testing.T, connCh chan net.Conn) net.Conn {
	t.Helper()
	select {
	case c := <-connCh:
		return c
	case <-time.After(3 * time.Second):
		t.Fatal("fake manager: no client connection")
		return nil
	}
}

// testConfig 构造指向假 Manager 的客户端配置（心跳间隔拉长，帧序列确定）。
func testConfig(t *testing.T, addr, dataDir string) *config.ClientConfig {
	t.Helper()
	host, portStr, err := net.SplitHostPort(addr)
	if err != nil {
		t.Fatalf("split addr: %v", err)
	}
	port, err := strconv.ParseUint(portStr, 10, 16)
	if err != nil {
		t.Fatalf("parse port: %v", err)
	}
	return &config.ClientConfig{
		ManagerHost:           host,
		ManagerPort:           uint16(port),
		NodeName:              "test-node",
		NodeToken:             "tok",
		Version:               "1.0.0-test",
		DataDir:               dataDir,
		HeartbeatIntervalSecs: 3600,
	}
}

// runSession 在后台跑一次 connectAndServe，返回结果 channel。
func runSession(ctx context.Context, cfg *config.ClientConfig) chan error {
	errCh := make(chan error, 1)
	go func() {
		errCh <- connectAndServe(ctx, cfg, runner.NewRunner(runner.DefaultDirs(cfg.DataDir)...), "")
	}()
	return errCh
}

// writeFakeDemoCLI 在 dataDir/clis 下放一个假 CLI demo：
// run 路径消费 stdin 后输出成功信封。
func writeFakeDemoCLI(t *testing.T, dataDir string) {
	t.Helper()
	if runtime.GOOS == "windows" {
		t.Skip("sh 假 CLI 仅支持类 Unix 系统")
	}
	cliDir := filepath.Join(dataDir, "clis")
	if err := os.MkdirAll(cliDir, 0o755); err != nil {
		t.Fatalf("mkdir: %v", err)
	}
	script := "#!/bin/sh\ncat >/dev/null\necho '{\"ok\":true,\"tool\":\"demo\",\"output\":{\"file_path\":\"/tmp/x.tar.gz\",\"size\":3,\"checksum\":\"ff\"}}'\n"
	if err := os.WriteFile(filepath.Join(cliDir, "demo"), []byte(script), 0o755); err != nil {
		t.Fatalf("write cli: %v", err)
	}
}

func TestWireSessionRegisterTaskResult(t *testing.T) {
	dataDir := t.TempDir()
	writeFakeDemoCLI(t, dataDir)

	addr, connCh := newFakeManager(t)
	cfg := testConfig(t, addr, dataDir)
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	errCh := runSession(ctx, cfg)

	srv := waitConn(t, connCh)
	defer srv.Close()

	// 1. 读 REGISTER。
	mt, payload, err := protocol.ReadFrame(srv)
	if err != nil {
		t.Fatalf("read register: %v", err)
	}
	if mt != protocol.TypeRegister {
		t.Fatalf("first frame type = %v", mt)
	}
	var reg protocol.RegisterMessage
	if err := json.Unmarshal(payload, &reg); err != nil || reg.NodeName != "test-node" {
		t.Fatalf("register = %s %v", payload, err)
	}

	// 2. 回 REGISTER_ACK 成功。
	ack, _ := protocol.Encode(protocol.TypeRegisterAck, &protocol.RegisterAck{Success: true, Message: "ok"})
	if _, err := srv.Write(ack); err != nil {
		t.Fatalf("write ack: %v", err)
	}

	// 3. 下发 TASK_COMMAND（假 CLI demo）。
	cmdFrame, _ := protocol.Encode(protocol.TypeTaskCommand, &protocol.TaskCommand{
		TaskID: 42, Module: "demo", Args: json.RawMessage(`{"k":"v"}`),
	})
	if _, err := srv.Write(cmdFrame); err != nil {
		t.Fatalf("write cmd: %v", err)
	}

	// 4. 读 TASK_RESULT。
	if err := srv.SetReadDeadline(time.Now().Add(5 * time.Second)); err != nil {
		t.Fatalf("deadline: %v", err)
	}
	mt2, payload2, err := protocol.ReadFrame(srv)
	if err != nil {
		t.Fatalf("read result: %v", err)
	}
	if mt2 != protocol.TypeTaskResult {
		t.Fatalf("result frame type = %v", mt2)
	}
	var res protocol.TaskResult
	if err := json.Unmarshal(payload2, &res); err != nil {
		t.Fatalf("unmarshal result: %v", err)
	}
	if res.TaskID != 42 || res.Status != "success" || res.FilePath == nil || *res.FilePath != "/tmp/x.tar.gz" {
		t.Fatalf("result = %+v", res)
	}

	// 5. 服务端断开 → 会话结束。
	if err := srv.Close(); err != nil {
		t.Fatalf("close: %v", err)
	}
	select {
	case err := <-errCh:
		if err == nil {
			t.Fatal("session should end with read error")
		}
	case <-time.After(5 * time.Second):
		t.Fatal("session did not end after server close")
	}
}

func TestWireSessionRegisterRejected(t *testing.T) {
	addr, connCh := newFakeManager(t)
	cfg := testConfig(t, addr, t.TempDir())
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	errCh := runSession(ctx, cfg)

	srv := waitConn(t, connCh)
	defer srv.Close()

	if _, _, err := protocol.ReadFrame(srv); err != nil { // REGISTER
		t.Fatalf("read register: %v", err)
	}
	ack, _ := protocol.Encode(protocol.TypeRegisterAck, &protocol.RegisterAck{Success: false, Message: "bad token"})
	if _, err := srv.Write(ack); err != nil {
		t.Fatalf("write ack: %v", err)
	}

	select {
	case err := <-errCh:
		if err == nil || err.Error() != "register rejected: bad token" {
			t.Fatalf("err = %v", err)
		}
	case <-time.After(5 * time.Second):
		t.Fatal("session did not end after reject")
	}
}

func TestWireSessionBadTaskPayloadIgnored(t *testing.T) {
	// 合法 JSON 帧但字段类型不符（task_id 为字符串）→ 结构反序列化失败，
	// 记 warn 后继续读循环，会话不中断。
	addr, connCh := newFakeManager(t)
	cfg := testConfig(t, addr, t.TempDir())
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	errCh := runSession(ctx, cfg)

	srv := waitConn(t, connCh)
	defer srv.Close()

	if _, _, err := protocol.ReadFrame(srv); err != nil { // REGISTER
		t.Fatalf("read register: %v", err)
	}
	body := []byte(`{"task_id":"not-a-number","module":"x"}`)
	frame := append([]byte{}, protocol.Magic[:]...)
	frame = binary.BigEndian.AppendUint32(frame, uint32(1+len(body)))
	frame = append(frame, byte(protocol.TypeTaskCommand))
	frame = append(frame, body...)
	if _, err := srv.Write(frame); err != nil {
		t.Fatalf("write bad frame: %v", err)
	}

	// 会话不应结束。
	time.Sleep(150 * time.Millisecond)
	select {
	case err := <-errCh:
		t.Fatalf("session ended unexpectedly: %v", err)
	default:
	}
	// 服务端断开 → 正常结束。
	if err := srv.Close(); err != nil {
		t.Fatalf("close: %v", err)
	}
	select {
	case <-errCh:
	case <-time.After(5 * time.Second):
		t.Fatal("session did not end after close")
	}
}

func TestWireSessionUnexpectedTypesIgnored(t *testing.T) {
	// 服务端下发 HEARTBEAT 与 TASK_RESULT（客户端不处理）→ 记日志后会话继续。
	addr, connCh := newFakeManager(t)
	cfg := testConfig(t, addr, t.TempDir())
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	errCh := runSession(ctx, cfg)

	srv := waitConn(t, connCh)
	defer srv.Close()

	if _, _, err := protocol.ReadFrame(srv); err != nil { // REGISTER
		t.Fatalf("read register: %v", err)
	}
	hb, _ := protocol.Encode(protocol.TypeHeartbeat, &protocol.Heartbeat{Timestamp: 1})
	if _, err := srv.Write(hb); err != nil {
		t.Fatalf("write hb: %v", err)
	}
	tr, _ := protocol.Encode(protocol.TypeTaskResult, &protocol.TaskResult{TaskID: 1, Status: "success"})
	if _, err := srv.Write(tr); err != nil {
		t.Fatalf("write tr: %v", err)
	}

	time.Sleep(150 * time.Millisecond)
	select {
	case err := <-errCh:
		t.Fatalf("session ended unexpectedly: %v", err)
	default:
	}
}

func TestWireSessionHeartbeatSent(t *testing.T) {
	addr, connCh := newFakeManager(t)
	cfg := testConfig(t, addr, t.TempDir())
	cfg.HeartbeatIntervalSecs = 1 // 1s 心跳
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	runSession(ctx, cfg)

	srv := waitConn(t, connCh)
	defer srv.Close()

	if _, _, err := protocol.ReadFrame(srv); err != nil { // REGISTER
		t.Fatalf("read register: %v", err)
	}
	// 最多 3s 内应收到一帧 HEARTBEAT。
	if err := srv.SetReadDeadline(time.Now().Add(3 * time.Second)); err != nil {
		t.Fatalf("deadline: %v", err)
	}
	for {
		mt, payload, err := protocol.ReadFrame(srv)
		if err != nil {
			t.Fatalf("read heartbeat: %v", err)
		}
		if mt != protocol.TypeHeartbeat {
			continue // 跳过注册等其他帧（payload 已消费）
		}
		var hb protocol.Heartbeat
		if err := json.Unmarshal(payload, &hb); err != nil || hb.Timestamp == 0 {
			t.Fatalf("heartbeat = %s %v", payload, err)
		}
		return
	}
}

func TestConnectAndServeDialFailure(t *testing.T) {
	// 占用一个端口再释放 → 拨号必失败。
	ln, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatalf("listen: %v", err)
	}
	addr := ln.Addr().String()
	if err := ln.Close(); err != nil {
		t.Fatalf("close: %v", err)
	}

	cfg := testConfig(t, addr, t.TempDir())
	if err := connectAndServe(context.Background(), cfg, runner.NewRunner(), ""); err == nil ||
		!strings.Contains(err.Error(), "connect manager failed") {
		t.Fatalf("err = %v", err)
	}
}

func TestRunReconnectLoopExitsOnCancel(t *testing.T) {
	addr, connCh := newFakeManager(t)
	cfg := testConfig(t, addr, t.TempDir())
	ctx, cancel := context.WithCancel(context.Background())
	errCh := make(chan error, 1)
	go func() { errCh <- Run(ctx, *cfg) }()

	srv := waitConn(t, connCh)
	defer srv.Close()
	if _, _, err := protocol.ReadFrame(srv); err != nil { // REGISTER
		t.Fatalf("read register: %v", err)
	}
	// 拒绝注册 → 会话结束 → Run 进入重连等待；随后取消 ctx → Run 退出。
	ack, _ := protocol.Encode(protocol.TypeRegisterAck, &protocol.RegisterAck{Success: false, Message: "nope"})
	if _, err := srv.Write(ack); err != nil {
		t.Fatalf("write ack: %v", err)
	}
	time.Sleep(100 * time.Millisecond)
	cancel()
	select {
	case err := <-errCh:
		if err != context.Canceled {
			t.Fatalf("err = %v", err)
		}
	case <-time.After(3 * time.Second):
		t.Fatal("Run did not exit after cancel")
	}
}
