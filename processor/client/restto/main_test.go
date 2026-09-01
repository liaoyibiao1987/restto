package main

import (
	"encoding/json"
	"os"
	"path/filepath"
	"runtime"
	"strings"
	"testing"
)

// 覆盖 CLI 子命令端到端行为：tool list / schema / run 经 runner 调度假 CLI
// （sh 脚本置于 $DATA_DIR/clis 下，与生产扩展包安装路径一致）。
// 信封构造的测试在 internal/common/envelope_test.go；参数解析在 internal/cliargs。

// writeFakeCLI 在 $DATA_DIR/clis 下放置假 CLI 并返回其脚本路径。
// script 为 sh 脚本内容：$1 = 子命令（run/info），run 时参数 JSON 在 stdin。
func writeFakeCLI(t *testing.T, name, script string) string {
	t.Helper()
	if runtime.GOOS == "windows" {
		t.Skip("sh 假 CLI 仅支持类 Unix 系统")
	}
	dataDir := t.TempDir()
	cliDir := filepath.Join(dataDir, "clis")
	if err := os.MkdirAll(cliDir, 0o755); err != nil {
		t.Fatalf("mkdir: %v", err)
	}
	path := filepath.Join(cliDir, name)
	if err := os.WriteFile(path, []byte(script), 0o755); err != nil {
		t.Fatalf("write cli: %v", err)
	}
	t.Setenv("DATA_DIR", dataDir)
	return path
}

// fakeBackupFileCLI 假 backup_file：校验 stdin JSON 的 path/dest，
// 齐全则输出成功信封，缺失则输出参数错误信封（ok=false、退出码 1）。
const fakeBackupFileCLI = `#!/bin/sh
args=$(cat)
case "$args" in
  *'"dest"'*)
    echo '{"ok":true,"tool":"backup_file","output":{"file_path":"/tmp/out.tar.gz","size":12,"checksum":"aa"}}'
    exit 0
    ;;
  *)
    echo '{"ok":false,"tool":"backup_file","error":"params error: args.dest required"}'
    exit 1
    ;;
esac
`

func TestRunToolListWithFakeCLI(t *testing.T) {
	writeFakeCLI(t, "backup_file", "#!/bin/sh\n[ \"$1\" = \"info\" ] && echo '{\"name\":\"backup_file\",\"description\":\"文件备份\",\"params_schema\":{}}'\nexit 0\n")

	var out strings.Builder
	code := run([]string{"tool", "list"}, strings.NewReader(""), &out)
	if code != 0 {
		t.Fatalf("code = %d, output = %s", code, out.String())
	}
	var m map[string]any
	if err := json.Unmarshal([]byte(out.String()), &m); err != nil {
		t.Fatalf("unmarshal: %v (%s)", err, out.String())
	}
	tools := m["tools"].([]any)
	if len(tools) != 1 {
		t.Fatalf("tools = %v", tools)
	}
	entry := tools[0].(map[string]any)
	if entry["name"] != "backup_file" || entry["description"] != "文件备份" {
		t.Fatalf("entry = %v", entry)
	}
}

func TestRunToolListEmpty(t *testing.T) {
	// 无任何 CLI 时输出 {"tools":[]} 且退出码 0。
	t.Setenv("DATA_DIR", t.TempDir())
	t.Setenv("RESTTO_CLIS_DIR", t.TempDir())

	var out strings.Builder
	code := run([]string{"tool", "list"}, strings.NewReader(""), &out)
	if code != 0 {
		t.Fatalf("code = %d, output = %s", code, out.String())
	}
	var m map[string]any
	if err := json.Unmarshal([]byte(out.String()), &m); err != nil {
		t.Fatalf("unmarshal: %v (%s)", err, out.String())
	}
	if len(m["tools"].([]any)) != 0 {
		t.Fatalf("tools = %v", m["tools"])
	}
}

func TestRunToolSchemaUnknown(t *testing.T) {
	t.Setenv("DATA_DIR", t.TempDir())
	var out strings.Builder
	if code := run([]string{"tool", "schema", "nope"}, strings.NewReader(""), &out); code != 2 {
		t.Fatalf("code = %d, out = %s", code, out.String())
	}
	if !strings.Contains(out.String(), "unknown tool: nope") {
		t.Fatalf("out = %s", out.String())
	}
}

func TestRunToolSchemaKnown(t *testing.T) {
	writeFakeCLI(t, "backup_file", "#!/bin/sh\n[ \"$1\" = \"info\" ] && echo '{\"name\":\"backup_file\",\"description\":\"d\",\"params_schema\":{\"type\":\"object\"}}'\nexit 0\n")

	var out strings.Builder
	if code := run([]string{"tool", "schema", "backup_file"}, strings.NewReader(""), &out); code != 0 {
		t.Fatalf("code = %d, output = %s", code, out.String())
	}
	var m map[string]any
	if err := json.Unmarshal([]byte(out.String()), &m); err != nil {
		t.Fatalf("unmarshal: %v", err)
	}
	if m["name"] != "backup_file" || m["params_schema"] == nil {
		t.Fatalf("schema = %s", out.String())
	}
}

