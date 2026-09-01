package daemon

import (
	"bytes"
	"context"
	"encoding/base64"
	"encoding/json"
	"errors"
	"os"
	"path/filepath"
	"runtime"
	"strings"
	"testing"
	"time"

	"restto-client/internal/common"
	"restto-client/internal/core/config"
	"restto-client/internal/core/crypto"
	"restto-client/internal/core/protocol"
	"restto-client/internal/runner"
)

// 覆盖产物→TaskResult 映射、失败结果构造与推送路由 / 升级重启。
// 测试绝不真正 exec：restarter 一律注入记录器。

func TestOutputMapsToSuccessResult(t *testing.T) {
	out := &common.ToolOutput{FilePath: "/a.tar.gz", Size: 5, Checksum: "ff"}
	r := outputToTaskResult(out, 7, "success")
	if r.TaskID != 7 {
		t.Fatalf("TaskID = %d", r.TaskID)
	}
	if r.Status != "success" {
		t.Fatalf("Status = %q", r.Status)
	}
	if r.FilePath == nil || *r.FilePath != "/a.tar.gz" {
		t.Fatalf("FilePath = %v", r.FilePath)
	}
	if r.Size == nil || *r.Size != 5 {
		t.Fatalf("Size = %v", r.Size)
	}
	if r.Checksum == nil || *r.Checksum != "ff" {
		t.Fatalf("Checksum = %v", r.Checksum)
	}
	if r.Error != nil {
		t.Fatalf("Error = %v, want nil", r.Error)
	}
}

func TestFailedResultShape(t *testing.T) {
	r := failedResult(9, "boom")
	if r.TaskID != 9 {
		t.Fatalf("TaskID = %d", r.TaskID)
	}
	if r.Status != "failed" {
		t.Fatalf("Status = %q", r.Status)
	}
	if r.Error == nil || *r.Error != "boom" {
		t.Fatalf("Error = %v", r.Error)
	}
	if r.FilePath != nil || r.Size != nil || r.Checksum != nil {
		t.Fatalf("success fields should be nil: %+v", r)
	}
}

func TestTaskResultJSONIsSnakeCase(t *testing.T) {
	// 与 Java Jackson SNAKE_CASE 对齐的关键字段名。
	r := failedResult(3, "x")
	data, err := json.Marshal(r)
	if err != nil {
		t.Fatalf("marshal: %v", err)
	}
	var m map[string]any
	if err := json.Unmarshal(data, &m); err != nil {
		t.Fatalf("unmarshal: %v", err)
	}
	for _, key := range []string{"task_id", "status", "error", "file_path", "size", "checksum"} {
		if _, ok := m[key]; !ok {
			t.Fatalf("json missing snake_case key %q: %s", key, data)
		}
	}
}

// newTestSession 构造指向临时 dataDir 的会话（runner 搜索空 clis 目录）。
func newTestSession(t *testing.T) *session {
	t.Helper()
	cfg := &config.ClientConfig{DataDir: t.TempDir()}
	return newSession(cfg, runner.NewRunner(runner.DefaultDirs(cfg.DataDir)...), "")
}

func TestRunModuleUnknown(t *testing.T) {
	s := newTestSession(t)
	out, errMsg := s.runModule(context.Background(), "nope", nil)
	if out != nil {
		t.Fatal("out should be nil for unknown module")
	}
	if errMsg != "unknown module: nope" {
		t.Fatalf("errMsg = %q", errMsg)
	}
}

// readFrameAs 从 frameCh 取一帧，断言类型后返回 JSON 载荷。
func readFrameAs(t *testing.T, s *session, want protocol.MessageType) json.RawMessage {
	t.Helper()
	select {
	case frame := <-s.frameCh:
		msgType, payload, err := protocol.ReadFrame(bytes.NewReader(frame))
		if err != nil {
			t.Fatalf("read frame: %v", err)
		}
		if msgType != want {
			t.Fatalf("frame type = %v, want %v", msgType, want)
		}
		return payload
	case <-time.After(2 * time.Second):
		t.Fatal("no frame within 2s")
		return nil
	}
}

// readAckFrame 从 frameCh 取一帧并解析为 BinaryAck。
func readAckFrame(t *testing.T, s *session) protocol.BinaryAck {
	t.Helper()
	var ack protocol.BinaryAck
	if err := json.Unmarshal(readFrameAs(t, s, protocol.TypeBinaryAck), &ack); err != nil {
		t.Fatalf("unmarshal ack: %v", err)
	}
	return ack
}

// makePush 构造单分片 BinaryPush（数据整体 base64 + 正确 sha256）。
func makePush(t *testing.T, version string, data []byte) protocol.BinaryPush {
	t.Helper()
	return protocol.BinaryPush{
		Version:     version,
		Checksum:    crypto.Sha256Hex(data),
		ChunkIndex:  0,
		TotalChunks: 1,
		Data:        base64.StdEncoding.EncodeToString(data),
	}
}

