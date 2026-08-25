// Package logger 实现日志初始化：按日期滚动写入日志目录，同时输出到 stdout。
//
// Init 返回的 flush 函数必须在进程退出前调用，否则缓冲日志会丢失。
package logger

import (
	"fmt"
	"io"
	"log/slog"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"time"
)

// dailyWriter 按天滚动文件：同一文件名整天复用，跨天自动切换。
type dailyWriter struct {
	mu   sync.Mutex
	dir  string
	base string // 如 "client.log"

	day          string // 当前所属日期 YYYY-MM-DD
	file         *os.File
	interval     time.Duration // 轮询间隔
	lastRollover time.Time
}

// newDailyWriter 创建按天滚动的写入器，文件名形如 client.log.2026-07-18。
func newDailyWriter(dir, base string) *dailyWriter {
	return &dailyWriter{
		dir:      dir,
		base:     base,
		interval: time.Minute,
	}
}

// Write 实现 io.Writer：跨天时先滚动再写。
func (w *dailyWriter) Write(p []byte) (int, error) {
	w.mu.Lock()
	defer w.mu.Unlock()
	today := time.Now().Format("2006-01-02")
	if w.file == nil || today != w.day {
		if err := w.rolloverLocked(today); err != nil {
			return 0, err
		}
	}
	return w.file.Write(p)
}

// rolloverLocked 关闭旧文件并打开新日期文件（调用方须持锁）。
func (w *dailyWriter) rolloverLocked(today string) error {
	if w.file != nil {
		_ = w.file.Close()
		w.file = nil
	}
	path := filepath.Join(w.dir, fmt.Sprintf("%s.%s", w.base, today))
	file, err := os.OpenFile(path, os.O_CREATE|os.O_APPEND|os.O_WRONLY, 0o644)
	if err != nil {
		return err
	}
	w.file = file
	w.day = today
	return nil
}

// Close 关闭底层文件。
func (w *dailyWriter) Close() error {
	w.mu.Lock()
	defer w.mu.Unlock()
	if w.file == nil {
		return nil
	}
	err := w.file.Close()
	w.file = nil
	return err
}

// Init 初始化全局 slog 记录器。
//
// - logDir: 日志目录，按天滚动，文件名形如 client.log.2026-07-18。
// - level: 最低日志级别（如 slog.LevelDebug / LevelInfo）。
// 返回 flush 函数：进程退出前调用，确保文件缓冲落盘。
func Init(logDir string, level slog.Level) (flush func()) {
	_ = os.MkdirAll(logDir, 0o755)

	fileWriter := newDailyWriter(logDir, "client.log")
	multi := io.MultiWriter(fileWriter, os.Stdout)

	handler := slog.NewTextHandler(multi, &slog.HandlerOptions{
		Level: level,
		// 文件里不要 ANSI 颜色；时间取本地时区。
		ReplaceAttr: func(groups []string, a slog.Attr) slog.Attr {
			if a.Key == slog.TimeKey && a.Value.Kind() == slog.KindTime {
				a.Value = slog.TimeValue(a.Value.Time().Local())
			}
			return a
		},
	})
	slog.SetDefault(slog.New(handler))

	return func() {
		_ = fileWriter.Close()
	}
}

// LevelFromEnv 从环境变量 LOG_LEVEL 解析日志级别，缺省 info。
//
// 支持 debug/info/warn/error（大小写不敏感）。
func LevelFromEnv() slog.Level {
	switch strings.ToLower(strings.TrimSpace(os.Getenv("LOG_LEVEL"))) {
	case "debug":
		return slog.LevelDebug
	case "warn", "warning":
		return slog.LevelWarn
	case "error":
		return slog.LevelError
	default:
		return slog.LevelInfo
	}
}
