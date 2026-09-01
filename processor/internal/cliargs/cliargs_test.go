package cliargs

import (
	"bytes"
	"os"
	"path/filepath"
	"strings"
	"testing"
)

// 覆盖参数来源解析（内联 / 文件 / stdin / 缺省）与 JSON 输出格式（不触网 / 不起子进程）。

func TestParseJSONOrEmpty(t *testing.T) {
	if v, err := ParseJSONOrEmpty(""); err != nil || string(v) != "{}" {
		t.Fatalf("empty: %v %s", err, v)
	}
	if v, err := ParseJSONOrEmpty(`{"k":1}`); err != nil || string(v) != `{"k":1}` {
		t.Fatalf("json: %v %s", err, v)
	}
	if _, err := ParseJSONOrEmpty("{bad"); err == nil {
		t.Fatal("bad json should error")
	}
}

func TestResolveArgsDefaultsToEmptyObject(t *testing.T) {
	v, code := ResolveArgs("", "", os.Stdout, os.ReadFile, strings.NewReader(""))
	if code != 0 || string(v) != "{}" {
		t.Fatalf("v=%s code=%d", v, code)
	}
}

func TestResolveArgsInlineJSON(t *testing.T) {
	v, code := ResolveArgs(`{"path":"/data"}`, "", os.Stdout, os.ReadFile, strings.NewReader(""))
	if code != 0 || string(v) != `{"path":"/data"}` {
		t.Fatalf("v=%s code=%d", v, code)
	}
}

func TestResolveArgsInlineBadJSONErrors(t *testing.T) {
	_, code := ResolveArgs("{bad", "", os.Stdout, os.ReadFile, strings.NewReader(""))
	if code != 2 {
		t.Fatalf("code = %d, want 2", code)
	}
}

func TestResolveArgsFromFile(t *testing.T) {
	dir := t.TempDir()
	path := filepath.Join(dir, "args.json")
	if err := os.WriteFile(path, []byte(`{"k":42}`), 0o600); err != nil {
		t.Fatalf("write: %v", err)
	}
	v, code := ResolveArgs("", path, os.Stdout, os.ReadFile, strings.NewReader(""))
	if code != 0 || string(v) != `{"k":42}` {
		t.Fatalf("v=%s code=%d", v, code)
	}
}

func TestResolveArgsFileMissingErrors(t *testing.T) {
	var out bytes.Buffer
	_, code := ResolveArgs("", "/nonexistent/args.json", &out, os.ReadFile, strings.NewReader(""))
	if code != 2 {
		t.Fatalf("code = %d, want 2", code)
	}
	if !strings.Contains(out.String(), "read args-file") {
		t.Fatalf("output = %s", out.String())
	}
}

func TestResolveArgsFromStdin(t *testing.T) {
	v, code := ResolveArgs("-", "", os.Stdout, os.ReadFile, strings.NewReader(`{"s":1}`))
	if code != 0 || string(v) != `{"s":1}` {
		t.Fatalf("v=%s code=%d", v, code)
	}
}

func TestResolveArgsStdinBlankDefaultsToEmpty(t *testing.T) {
	v, code := ResolveArgs("-", "", os.Stdout, os.ReadFile, strings.NewReader("  \n"))
	if code != 0 || string(v) != "{}" {
		t.Fatalf("v=%s code=%d", v, code)
	}
}

func TestResolveArgsInlineWinsOverFile(t *testing.T) {
	// --args 优先级高于 --args-file。
	v, code := ResolveArgs(`{"a":1}`, "/nonexistent.json", os.Stdout, os.ReadFile, strings.NewReader(""))
	if code != 0 || string(v) != `{"a":1}` {
		t.Fatalf("v=%s code=%d", v, code)
	}
}

func TestWriteJSONFormat(t *testing.T) {
	// 两空格缩进 + 换行，键按字典序（map 语义），与主程序既有输出格式一致。
	var out bytes.Buffer
	WriteJSON(&out, map[string]any{"ok": true, "tool": "backup_file"})
	want := "{\n  \"ok\": true,\n  \"tool\": \"backup_file\"\n}\n"
	if out.String() != want {
		t.Fatalf("output = %q, want %q", out.String(), want)
	}
}

func TestTrimSpace(t *testing.T) {
	if TrimSpace(" \t a \r\n") != "a" {
		t.Fatal("trim failed")
	}
	if TrimSpace("") != "" {
		t.Fatal("empty should stay empty")
	}
}
