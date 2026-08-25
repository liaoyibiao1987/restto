package main

import (
	"bytes"
	"encoding/json"
	"os"
	"path/filepath"
	"strings"
	"testing"

	"restto-client/internal/common"
)

// 覆盖信封构造、参数解析与 CLI 子命令端到端行为（不触网 / 不起子进程）。

func TestEnvelopeSuccessShape(t *testing.T) {
	out := &common.ToolOutput{FilePath: "/x", Size: 10, Checksum: "abc"}
	env := buildEnvelope("backup_file", out, nil)
	data, _ := json.Marshal(env)
	var m map[string]any
	if err := json.Unmarshal(data, &m); err != nil {
		t.Fatalf("unmarshal: %v", err)
	}
	if m["ok"] != true || m["tool"] != "backup_file" {
		t.Fatalf("envelope header = %v", m)
	}
	output := m["output"].(map[string]any)
	if output["size"].(float64) != 10 || output["checksum"] != "abc" {
		t.Fatalf("output = %v", output)
	}
	if _, exists := m["error"]; exists {
		t.Fatal("error should be absent on success")
	}
}

func TestEnvelopeErrorShape(t *testing.T) {
	env := buildEnvelope("backup_file", nil, common.NewParamsError("missing args.path"))
	data, _ := json.Marshal(env)
	var m map[string]any
	if err := json.Unmarshal(data, &m); err != nil {
		t.Fatalf("unmarshal: %v", err)
	}
	if m["ok"] != false || m["tool"] != "backup_file" {
		t.Fatalf("envelope header = %v", m)
	}
	if !strings.Contains(m["error"].(string), "args.path") {
		t.Fatalf("error = %v", m["error"])
	}
	if _, exists := m["output"]; exists {
		t.Fatal("output should be absent on error")
	}
}

func TestParseJSONOrEmpty(t *testing.T) {
	if v, err := parseJSONOrEmpty(""); err != nil || string(v) != "{}" {
		t.Fatalf("empty: %v %s", err, v)
	}
	if v, err := parseJSONOrEmpty(`{"k":1}`); err != nil || string(v) != `{"k":1}` {
		t.Fatalf("json: %v %s", err, v)
	}
	if _, err := parseJSONOrEmpty("{bad"); err == nil {
		t.Fatal("bad json should error")
	}
}

func TestResolveArgsDefaultsToEmptyObject(t *testing.T) {
	v, code := resolveArgs("", "", os.Stdout, os.ReadFile, strings.NewReader(""))
	if code != 0 || string(v) != "{}" {
		t.Fatalf("v=%s code=%d", v, code)
	}
}

func TestResolveArgsInlineJSON(t *testing.T) {
	v, code := resolveArgs(`{"path":"/data"}`, "", os.Stdout, os.ReadFile, strings.NewReader(""))
	if code != 0 || string(v) != `{"path":"/data"}` {
		t.Fatalf("v=%s code=%d", v, code)
	}
}

func TestResolveArgsInlineBadJSONErrors(t *testing.T) {
	_, code := resolveArgs("{bad", "", os.Stdout, os.ReadFile, strings.NewReader(""))
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
	v, code := resolveArgs("", path, os.Stdout, os.ReadFile, strings.NewReader(""))
	if code != 0 || string(v) != `{"k":42}` {
		t.Fatalf("v=%s code=%d", v, code)
	}
}

func TestResolveArgsFromStdin(t *testing.T) {
	v, code := resolveArgs("-", "", os.Stdout, os.ReadFile, strings.NewReader(`{"s":1}`))
	if code != 0 || string(v) != `{"s":1}` {
		t.Fatalf("v=%s code=%d", v, code)
	}
}

func TestResolveArgsStdinBlankDefaultsToEmpty(t *testing.T) {
	v, code := resolveArgs("-", "", os.Stdout, os.ReadFile, strings.NewReader("  \n"))
	if code != 0 || string(v) != "{}" {
		t.Fatalf("v=%s code=%d", v, code)
	}
}

func TestParseBackupFileFlags(t *testing.T) {
	var out bytes.Buffer
	v, ok := parseBackupFileFlags([]string{"--path", "/data", "--dest", "/b/x.tar.gz"}, &out)
	if !ok {
		t.Fatalf("parse failed: %s", out.String())
	}
	var m map[string]any
	if err := json.Unmarshal(v, &m); err != nil {
		t.Fatalf("unmarshal: %v", err)
	}
	if m["path"] != "/data" || m["dest"] != "/b/x.tar.gz" {
		t.Fatalf("flags = %v", m)
	}
}

func TestParseBackupFileFlagsMissing(t *testing.T) {
	var out bytes.Buffer
	if _, ok := parseBackupFileFlags([]string{"--path", "/data"}, &out); ok {
		t.Fatal("missing --dest should fail")
	}
}

