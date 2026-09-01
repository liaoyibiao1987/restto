package main

import (
	"encoding/json"
	"os"
	"path/filepath"
	"runtime"
	"strings"
	"testing"
)

// 覆盖 CLI 外壳：子命令分发、信封输出、stdout JSON 纯净性、退出码。
// 核心导出逻辑的测试在 backupmysql_test.go（随包迁移）。

func TestRunBackupMysqlParamsErrorFails(t *testing.T) {
	// 缺少必填参数：信封 ok=false、退出码 1（不依赖真实 mysqldump）。
	var out strings.Builder
	code := run([]string{"run", "--args", `{"host":"127.0.0.1"}`}, strings.NewReader(""), &out)
	if code != 1 {
		t.Fatalf("code = %d, output = %s", code, out.String())
	}
	var env map[string]any
	if err := json.Unmarshal([]byte(out.String()), &env); err != nil {
		t.Fatalf("stdout not pure json: %v (%s)", err, out.String())
	}
	if env["ok"] != false || env["tool"] != Name {
		t.Fatalf("envelope = %s", out.String())
	}
	if !strings.Contains(env["error"].(string), "host/user/database required") {
		t.Fatalf("error = %v", env["error"])
	}
}

func TestRunBackupMysqlBadArgsUsage(t *testing.T) {
	var out strings.Builder
	if code := run([]string{"run", "--args", "{bad"}, strings.NewReader(""), &out); code != 2 {
		t.Fatalf("code = %d", code)
	}
	out.Reset()
	if code := run([]string{"run", "--unexpected"}, strings.NewReader(""), &out); code != 2 {
		t.Fatalf("code = %d", code)
	}
}

func TestRunBackupMysqlArgsFromStdin(t *testing.T) {
	// 参数经 stdin 传入；缺 dest 时报参数错误（不触发 mysqldump）。
	argsJSON := `{"host":"127.0.0.1","user":"root","database":"db"}`
	var out strings.Builder
	code := run([]string{"run", "--args", "-"}, strings.NewReader(argsJSON), &out)
	if code != 1 {
		t.Fatalf("code = %d, output = %s", code, out.String())
	}
	var env map[string]any
	if err := json.Unmarshal([]byte(out.String()), &env); err != nil || env["ok"] != false {
		t.Fatalf("envelope = %s (%v)", out.String(), err)
	}
	if !strings.Contains(env["error"].(string), "args.dest") {
		t.Fatalf("error = %v", env["error"])
	}
}

func TestRunBackupMysqlSubprocessFailure(t *testing.T) {
	// PATH 中无 mysqldump：子进程启动失败 → 信封 ok=false、退出码 1、错误可读。
	t.Setenv("PATH", t.TempDir()) // 清空 PATH，确保找不到 mysqldump
	dir := t.TempDir()
	args := `{"host":"127.0.0.1","user":"u","database":"d","dest":"` + filepath.Join(dir, "o.sql.gz") + `"}`
	var out strings.Builder
	code := run([]string{"run", "--args", args}, strings.NewReader(""), &out)
	if code != 1 {
		t.Fatalf("code = %d, output = %s", code, out.String())
	}
	var env map[string]any
	if err := json.Unmarshal([]byte(out.String()), &env); err != nil {
		t.Fatalf("stdout not pure json: %v (%s)", err, out.String())
	}
	if env["ok"] != false {
		t.Fatalf("envelope = %s", out.String())
	}
}

// writeFakeMysqldump 在 dir 下创建假 mysqldump（输出固定 SQL 后成功退出），
// 并把 PATH 指向该目录 —— 覆盖「导出 → gzip 落盘 → 信封成功」完整链路。
func writeFakeMysqldump(t *testing.T, dir string) {
	t.Helper()
	if runtime.GOOS == "windows" {
		t.Skip("sh 假 mysqldump 仅支持类 Unix 系统")
	}
	if err := os.MkdirAll(dir, 0o755); err != nil {
		t.Fatalf("mkdir: %v", err)
	}
	script := "#!/bin/sh\nfor a in \"$@\"; do case \"$a\" in --database) ;; esac; done\necho 'CREATE TABLE t(id INT);'\necho '-- dump done' >&2\nexit 0\n"
	if err := os.WriteFile(filepath.Join(dir, "mysqldump"), []byte(script), 0o755); err != nil {
		t.Fatalf("write mysqldump: %v", err)
	}
	t.Setenv("PATH", dir)
}

func TestRunBackupMysqlSuccessWithFakeDump(t *testing.T) {
	bin := t.TempDir()
	writeFakeMysqldump(t, bin)
	dest := filepath.Join(t.TempDir(), "nested", "o.sql.gz")
	argsJSON := `{"host":"127.0.0.1","user":"root","password":"p","database":"db","dest":"` + dest + `"}`

	var out strings.Builder
	code := run([]string{"run", "--args", argsJSON}, strings.NewReader(""), &out)
	if code != 0 {
		t.Fatalf("code = %d, output = %s", code, out.String())
	}
	var env map[string]any
	if err := json.Unmarshal([]byte(out.String()), &env); err != nil {
		t.Fatalf("stdout not pure json: %v (%s)", err, out.String())
	}
	if env["ok"] != true || env["tool"] != Name {
		t.Fatalf("envelope = %s", out.String())
	}
	if _, err := os.Stat(dest); err != nil {
		t.Fatalf("artifact missing: %v", err)
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
	if info["name"] != Name || info["params_schema"] == nil {
		t.Fatalf("info = %s", out.String())
	}
}

func TestVersionAndHelpAndUnknown(t *testing.T) {
	var out strings.Builder
	if code := run([]string{"version"}, strings.NewReader(""), &out); code != 0 || !strings.Contains(out.String(), Name) {
		t.Fatalf("version code=%d out=%s", code, out.String())
	}
	out.Reset()
	if code := run([]string{"--help"}, strings.NewReader(""), &out); code != 0 || !strings.Contains(out.String(), "用法") {
		t.Fatalf("help code=%d out=%s", code, out.String())
	}
	out.Reset()
	if code := run([]string{"nope"}, strings.NewReader(""), &out); code != 2 {
		t.Fatalf("unknown code=%d", code)
	}
	out.Reset()
	if code := run(nil, strings.NewReader(""), &out); code != 2 {
		t.Fatalf("empty code=%d", code)
	}
}
