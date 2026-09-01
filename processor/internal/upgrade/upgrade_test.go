package upgrade

import (
	"bytes"
	"os"
	"path/filepath"
	"strings"
	"testing"
)

// 覆盖：新装 / 覆盖已存在 / 权限 / 目录自动创建 / 临时文件清理 / 大负载。
// Windows 分支（旧文件改名 .old）依赖运行平台，仅在 Windows 上实际生效，
// 类 Unix 平台通过代码评审覆盖（此仓主力开发环境为 Linux/WSL2）。

func TestInstallToNewPath(t *testing.T) {
	dir := t.TempDir()
	dest := filepath.Join(dir, "clis", "backup_file") // 父目录 clis 不存在，应自动创建
	payload := []byte("#!/bin/sh\nfake-cli\n")
	if err := InstallExecutable(payload, dest); err != nil {
		t.Fatalf("install: %v", err)
	}
	got, err := os.ReadFile(dest)
	if err != nil {
		t.Fatalf("read: %v", err)
	}
	if !bytes.Equal(got, payload) {
		t.Fatalf("content = %q", got)
	}
	info, err := os.Stat(dest)
	if err != nil {
		t.Fatalf("stat: %v", err)
	}
	if info.Mode().Perm()&0o111 == 0 {
		t.Fatalf("mode = %v, want exec bit", info.Mode())
	}
	// 不残留 .tmp-* 临时文件。
	entries, _ := os.ReadDir(filepath.Dir(dest))
	for _, e := range entries {
		if strings.HasPrefix(e.Name(), ".tmp-clis-") {
			t.Fatalf("temp file leaked: %s", e.Name())
		}
	}
}

func TestInstallOverwritesExisting(t *testing.T) {
	dir := t.TempDir()
	dest := filepath.Join(dir, "mod")
	if err := os.WriteFile(dest, []byte("old"), 0o755); err != nil {
		t.Fatalf("seed: %v", err)
	}
	if err := InstallExecutable([]byte("new-content"), dest); err != nil {
		t.Fatalf("install: %v", err)
	}
	got, _ := os.ReadFile(dest)
	if string(got) != "new-content" {
		t.Fatalf("content = %q", got)
	}
}

func TestInstallLargePayload(t *testing.T) {
	// 模拟二进制体积（1MB），验证写入完整性。
	dir := t.TempDir()
	dest := filepath.Join(dir, "big")
	payload := bytes.Repeat([]byte{0xA5}, 1<<20)
	if err := InstallExecutable(payload, dest); err != nil {
		t.Fatalf("install: %v", err)
	}
	got, err := os.ReadFile(dest)
	if err != nil || len(got) != len(payload) {
		t.Fatalf("size = %d err = %v", len(got), err)
	}
	if !bytes.Equal(got, payload) {
		t.Fatal("content mismatch")
	}
}

func TestInstallKeepsExecBitOnOverwrite(t *testing.T) {
	// 覆盖安装后仍需保持可执行位（rename 前对临时文件 chmod 0755）。
	dir := t.TempDir()
	dest := filepath.Join(dir, "mod")
	for i := 0; i < 2; i++ {
		if err := InstallExecutable([]byte("v"), dest); err != nil {
			t.Fatalf("round %d: %v", i, err)
		}
		info, err := os.Stat(dest)
		if err != nil {
			t.Fatalf("stat: %v", err)
		}
		if info.Mode().Perm()&0o111 == 0 {
			t.Fatalf("round %d: exec bit lost (%v)", i, info.Mode())
		}
	}
}

func TestInstallDestIsDirectoryErrors(t *testing.T) {
	// 目标路径是个目录：rename(file→dir) 失败，应报错且不残留临时文件。
	dir := t.TempDir()
	dest := filepath.Join(dir, "mod")
	if err := os.MkdirAll(dest, 0o755); err != nil {
		t.Fatalf("mkdir: %v", err)
	}
	if err := InstallExecutable([]byte("x"), dest); err == nil {
		t.Fatal("install onto directory should fail")
	}
	entries, _ := os.ReadDir(dir)
	for _, e := range entries {
		if strings.HasPrefix(e.Name(), ".tmp-clis-") {
			t.Fatalf("temp file leaked: %s", e.Name())
		}
	}
}

func TestInstallMkdirAllFails(t *testing.T) {
	// 父路径中存在同名普通文件 → MkdirAll 失败。
	dir := t.TempDir()
	blocker := filepath.Join(dir, "blocker")
	if err := os.WriteFile(blocker, []byte("f"), 0o644); err != nil {
		t.Fatalf("seed: %v", err)
	}
	if err := InstallExecutable([]byte("x"), filepath.Join(blocker, "sub", "mod")); err == nil {
		t.Fatal("should fail when parent is a file")
	}
}

func TestInstallStageFailureAborts(t *testing.T) {
	// 注入 stageFile 故障：应报错且不影响目标文件。
	dir := t.TempDir()
	dest := filepath.Join(dir, "mod")
	if err := os.WriteFile(dest, []byte("original"), 0o755); err != nil {
		t.Fatalf("seed: %v", err)
	}
	orig := stageFile
	defer func() { stageFile = orig }()
	stageFile = func(string, []byte) (string, error) {
		return "", os.ErrPermission
	}
	if err := InstallExecutable([]byte("x"), dest); err == nil {
		t.Fatal("stage failure should propagate")
	}
	got, _ := os.ReadFile(dest)
	if string(got) != "original" {
		t.Fatalf("dest corrupted: %q", got)
	}
}

func TestStageFileCreateTempFails(t *testing.T) {
	orig := newTempFile
	defer func() { newTempFile = orig }()
	newTempFile = func(string, string) (*os.File, error) {
		return nil, os.ErrPermission
	}
	if _, err := stageFile(t.TempDir(), []byte("x")); err == nil {
		t.Fatal("create failure should propagate")
	}
}

func TestStageFileWriteFailsOnClosedHandle(t *testing.T) {
	// 注入「已关闭的文件句柄」：Write 必然失败，覆盖写入错误清理分支。
	dir := t.TempDir()
	f, err := os.Create(filepath.Join(dir, "pre-created"))
	if err != nil {
		t.Fatalf("create: %v", err)
	}
	name := f.Name()
	if err := f.Close(); err != nil {
		t.Fatalf("close: %v", err)
	}
	orig := newTempFile
	defer func() { newTempFile = orig }()
	newTempFile = func(string, string) (*os.File, error) {
		return f, nil // 已关闭的句柄
	}
	if _, err := stageFile(dir, []byte("x")); err == nil {
		t.Fatal("write to closed handle should fail")
	}
	// 清理分支应已移除预创建文件。
	if _, statErr := os.Stat(name); statErr == nil {
		t.Fatal("staged temp file should be removed on write failure")
	}
}