func TestMax64(t *testing.T) {
	if max64(3, 7) != 7 || max64(7, 3) != 7 || max64(0, 0) != 0 {
		t.Fatal("max64 broken")
	}
}

func TestSplitPushVersion(t *testing.T) {
	cases := []struct {
		in          string
		module      string
		ver         string
		isExtension bool
	}{
		{"backup_file@1.2.0", "backup_file", "1.2.0", true},
		{"1.0.2", "", "1.0.2", false},
		{"@1.0", "", "1.0", true},   // @ 在首位：module 空 → 走扩展包但校验拒绝
		{"a@b@c", "a", "b@c", true}, // 多 @ 取首个，版本部分原样保留
		{"", "", "", false},         // 空串 → 自升级路径（版本空）
		{"backupmysql@v2.0.1", "backupmysql", "v2.0.1", true},
	}
	for _, c := range cases {
		m, v, ext := splitPushVersion(c.in)
		if m != c.module || v != c.ver || ext != c.isExtension {
			t.Fatalf("splitPushVersion(%q) = (%q,%q,%v), want (%q,%q,%v)",
				c.in, m, v, ext, c.module, c.ver, c.isExtension)
		}
	}
}

func TestExtensionPushInstallsCLI(t *testing.T) {
	s := newTestSession(t)
	data := []byte("#!/bin/sh\necho fake-cli\n")

	s.handleBinaryPush(context.Background(), makePush(t, "backup_file@1.2.0", data))

	// 安装位置与内容 / 权限。
	dest := filepath.Join(s.cliDir, "backup_file")
	got, err := os.ReadFile(dest)
	if err != nil {
		t.Fatalf("extension not installed: %v", err)
	}
	if !bytes.Equal(got, data) {
		t.Fatalf("installed content mismatch: %q", got)
	}
	if fi, err := os.Stat(dest); err != nil || fi.Mode().Perm() != 0o755 {
		t.Fatalf("installed mode wrong: %v %v", fi, err)
	}
	// ACK 成功。
	if ack := readAckFrame(t, s); !ack.Success || ack.Version != "backup_file@1.2.0" {
		t.Fatalf("ack = %+v", ack)
	}
}

func TestExtensionPushInvalidModuleNameRejected(t *testing.T) {
	s := newTestSession(t)

	s.handleBinaryPush(context.Background(), makePush(t, "../evil@1.0", []byte("x")))

	if ack := readAckFrame(t, s); ack.Success || ack.Error == nil || *ack.Error != "invalid module name" {
		t.Fatalf("ack = %+v", ack)
	}
	// 确认未落盘任何东西。
	entries, _ := os.ReadDir(s.cliDir)
	if len(entries) != 0 {
		t.Fatalf("cliDir should be empty, got %v", entries)
	}
}

func TestBinaryPushChecksumMismatchNACKs(t *testing.T) {
	s := newTestSession(t)
	exePath := filepath.Join(t.TempDir(), "self")
	if err := os.WriteFile(exePath, []byte("OLD"), 0o755); err != nil {
		t.Fatalf("write self: %v", err)
	}
	s.exePath = exePath

	push := makePush(t, "1.0.2", []byte("NEW"))
	push.Checksum = "deadbeef" // 故意错的校验值
	s.handleBinaryPush(context.Background(), push)

	if ack := readAckFrame(t, s); ack.Success || ack.Error == nil || *ack.Error != "checksum mismatch" {
		t.Fatalf("ack = %+v", ack)
	}
	// 自身文件未被替换。
	got, _ := os.ReadFile(exePath)
	if string(got) != "OLD" {
		t.Fatalf("self binary must be untouched, got %q", got)
	}
}

func TestSelfUpgradeReplacesAndRestarts(t *testing.T) {
	// 缩短 ACK 刷出等待，测试不真等 2s。
	oldDelay := restartFlushDelay
	restartFlushDelay = time.Millisecond
	t.Cleanup(func() { restartFlushDelay = oldDelay })

	s := newTestSession(t)
	exePath := filepath.Join(t.TempDir(), "self")
	if err := os.WriteFile(exePath, []byte("OLD"), 0o755); err != nil {
		t.Fatalf("write self: %v", err)
	}
	s.exePath = exePath
	s.restartWait = 2 * time.Second
	restarted := make(chan error, 1)
	s.restarter = func() error { restarted <- nil; return nil }

	s.handleBinaryPush(context.Background(), makePush(t, "1.0.2", []byte("NEW-BINARY")))

	// ACK 成功。
	if ack := readAckFrame(t, s); !ack.Success || ack.Version != "1.0.2" {
		t.Fatalf("ack = %+v", ack)
	}
	// 自身文件被替换为新内容。
	got, err := os.ReadFile(exePath)
	if err != nil || string(got) != "NEW-BINARY" {
		t.Fatalf("self not replaced: %q %v", got, err)
	}
	// 存档也在 dataDir。
	arch, err := os.ReadFile(filepath.Join(s.dataDir, "restto-client-1.0.2.bin"))
	if err != nil || string(arch) != "NEW-BINARY" {
		t.Fatalf("archive missing: %q %v", arch, err)
	}
	// 重启恰好一次。
	select {
	case <-restarted:
	case <-time.After(2 * time.Second):
		t.Fatal("restarter not called")
	}
	select {
	case err := <-restarted:
		t.Fatalf("restarter called twice: %v", err)
	case <-time.After(50 * time.Millisecond):
	}
}