func TestRunToolRunSuccessEnvelope(t *testing.T) {
	writeFakeCLI(t, "backup_file", fakeBackupFileCLI)

	var out strings.Builder
	code := run([]string{"tool", "run", "backup_file", "--args", `{"path":"/data","dest":"/tmp/o.tar.gz"}`},
		strings.NewReader(""), &out)
	if code != 0 {
		t.Fatalf("code = %d, output = %s", code, out.String())
	}
	var env map[string]any
	if err := json.Unmarshal([]byte(out.String()), &env); err != nil {
		t.Fatalf("unmarshal: %v (%s)", err, out.String())
	}
	if env["ok"] != true || env["tool"] != "backup_file" {
		t.Fatalf("envelope = %s", out.String())
	}
}

func TestRunToolRunFailureEnvelopeExit1(t *testing.T) {
	writeFakeCLI(t, "backup_file", fakeBackupFileCLI)

	var out strings.Builder
	code := run([]string{"tool", "run", "backup_file", "--args", `{"path":"/whatever"}`},
		strings.NewReader(""), &out)
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
	if !strings.Contains(env["error"].(string), "params error") {
		t.Fatalf("error = %v", env["error"])
	}
}

func TestRunToolRunUnknownTool(t *testing.T) {
	t.Setenv("DATA_DIR", t.TempDir())
	var out strings.Builder
	code := run([]string{"tool", "run", "nope", "--args", "{}"}, strings.NewReader(""), &out)
	if code != 2 {
		t.Fatalf("code = %d, want 2", code)
	}
	if !strings.Contains(out.String(), "unknown tool: nope") {
		t.Fatalf("out = %s", out.String())
	}
}

func TestRunToolRunArgsFromStdin(t *testing.T) {
	writeFakeCLI(t, "backup_file", fakeBackupFileCLI)

	var out strings.Builder
	code := run([]string{"tool", "run", "backup_file", "--args", "-"},
		strings.NewReader(`{"path":"/data","dest":"/tmp/o.tar.gz"}`), &out)
	if code != 0 {
		t.Fatalf("code = %d, output = %s", code, out.String())
	}
	var env map[string]any
	if err := json.Unmarshal([]byte(out.String()), &env); err != nil || env["ok"] != true {
		t.Fatalf("envelope = %s (%v)", out.String(), err)
	}
}

func TestRunToolRunUnderscoreAlias(t *testing.T) {
	// 装名为 backupfile（无下划线）的 CLI，按 backup_file 调度亦可命中
	// （runner 候选名去下划线规则）。
	writeFakeCLI(t, "backupfile", fakeBackupFileCLI)

	var out strings.Builder
	code := run([]string{"tool", "run", "backup_file", "--args", `{"path":"/d","dest":"/o"}`},
		strings.NewReader(""), &out)
	if code != 0 {
		t.Fatalf("code = %d, output = %s", code, out.String())
	}
}

func TestRunToolRunFlagErrors(t *testing.T) {
	t.Setenv("DATA_DIR", t.TempDir())
	var out strings.Builder
	if code := run([]string{"tool", "run"}, strings.NewReader(""), &out); code != 2 {
		t.Fatalf("code = %d", code)
	}
	out.Reset()
	if code := run([]string{"tool", "run", "x", "--bogus"}, strings.NewReader(""), &out); code != 2 {
		t.Fatalf("code = %d", code)
	}
	out.Reset()
	if code := run([]string{"tool", "run", "x", "--args"}, strings.NewReader(""), &out); code != 2 {
		t.Fatalf("code = %d", code)
	}
}

func TestRunToolActionUnknown(t *testing.T) {
	var out strings.Builder
	if code := run([]string{"tool", "frobnicate"}, strings.NewReader(""), &out); code != 2 {
		t.Fatalf("code = %d", code)
	}
}

func TestLegacyCommandsRemoved(t *testing.T) {
	// 旧命令已按用户决策移除：应报 unknown command 且退出码 2。
	for _, cmd := range []string{"backup-file", "backup-mysql", "run-module"} {
		var out strings.Builder
		if code := run([]string{cmd}, strings.NewReader(""), &out); code != 2 {
			t.Fatalf("%s code = %d, want 2", cmd, code)
		}
		if !strings.Contains(out.String(), "unknown command: "+cmd) {
			t.Fatalf("%s out = %s", cmd, out.String())
		}
	}
}

func TestRunUnknownCommand(t *testing.T) {
	var out strings.Builder
	if code := run([]string{"nope"}, strings.NewReader(""), &out); code != 2 {
		t.Fatalf("code = %d", code)
	}
}

func TestRunEmptyArgs(t *testing.T) {
	var out strings.Builder
	if code := run(nil, strings.NewReader(""), &out); code != 2 {
		t.Fatalf("code = %d", code)
	}
}

func TestRunHelpAndVersion(t *testing.T) {
	var out strings.Builder
	if code := run([]string{"--help"}, strings.NewReader(""), &out); code != 0 || !strings.Contains(out.String(), "用法") {
		t.Fatalf("help code=%d out=%s", code, out.String())
	}
	out.Reset()
	if code := run([]string{"--version"}, strings.NewReader(""), &out); code != 0 {
		t.Fatalf("version code=%d", code)
	}
}