func TestParseBackupMysqlFlags(t *testing.T) {
	var out bytes.Buffer
	v, ok := parseBackupMysqlFlags([]string{
		"--user", "root", "--password", "p", "--database", "db", "--dest", "/b.sql.gz",
	}, &out)
	if !ok {
		t.Fatalf("parse failed: %s", out.String())
	}
	var m map[string]any
	if err := json.Unmarshal(v, &m); err != nil {
		t.Fatalf("unmarshal: %v", err)
	}
	if m["host"] != "127.0.0.1" || m["port"].(float64) != 3306 || m["database"] != "db" {
		t.Fatalf("flags = %v", m)
	}
}

func TestParseRunModule(t *testing.T) {
	var out bytes.Buffer
	name, args, ok := parseRunModule([]string{"backup_file", "--args", `{"a":1}`}, &out)
	if !ok || name != "backup_file" || args != `{"a":1}` {
		t.Fatalf("name=%q args=%q ok=%v", name, args, ok)
	}
}

func TestParseRunModuleDefaults(t *testing.T) {
	var out bytes.Buffer
	name, args, ok := parseRunModule([]string{"backup_file"}, &out)
	if !ok || name != "backup_file" || args != "{}" {
		t.Fatalf("name=%q args=%q ok=%v", name, args, ok)
	}
}

func TestRunToolList(t *testing.T) {
	var out bytes.Buffer
	code := runToolAction(nil, []string{"list"}, &out)
	if code != 0 {
		t.Fatalf("code = %d, output = %s", code, out.String())
	}
	var m map[string]any
	if err := json.Unmarshal(out.Bytes(), &m); err != nil {
		t.Fatalf("unmarshal: %v (%s)", err, out.String())
	}
	if len(m["tools"].([]any)) == 0 {
		t.Fatal("tools list empty")
	}
}

func TestRunToolSchemaUnknown(t *testing.T) {
	var out bytes.Buffer
	if code := runToolAction(nil, []string{"schema", "nope"}, &out); code != 2 {
		t.Fatalf("code = %d", code)
	}
}

func TestRunToolSchemaKnown(t *testing.T) {
	var out bytes.Buffer
	if code := runToolAction(nil, []string{"schema", "backup_file"}, &out); code != 0 {
		t.Fatalf("code = %d, output = %s", code, out.String())
	}
	var m map[string]any
	if err := json.Unmarshal(out.Bytes(), &m); err != nil {
		t.Fatalf("unmarshal: %v", err)
	}
	if m["name"] != "backup_file" {
		t.Fatalf("schema name = %v", m["name"])
	}
}

func TestRunToolRunEndToEnd(t *testing.T) {
	// 端到端：真实打包一个临时目录（走注册表 → 工具 → tar.gz → 信封输出）。
	dir := t.TempDir()
	src := filepath.Join(dir, "src")
	if err := os.MkdirAll(src, 0o755); err != nil {
		t.Fatalf("mkdir: %v", err)
	}
	if err := os.WriteFile(filepath.Join(src, "a.txt"), []byte("a"), 0o644); err != nil {
		t.Fatalf("write: %v", err)
	}
	dest := filepath.Join(dir, "out.tar.gz")

	var out bytes.Buffer
	code := run([]string{"tool", "run", "backup_file", "--args", `{"path":"` + src + `","dest":"` + dest + `"}`}, strings.NewReader(""), &out)
	if code != 0 {
		t.Fatalf("code = %d, output = %s", code, out.String())
	}
	var env map[string]any
	if err := json.Unmarshal(out.Bytes(), &env); err != nil {
		t.Fatalf("unmarshal: %v (%s)", err, out.String())
	}
	if env["ok"] != true {
		t.Fatalf("envelope = %s", out.String())
	}
}

func TestRunToolRunMissingArgFails(t *testing.T) {
	var out bytes.Buffer
	code := run([]string{"tool", "run", "backup_file", "--args", `{"path":"/whatever"}`}, strings.NewReader(""), &out)
	if code != 1 {
		t.Fatalf("code = %d, want 1", code)
	}
	var env map[string]any
	if err := json.Unmarshal(out.Bytes(), &env); err != nil {
		t.Fatalf("unmarshal: %v", err)
	}
	if env["ok"] != false {
		t.Fatalf("envelope = %s", out.String())
	}
}

func TestRunUnknownCommand(t *testing.T) {
	var out bytes.Buffer
	if code := run([]string{"nope"}, strings.NewReader(""), &out); code != 2 {
		t.Fatalf("code = %d", code)
	}
}

func TestRunHelpAndVersion(t *testing.T) {
	var out bytes.Buffer
	if code := run([]string{"--help"}, strings.NewReader(""), &out); code != 0 || !strings.Contains(out.String(), "用法") {
		t.Fatalf("help code=%d out=%s", code, out.String())
	}
	out.Reset()
	if code := run([]string{"--version"}, strings.NewReader(""), &out); code != 0 {
		t.Fatalf("version code=%d", code)
	}
}