func TestSelfUpgradeWithoutExePathNACKs(t *testing.T) {
	s := newTestSession(t) // exePath 为空：禁用自升级

	s.handleBinaryPush(context.Background(), makePush(t, "1.0.2", []byte("NEW")))

	if ack := readAckFrame(t, s); ack.Success || ack.Error == nil || *ack.Error != "self path unavailable" {
		t.Fatalf("ack = %+v", ack)
	}
}

func TestScheduleRestartWaitsForInFlightTasks(t *testing.T) {
	oldDelay := restartFlushDelay
	restartFlushDelay = time.Millisecond
	t.Cleanup(func() { restartFlushDelay = oldDelay })

	s := newTestSession(t)
	s.restartWait = 5 * time.Second
	restarted := make(chan error, 1)
	s.restarter = func() error { restarted <- nil; return nil }

	s.tasks.Add(1) // 模拟一个在途任务
	s.scheduleRestart()

	// 在途任务未清零期间不应重启。
	select {
	case <-restarted:
		t.Fatal("restarted while task in flight")
	case <-time.After(150 * time.Millisecond):
	}

	s.tasks.Add(-1) // 任务完成 → 放行重启
	select {
	case <-restarted:
	case <-time.After(2 * time.Second):
		t.Fatal("restarter not called after drain")
	}
}

func TestScheduleRestartTimeoutWithStuckTasks(t *testing.T) {
	oldDelay := restartFlushDelay
	restartFlushDelay = time.Millisecond
	t.Cleanup(func() { restartFlushDelay = oldDelay })

	s := newTestSession(t)
	s.restartWait = 50 * time.Millisecond // 等待上限极短
	restarted := make(chan error, 1)
	s.restarter = func() error { restarted <- nil; return nil }

	s.tasks.Add(1) // 卡死的任务：超时也要重启
	s.scheduleRestart()

	select {
	case <-restarted:
	case <-time.After(2 * time.Second):
		t.Fatal("restarter not called after restartWait timeout")
	}
	s.tasks.Add(-1)
}

func TestMultiChunkBinaryPushAssembles(t *testing.T) {
	s := newTestSession(t)
	data := []byte("0123456789")
	sum := crypto.Sha256Hex(data)

	// 两个分片，乱序缓存但按 chunk_index 判断是否收齐。
	for _, part := range []struct {
		idx  uint32
		body []byte
	}{{0, data[:5]}, {1, data[5:]}} {
		s.handleBinaryPush(context.Background(), protocol.BinaryPush{
			Version:     "backup_file@2.0.0",
			Checksum:    sum,
			ChunkIndex:  part.idx,
			TotalChunks: 2,
			Data:        base64.StdEncoding.EncodeToString(part.body),
		})
	}

	if ack := readAckFrame(t, s); !ack.Success {
		t.Fatalf("ack = %+v", ack)
	}
	got, err := os.ReadFile(filepath.Join(s.cliDir, "backup_file"))
	if err != nil || string(got) != string(data) {
		t.Fatalf("assembled = %q %v", got, err)
	}
}

func TestDoRestartNilRestarterNoop(t *testing.T) {
	s := newTestSession(t)
	s.restarter = nil
	s.doRestart() // 不应 panic
}

func TestHandleTaskCommandFailedAndSuccess(t *testing.T) {
	s := newTestSession(t)
	// 失败分支：未安装的模块。
	s.handleTaskCommand(context.Background(), protocol.TaskCommand{TaskID: 5, Module: "nope"})
	var res protocol.TaskResult
	if err := json.Unmarshal(readFrameAs(t, s, protocol.TypeTaskResult), &res); err != nil {
		t.Fatalf("unmarshal: %v", err)
	}
	if res.TaskID != 5 || res.Status != "failed" || res.Error == nil || *res.Error != "unknown module: nope" {
		t.Fatalf("result = %+v", res)
	}

	// 成功分支：放一个假 CLI demo（runner 经 dataDir/clis 发现）。
	writeFakeDemoCLI(t, s.dataDir)
	s.handleTaskCommand(context.Background(), protocol.TaskCommand{TaskID: 6, Module: "demo"})
	if err := json.Unmarshal(readFrameAs(t, s, protocol.TypeTaskResult), &res); err != nil {
		t.Fatalf("unmarshal: %v", err)
	}
	if res.TaskID != 6 || res.Status != "success" || res.FilePath == nil || *res.FilePath != "/tmp/x.tar.gz" {
		t.Fatalf("result = %+v", res)
	}
}

