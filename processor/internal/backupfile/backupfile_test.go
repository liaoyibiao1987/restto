package backupfile

import (
	"archive/tar"
	"compress/gzip"
	"context"
	"encoding/json"
	"io"
	"os"
	"path/filepath"
	"strings"
	"testing"

	"rustto-client/internal/common"
)

// 覆盖打包成功（目录 / 单文件）、源缺失、Tool 契约调度、缺参校验与归档可解压。

func TestPacksADirectory(t *testing.T) {
	root := t.TempDir()
	src := filepath.Join(root, "src")
	if err := os.MkdirAll(filepath.Join(src, "nested"), 0o755); err != nil {
		t.Fatalf("mkdir: %v", err)
	}
	if err := os.WriteFile(filepath.Join(src, "hello.txt"), []byte("hi"), 0o644); err != nil {
		t.Fatalf("write: %v", err)
	}
	if err := os.WriteFile(filepath.Join(src, "nested", "deep.txt"), []byte("deep"), 0o644); err != nil {
		t.Fatalf("write nested: %v", err)
	}
	dest := filepath.Join(root, "out.tar.gz")

	out, err := BackupPath(src, dest)
	if err != nil {
		t.Fatalf("BackupPath: %v", err)
	}
	if !fileExists(dest) {
		t.Fatal("dest not created")
	}
	if out.Size == 0 {
		t.Fatal("size should be > 0")
	}
	if out.Checksum == "" {
		t.Fatal("checksum should not be empty")
	}

	// 解包验证内容。
	names := listArchive(t, dest)
	joined := strings.Join(names, ",")
	for _, want := range []string{"src/", "src/hello.txt", "src/nested/deep.txt"} {
		if !strings.Contains(joined, want) {
			t.Fatalf("archive entries %q missing %q", joined, want)
		}
	}
}

func TestPacksASingleFile(t *testing.T) {
	root := t.TempDir()
	src := filepath.Join(root, "note.txt")
	if err := os.WriteFile(src, []byte("hello"), 0o644); err != nil {
		t.Fatalf("write: %v", err)
	}
	dest := filepath.Join(root, "out.tar.gz")

	out, err := BackupPath(src, dest)
	if err != nil {
		t.Fatalf("BackupPath: %v", err)
	}
	abs, _ := filepath.Abs(dest)
	if out.FilePath != abs {
		t.Fatalf("FilePath = %q, want %q", out.FilePath, abs)
	}
	if !fileExists(dest) {
		t.Fatal("dest not created")
	}
	names := listArchive(t, dest)
	if len(names) != 1 || names[0] != "note.txt" {
		t.Fatalf("archive entries = %v, want [note.txt]", names)
	}
}

func TestErrorsWhenSourceMissing(t *testing.T) {
	root := t.TempDir()
	_, err := BackupPath(filepath.Join(root, "nope"), filepath.Join(root, "out.tar.gz"))
	if err == nil || err.Kind != common.KindParams {
		t.Fatalf("err = %v, want KindParams", err)
	}
}

func TestCreatesDestParentDirs(t *testing.T) {
	root := t.TempDir()
	src := filepath.Join(root, "a.txt")
	if err := os.WriteFile(src, []byte("x"), 0o644); err != nil {
		t.Fatalf("write: %v", err)
	}
	dest := filepath.Join(root, "deep", "nested", "out.tar.gz")
	if _, err := BackupPath(src, dest); err != nil {
		t.Fatalf("BackupPath: %v", err)
	}
	if !fileExists(dest) {
		t.Fatal("dest with parents not created")
	}
}

func TestToolRunsWithValidArgs(t *testing.T) {
	root := t.TempDir()
	src := filepath.Join(root, "src")
	if err := os.MkdirAll(src, 0o755); err != nil {
		t.Fatalf("mkdir: %v", err)
	}
	if err := os.WriteFile(filepath.Join(src, "a.txt"), []byte("a"), 0o644); err != nil {
		t.Fatalf("write: %v", err)
	}
	dest := filepath.Join(root, "out.tar.gz")

	args, _ := json.Marshal(map[string]string{"path": src, "dest": dest})
	out, err := Tool{}.Run(context.Background(), args)
	if err != nil {
		t.Fatalf("Run: %v", err)
	}
	if out.Size == 0 {
		t.Fatal("size should be > 0")
	}
}

func TestToolErrorsOnMissingArg(t *testing.T) {
	args, _ := json.Marshal(map[string]string{"path": "/tmp/whatever"}) // 缺 dest
	_, err := Tool{}.Run(context.Background(), args)
	if err == nil || err.Kind != common.KindParams {
		t.Fatalf("err = %v, want KindParams", err)
	}
}

func TestRunRespectsContextCancel(t *testing.T) {
	ctx, cancel := context.WithCancel(context.Background())
	cancel()
	args, _ := json.Marshal(map[string]string{"path": "/etc", "dest": "/tmp/x.tar.gz"})
	_, err := Tool{}.Run(ctx, args)
	if err == nil {
		t.Fatal("expected cancel error")
	}
}

func TestInfoNameIsStable(t *testing.T) {
	got := (Tool{}).Info().Name
	if got != "backup_file" {
		t.Fatalf("name = %q, want backup_file", got)
	}
}

// fileExists 判断路径存在。
func fileExists(path string) bool {
	_, err := os.Stat(path)
	return err == nil
}

// listArchive 解开 tar.gz 返回全部条目名。
func listArchive(t *testing.T, path string) []string {
	t.Helper()
	file, err := os.Open(path)
	if err != nil {
		t.Fatalf("open archive: %v", err)
	}
	defer file.Close() //nolint:errcheck

	gzReader, err := gzip.NewReader(file)
	if err != nil {
		t.Fatalf("gzip reader: %v", err)
	}
	defer gzReader.Close() //nolint:errcheck

	var names []string
	tarReader := tar.NewReader(gzReader)
	for {
		hdr, err := tarReader.Next()
		if err == io.EOF {
			break
		}
		if err != nil {
			t.Fatalf("tar next: %v", err)
		}
		names = append(names, hdr.Name)
	}
	return names
}
