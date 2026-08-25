package crypto

import (
	"os"
	"path/filepath"
	"testing"
)

// 覆盖 sha256 已知向量与文件流式摘要。

func TestSha256KnownVector(t *testing.T) {
	// sha256("abc") 的已知值。
	got := Sha256Hex([]byte("abc"))
	want := "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
	if got != want {
		t.Fatalf("Sha256Hex = %q, want %q", got, want)
	}
}

func TestSha256FileMatchesBytes(t *testing.T) {
	dir := t.TempDir()
	path := filepath.Join(dir, "data.bin")
	payload := []byte("hello restto go client")
	if err := os.WriteFile(path, payload, 0o600); err != nil {
		t.Fatalf("write: %v", err)
	}

	fileSum, err := Sha256File(path)
	if err != nil {
		t.Fatalf("Sha256File: %v", err)
	}
	if fileSum != Sha256Hex(payload) {
		t.Fatalf("file digest %q != bytes digest %q", fileSum, Sha256Hex(payload))
	}
}

func TestSha256FileMissingPath(t *testing.T) {
	_, err := Sha256File(filepath.Join(t.TempDir(), "nope.bin"))
	if err == nil {
		t.Fatal("expected error for missing file")
	}
}
