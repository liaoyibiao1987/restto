package logger

import (
	"log/slog"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"
)

// 覆盖按天滚动文件写入与日志级别解析。

func TestDailyWriterWritesToFile(t *testing.T) {
	dir := t.TempDir()
	w := newDailyWriter(dir, "client.log")
	defer w.Close() //nolint:errcheck // 测试清理

	if _, err := w.Write([]byte("hello\n")); err != nil {
		t.Fatalf("write: %v", err)
	}

	today := time.Now().Format("2006-01-02")
	path := filepath.Join(dir, "client.log."+today)
	data, err := os.ReadFile(path)
	if err != nil {
		t.Fatalf("read log file: %v", err)
	}
	if !strings.Contains(string(data), "hello") {
		t.Fatalf("log content = %q, want contains 'hello'", data)
	}
}

func TestDailyWriterRolloverOnNewDay(t *testing.T) {
	dir := t.TempDir()
	w := newDailyWriter(dir, "client.log")
	defer w.Close() //nolint:errcheck

	// 写入“今天”。
	if _, err := w.Write([]byte("today\n")); err != nil {
		t.Fatalf("write: %v", err)
	}
	today := time.Now().Format("2006-01-02")
	if _, err := os.Stat(filepath.Join(dir, "client.log."+today)); err != nil {
		t.Fatalf("today file missing: %v", err)
	}

	// 伪造跨天：改内部日期后写，触发按“真实日期”重新滚动。
	// 由于真实日期不变，应重新打开今天 的文件（day 字段被纠正回 today）。
	w.mu.Lock()
	w.day = "2000-01-01"
	w.mu.Unlock()
	if _, err := w.Write([]byte("nextday\n")); err != nil {
		t.Fatalf("write next day: %v", err)
	}

	if w.day == "2000-01-01" {
		t.Fatal("stale day should have been corrected")
	}
	if w.day != today {
		t.Fatalf("day = %q, want %q", w.day, today)
	}
	// 两条日志都应落在今天的文件里。
	data, err := os.ReadFile(filepath.Join(dir, "client.log."+today))
	if err != nil {
		t.Fatalf("read log: %v", err)
	}
	if !strings.Contains(string(data), "today") || !strings.Contains(string(data), "nextday") {
		t.Fatalf("log content = %q, want both lines", data)
	}
}

func TestInitWritesBothSinks(t *testing.T) {
	dir := t.TempDir()
	flush := Init(dir, slog.LevelInfo)
	defer flush()

	slog.Info("core-message", "k", "v")

	today := time.Now().Format("2006-01-02")
	data, err := os.ReadFile(filepath.Join(dir, "client.log."+today))
	if err != nil {
		t.Fatalf("read log file: %v", err)
	}
	if !strings.Contains(string(data), "core-message") {
		t.Fatalf("file sink missing message: %q", data)
	}
}

func TestLevelFromEnv(t *testing.T) {
	cases := map[string]slog.Level{
		"debug": slog.LevelDebug,
		"INFO":  slog.LevelInfo,
		"warn":  slog.LevelWarn,
		"error": slog.LevelError,
		"":      slog.LevelInfo,
		"junk":  slog.LevelInfo,
	}
	for input, want := range cases {
		t.Setenv("LOG_LEVEL", input)
		if got := LevelFromEnv(); got != want {
			t.Fatalf("LevelFromEnv(%q) = %v, want %v", input, got, want)
		}
	}
}
