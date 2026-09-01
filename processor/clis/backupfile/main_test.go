package main

import (
	"encoding/json"
	"os"
	"path/filepath"
	"strings"
	"testing"
)

// 覆盖 CLI 外壳：子命令分发、信封输出、stdout JSON 纯净性、退出码。
// 核心打包逻辑的测试在 backupfile_test.go（随包迁移）。

func TestRunBackupSuccess(t *testing.T) {
	dir := t.TempDir()
	src := filepath.Join(dir, "src")
	if err := os.MkdirAll(src, 0o755); err != nil {
		t.Fatalf("mkdir: %v", err)
	}
	if err := os.WriteFile(filepath.Join(src, "a.txt"), []byte("hello"), 0o644); err != nil {
		t.Fatalf("write: %v", err)
	}
	dest := filepath.Join(dir, "out.tar.gz")

	var out strings.Builder
	code := run([]string{"run", "--args", `{"path":"` + src + `","dest":"` + dest + `"}`}, strings.NewReader(""), &out)
	if code != 0 {
		t.Fatalf("code = %d, output = %s", code, out.String())
	}
	// stdout 必须是单个可解析 JSON 文档（防日志污染契约）。
	var env map[string]any
	if err := json.Unmarshal([]byte(out.String()), &env); err != nil {
		t.Fatalf("stdout not pure json: %v (%s)", err, out.String())
	}
	if env["ok"] != true || env["tool"] != Name {
		t.Fatalf("envelope = %s", out.String())
	}
	output := env["output"].(map[string]any)
	if output["file_path"] != dest {
		t.Fatalf("output = %v", output)
	}
	if _, err := os.Stat(dest); err != nil {
		t.Fatalf("artifact missing: %v", err)
	}
}

func TestRunBackupMissingParamsFails(t *testing.T) {
	var out strings.Builder
	code := run([]string{"run", "--args", `{"path":"/whatever"}`}, strings.NewReader(""), &out)
	if code != 1 {
		t.Fatalf("code = %d, want 1", code)
	}
	var env map[string]any
	if err := json.Unmarshal([]byte(out.String()), &env); err != nil {
		t.Fatalf("unmarshal: %v (%s)", err, out.String())
	}
	if env["ok"] != false {
		t.Fatalf("envelope = %s", out.String())
	}
	if !strings.Contains(env["error"].(string), "args.dest") {
		t.Fatalf("error = %v", env["error"])
	}
}

func TestRunBackupBadArgsUsage(t *testing.T) {
	var out strings.Builder
	if code := run([]string{"run", "--args", "{bad"}, strings.NewReader(""), &out); code != 2 {
		t.Fatalf("code = %d", code)
	}
	out.Reset()
	if code := run([]string{"run", "--no-such-flag"}, strings.NewReader(""), &out); code != 2 {
		t.Fatalf("code = %d", code)
	}
}

func TestRunBackupArgsFromStdin(t *testing.T) {
	dir := t.TempDir()
	src := filepath.Join(dir, "f.txt")
	if err := os.WriteFile(src, []byte("x"), 0o644); err != nil {
		t.Fatalf("write: %v", err)
	}
	dest := filepath.Join(dir, "o.tar.gz")
	argsJSON := `{"path":"` + src + `","dest":"` + dest + `"}`

	var out strings.Builder
	code := run([]string{"run", "--args", "-"}, strings.NewReader(argsJSON), &out)
	if code != 0 {
		t.Fatalf("code = %d, output = %s", code, out.String())
	}
	var env map[string]any
	if err := json.Unmarshal([]byte(out.String()), &env); err != nil || env["ok"] != true {
		t.Fatalf("envelope = %s (%v)", out.String(), err)
	}
}

func TestRunArgsFile(t *testing.T) {
	dir := t.TempDir()
	src := filepath.Join(dir, "f.txt")
	if err := os.WriteFile(src, []byte("x"), 0o644); err != nil {
		t.Fatalf("write: %v", err)
	}
	argsFile := filepath.Join(dir, "args.json")
	payload := `{"path":"` + src + `","dest":"` + filepath.Join(dir, "o.tar.gz") + `"}`
	if err := os.WriteFile(argsFile, []byte(payload), 0o600); err != nil {
		t.Fatalf("write args: %v", err)
	}
	var out strings.Builder
	if code := run([]string{"run", "--args-file", argsFile}, strings.NewReader(""), &out); code != 0 {
		t.Fatalf("code = %d, output = %s", code, out.String())
	}
}

func TestInfoSubcommand(t *testing.T) {
	var out strings.Builder
	if code := run([]string{"info"}, strings.NewReader(""), &out); code != 0 {
		t.Fatalf("code = %d", code)
	}
	var info map[string]any
	if err := json.Unmarshal([]byte(out.String()), &info); err != nil {
		t.Fatalf("unmarshal: %v (%s)", err, out.String())
	}
	if info["name"] != Name || info["description"] == "" || info["params_schema"] == nil {
		t.Fatalf("info = %s", out.String())
	}
}

func TestVersionAndHelp(t *testing.T) {
	var out strings.Builder
	if code := run([]string{"version"}, strings.NewReader(""), &out); code != 0 || !strings.Contains(out.String(), Name) {
		t.Fatalf("version code=%d out=%s", code, out.String())
	}
	out.Reset()
	if code := run([]string{"--help"}, strings.NewReader(""), &out); code != 0 || !strings.Contains(out.String(), "用法") {
		t.Fatalf("help code=%d out=%s", code, out.String())
	}
}

func TestUnknownCommandAndEmpty(t *testing.T) {
	var out strings.Builder
	if code := run([]string{"frobnicate"}, strings.NewReader(""), &out); code != 2 {
		t.Fatalf("code = %d", code)
	}
	out.Reset()
	if code := run(nil, strings.NewReader(""), &out); code != 2 {
		t.Fatalf("code = %d", code)
	}
}

func TestVersionFromEnv(t *testing.T) {
	t.Setenv("CLIENT_VERSION", "9.9.9")
	var out strings.Builder
	if code := run([]string{"version"}, strings.NewReader(""), &out); code != 0 || !strings.Contains(out.String(), "9.9.9") {
		t.Fatalf("version code=%d out=%s", code, out.String())
	}
}

func TestLevelFromEnv(t *testing.T) {
	cases := map[string]string{"debug": "DEBUG", "warn": "WARN", "error": "ERROR"}
	for env, want := range cases {
		t.Setenv("LOG_LEVEL", env)
		if got := levelFromEnv().String(); got != want {
			t.Fatalf("LOG_LEVEL=%s: got %s want %s", env, got, want)
		}
	}
	t.Setenv("LOG_LEVEL", "")
	if got := levelFromEnv().String(); got != "INFO" {
		t.Fatalf("default level = %s", got)
	}
}

func TestRedactArgs(t *testing.T) {
	if got := redactArgs(json.RawMessage(`{"path":"/a"}`)); got != `{"path":"/a"}` {
		t.Fatalf("small args = %s", got)
	}
	if got := redactArgs(json.RawMessage(`{"path":"` + strings.Repeat("x", 600) + `"}`)); !strings.Contains(got, "bytes") {
		t.Fatalf("large args = %s", got)
	}
	if got := redactArgs(nil); !strings.Contains(got, "bytes") {
		t.Fatalf("empty args = %s", got)
	}
}

func TestRunFlagRequiresValue(t *testing.T) {
	var out strings.Builder
	if code := run([]string{"run", "--args"}, strings.NewReader(""), &out); code != 2 {
		t.Fatalf("code = %d, out = %s", code, out.String())
	}
}