func TestExtensionInstallFailureNACKs(t *testing.T) {
	s := newTestSession(t)
	// 目标位置已被目录占用 → 安装失败。
	if err := os.MkdirAll(filepath.Join(s.cliDir, "backup_file"), 0o755); err != nil {
		t.Fatalf("mkdir: %v", err)
	}

	s.handleBinaryPush(context.Background(), makePush(t, "backup_file@1.0.0", []byte("x")))

	ack := readAckFrame(t, s)
	if ack.Success || ack.Error == nil || !strings.HasPrefix(*ack.Error, "install failed") {
		t.Fatalf("ack = %+v", ack)
	}
}

func TestSelfUpgradeInstallFailureNACKs(t *testing.T) {
	oldDelay := restartFlushDelay
	restartFlushDelay = time.Millisecond
	t.Cleanup(func() { restartFlushDelay = oldDelay })

	s := newTestSession(t)
	restarted := make(chan error, 1)
	s.restarter = func() error { restarted <- nil; return nil }
	// exePath 指向已存在的目录 → 替换失败。
	s.exePath = filepath.Join(t.TempDir(), "occupied")
	if err := os.MkdirAll(s.exePath, 0o755); err != nil {
		t.Fatalf("mkdir: %v", err)
	}

	s.handleBinaryPush(context.Background(), makePush(t, "1.0.2", []byte("X")))

	ack := readAckFrame(t, s)
	if ack.Success || ack.Error == nil || !strings.HasPrefix(*ack.Error, "upgrade failed") {
		t.Fatalf("ack = %+v", ack)
	}
	select {
	case <-restarted:
		t.Fatal("must not restart after failed install")
	case <-time.After(100 * time.Millisecond):
	}
}

func TestSelfUpgradeRestarterErrorKeepsServing(t *testing.T) {
	oldDelay := restartFlushDelay
	restartFlushDelay = time.Millisecond
	t.Cleanup(func() { restartFlushDelay = oldDelay })

	s := newTestSession(t)
	s.exePath = filepath.Join(t.TempDir(), "self")
	if err := os.WriteFile(s.exePath, []byte("OLD"), 0o755); err != nil {
		t.Fatalf("write: %v", err)
	}
	s.restartWait = 50 * time.Millisecond
	s.restarter = func() error { return errors.New("exec boom") } // 重启失败：仅记日志

	s.handleBinaryPush(context.Background(), makePush(t, "1.0.2", []byte("NEW")))

	if ack := readAckFrame(t, s); !ack.Success {
		t.Fatalf("ack = %+v", ack)
	}
	// 进程不退出、不 panic 即为通过；短暂等待日志路径执行完。
	time.Sleep(100 * time.Millisecond)
}

func TestPersistReceivedBinaryErrorPaths(t *testing.T) {
	// dataDir 是普通文件 → MkdirAll 失败（仅记日志）。
	notADir := filepath.Join(t.TempDir(), "file")
	if err := os.WriteFile(notADir, []byte("x"), 0o644); err != nil {
		t.Fatalf("write: %v", err)
	}
	s := newTestSession(t)
	s.dataDir = notADir
	s.persistReceivedBinary([]byte("d"), "1.0")

	// 目标 .bin 位置被目录占用 → WriteFile 失败（仅记日志）。
	s2 := newTestSession(t)
	if err := os.MkdirAll(filepath.Join(s2.dataDir, "restto-client-9.9.9.bin"), 0o755); err != nil {
		t.Fatalf("mkdir: %v", err)
	}
	s2.persistReceivedBinary([]byte("d"), "9.9.9")
}

func TestSendFrameChannelFull(t *testing.T) {
	s := newTestSession(t)
	for i := 0; i < writerBufferSize; i++ {
		s.frameCh <- []byte("x")
	}
	if err := s.sendFrame(protocol.TypeBinaryAck, &protocol.BinaryAck{Version: "v"}); err == nil {
		t.Fatal("expected channel-full error")
	}
	// sendBinaryAck 满队列走 warn 分支，不应 panic。
	s.sendBinaryAck("v", false, "boom")
}

func TestExecSelfBadPathFails(t *testing.T) {
	if runtime.GOOS == "windows" {
		t.Skip("unix syscall.Exec 语义")
	}
	if err := execSelf("/nonexistent/path/to/self"); err == nil {
		t.Fatal("expected error for nonexistent path")
	}
}
