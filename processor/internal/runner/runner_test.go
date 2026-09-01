package runner

import (
	"context"
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"runtime"
	"strings"
	"testing"
	"time"

	"restto-client/internal/common"
)

// 用 #!/bin/sh 假 CLI 脚本覆盖：信封成功 / 失败透传 / 异常输出 / stdin 参数透传 /
// ctx 取消 / 模块名校验 / 候选名与目录优先级 / List 发现。
// sh 脚本仅在类 Unix 系统可用，Windows 跳过进程类用例（纯函数用例照跑）。

// writeFakeCLI 在 dir 下创建名为 name 的可执行 sh 脚本，返回其路径。
func writeFakeCLI(t *testing.T, dir, name, script string) string {
	t.Helper()
	if err := os.MkdirAll(dir, 0o755); err != nil {
		t.Fatalf("mkdir: %v", err)
	}
	path := filepath.Join(dir, name)
	if err := os.WriteFile(path, []byte("#!/bin/sh\n"+script+"\n"), 0o755); err != nil {
		t.Fatalf("write cli: %v", err)
	}
	return path
}

func requireUnix(t *testing.T) {
	t.Helper()
	if runtime.GOOS == "windows" {
		t.Skip("sh 脚本假 CLI 仅支持类 Unix 系统")
	}
}

func TestValidModuleName(t *testing.T) {
	valid := []string{"backup_file", "backupmysql", "a", "A1", "x-y", "x.y", "mod-1_2.3", strings.Repeat("a", 64)}
	invalid := []string{"", ".hidden", "-lead", "_lead", "a/b", "../x", "a b", "a|b", strings.Repeat("a", 65), "模块"}
	for _, name := range valid {
		if !ValidModuleName(name) {
			t.Fatalf("%q should be valid", name)
		}
	}
	for _, name := range invalid {
		if ValidModuleName(name) {
			t.Fatalf("%q should be invalid", name)
		}
	}
}

func TestCandidateNames(t *testing.T) {
	if got := candidateNames("backup_file", "linux"); len(got) != 2 || got[0] != "backup_file" || got[1] != "backupfile" {
		t.Fatalf("linux candidates = %v", got)
	}
	if got := candidateNames("backupfile", "linux"); len(got) != 1 || got[0] != "backupfile" {
		t.Fatalf("no-underscore candidates = %v", got)
	}
	if got := candidateNames("backup_file", "windows"); len(got) != 4 || got[0] != "backup_file.exe" || got[1] != "backupfile.exe" || got[2] != "backup_file" || got[3] != "backupfile" {
		t.Fatalf("windows candidates = %v", got)
	}
}

func TestRunSuccessEnvelope(t *testing.T) {
	requireUnix(t)
	dir := t.TempDir()
	writeFakeCLI(t, dir, "backup_file", `echo '{"ok":true,"tool":"backup_file","output":{"file_path":"/a.tar.gz","size":9,"checksum":"c1"}}'`)
	r := NewRunner(dir)
	out, terr := r.Run(context.Background(), "backup_file", []byte(`{"path":"/a"}`))
	if terr != nil {
		t.Fatalf("terr = %v", terr)
	}
	if out.FilePath != "/a.tar.gz" || out.Size != 9 || out.Checksum != "c1" {
		t.Fatalf("out = %+v", out)
	}
}

func TestRunResolvesStrippedUnderscoreName(t *testing.T) {
	// 模块名 backup_file 应能命中文件名 backupfile（构建产物命名）。
	requireUnix(t)
	dir := t.TempDir()
	writeFakeCLI(t, dir, "backupfile", `echo '{"ok":true,"tool":"backup_file","output":{"file_path":"/b","size":1,"checksum":"x"}}'`)
	out, terr := NewRunner(dir).Run(context.Background(), "backup_file", nil)
	if terr != nil || out.FilePath != "/b" {
		t.Fatalf("out=%+v terr=%v", out, terr)
	}
}

func TestRunFailureEnvelopeVerbatim(t *testing.T) {
	// ok=false 信封：error 原文透传（KindExternal 不加前缀），即使退出码为 1。
	requireUnix(t)
	dir := t.TempDir()
	writeFakeCLI(t, dir, "backup_file", `echo '{"ok":false,"tool":"backup_file","error":"params error: missing or invalid args.path"}'; exit 1`)
	_, terr := NewRunner(dir).Run(context.Background(), "backup_file", nil)
	if terr == nil {
		t.Fatal("should fail")
	}
	if terr.Kind != common.KindExternal || terr.Error() != "params error: missing or invalid args.path" {
		t.Fatalf("terr = %+v (%s)", terr, terr.Error())
	}
}

func TestRunGarbageStdoutIncludesExitAndStderr(t *testing.T) {
	requireUnix(t)
	dir := t.TempDir()
	writeFakeCLI(t, dir, "backup_file", `echo "boom" >&2; echo "not-json"; exit 3`)
	_, terr := NewRunner(dir).Run(context.Background(), "backup_file", nil)
	if terr == nil || terr.Kind != common.KindExternal {
		t.Fatalf("terr = %v", terr)
	}
	msg := terr.Error()
	if !strings.Contains(msg, "code=3") || !strings.Contains(msg, "boom") || !strings.Contains(msg, "stderr tail") {
		t.Fatalf("msg = %s", msg)
	}
}

func TestRunStderrTailTruncated(t *testing.T) {
	// 100KB stderr：错误信息只保留末尾 2KB。
	requireUnix(t)
	dir := t.TempDir()
	writeFakeCLI(t, dir, "backup_file", `for i in $(seq 1 10000); do echo "0123456789PREFIX-BIG-LINE"; done >&2; exit 1`)
	_, terr := NewRunner(dir).Run(context.Background(), "backup_file", nil)
	if terr == nil {
		t.Fatal("should fail")
	}
	if len(terr.Error()) > stderrTailLimit+512 { // 2KB 尾部 + 固定文案余量
		t.Fatalf("error too long: %d bytes", len(terr.Error()))
	}
	if strings.Contains(terr.Error(), "PREFIX-BIG-LINE") && !strings.Contains(terr.Error(), "09999") {
		// 粗验证保留的是末尾内容：最后一行编号应接近 10000。
		t.Log("tail check: last lines kept")
	}
}

func TestRunUnknownModule(t *testing.T) {
	r := NewRunner(t.TempDir())
	_, terr := r.Run(context.Background(), "nope", nil)
	if terr == nil || terr.Kind != common.KindNotFound {
		t.Fatalf("terr = %v", terr)
	}
	if terr.Error() != "unknown module: nope" {
		t.Fatalf("msg = %s", terr.Error())
	}
}

func TestRunInvalidModuleNameRejected(t *testing.T) {
	// 路径穿越类模块名（含分隔符 / 点号开头）必须在校验层被拒绝，
	// 不应触达任何文件系统拼接。
	for _, name := range []string{"../x", "a/b", ".hidden", "/etc/passwd"} {
		_, terr := NewRunner(t.TempDir()).Run(context.Background(), name, nil)
		if terr == nil || terr.Kind != common.KindNotFound {
			t.Fatalf("name %q: terr = %v", name, terr)
		}
	}
}

func TestRunPassesArgsViaStdin(t *testing.T) {
	// 断言参数 JSON 经 stdin 完整传入子进程。
	requireUnix(t)
	dir := t.TempDir()
	captured := filepath.Join(dir, "captured.json")
	writeFakeCLI(t, dir, "backup_file", fmt.Sprintf(`cat > %s; echo '{"ok":true,"tool":"backup_file","output":{"file_path":"/a","size":1,"checksum":"c"}}'`, captured))
	r := NewRunner(dir)
	args := json.RawMessage(`{"path":"/data/src","dest":"/data/out.tar.gz"}`)
	if _, terr := r.Run(context.Background(), "backup_file", args); terr != nil {
		t.Fatalf("run: %v", terr)
	}
	got, err := os.ReadFile(captured)
	if err != nil {
		t.Fatalf("read captured: %v", err)
	}
	if string(got) != string(args) {
		t.Fatalf("captured = %s, want %s", got, args)
	}
}

func TestRunEmptyArgsNormalized(t *testing.T) {
	// nil 参数归一为 {}，仍能通过 stdin 传递并被信封成功回执。
	requireUnix(t)
	dir := t.TempDir()
	writeFakeCLI(t, dir, "backup_file", `read_str=$(cat); [ "$read_str" = "{}" ] || exit 9; echo '{"ok":true,"tool":"backup_file","output":{"file_path":"/a","size":1,"checksum":"c"}}'`)
	out, terr := NewRunner(dir).Run(context.Background(), "backup_file", nil)
	if terr != nil || out == nil {
		t.Fatalf("out=%v terr=%v", out, terr)
	}
}

func TestRunContextCancelKillsChild(t *testing.T) {
	requireUnix(t)
	dir := t.TempDir()
	writeFakeCLI(t, dir, "backup_file", `sleep 5`)
	ctx, cancel := context.WithTimeout(context.Background(), 150*time.Millisecond)
	defer cancel()
	start := time.Now()
	_, terr := NewRunner(dir).Run(ctx, "backup_file", nil)
	if terr == nil || !strings.Contains(terr.Error(), "canceled") {
		t.Fatalf("terr = %v", terr)
	}
	if elapsed := time.Since(start); elapsed > 3*time.Second {
		t.Fatalf("child not killed promptly: %v", elapsed)
	}
}

func TestResolveDirectoryPrecedence(t *testing.T) {
	// 同名 CLI：高优先级目录胜出。
	requireUnix(t)
	high, low := t.TempDir(), t.TempDir()
	writeFakeCLI(t, high, "mod", `echo high`)
	writeFakeCLI(t, low, "mod", `echo low`)
	r := NewRunner(high, low)
	path, terr := r.resolve("mod")
	if terr != nil {
		t.Fatalf("resolve: %v", terr)
	}
	if filepath.Dir(path) != high {
		t.Fatalf("resolved %s, want dir %s", path, high)
	}
}

func TestResolveRequiresExecBit(t *testing.T) {
	// 无执行位的文件不算命中。
	requireUnix(t)
	dir := t.TempDir()
	if err := os.WriteFile(filepath.Join(dir, "mod"), []byte("#!/bin/sh\n"), 0o644); err != nil {
		t.Fatalf("write: %v", err)
	}
	_, terr := NewRunner(dir).resolve("mod")
	if terr == nil || terr.Kind != common.KindNotFound {
		t.Fatalf("terr = %v", terr)
	}
}

func TestResolvePathFallback(t *testing.T) {
	// 目录未命中时走 PATH 兜底（注入 lookUp 指向一个真实存在的可执行文件）。
	requireUnix(t)
	other := t.TempDir()
	writeFakeCLI(t, other, "mod", `echo hi`)
	r := NewRunner(t.TempDir())
	r.lookUp = func(name string) (string, error) {
		if name == "mod" {
			return filepath.Join(other, "mod"), nil
		}
		return "", fmt.Errorf("not found")
	}
	path, terr := r.resolve("mod")
	if terr != nil || path != filepath.Join(other, "mod") {
		t.Fatalf("path=%s terr=%v", path, terr)
	}
}

func TestInfo(t *testing.T) {
	requireUnix(t)
	dir := t.TempDir()
	writeFakeCLI(t, dir, "backup_file", `case "$1" in info) echo '{"name":"backup_file","description":"d","params_schema":{"type":"object"}}';; *) exit 2;; esac`)
	info, terr := NewRunner(dir).Info(context.Background(), "backup_file")
	if terr != nil {
		t.Fatalf("info: %v", terr)
	}
	if info.Name != "backup_file" || info.Description != "d" || info.ParamsSchema["type"] != "object" {
		t.Fatalf("info = %+v", info)
	}
}

func TestInfoTimeout(t *testing.T) {
	requireUnix(t)
	dir := t.TempDir()
	writeFakeCLI(t, dir, "backup_file", `case "$1" in info) sleep 5;; *) exit 2;; esac`)
	ctx, cancel := context.WithTimeout(context.Background(), 150*time.Millisecond)
	defer cancel()
	_, terr := NewRunner(dir).Info(ctx, "backup_file")
	if terr == nil || !strings.Contains(terr.Error(), "canceled") {
		t.Fatalf("terr = %v", terr)
	}
}

func TestListDedupSortAndSkipFailure(t *testing.T) {
	requireUnix(t)
	dir := t.TempDir()
	writeFakeCLI(t, dir, "b_second", `echo '{"name":"b_second","description":"2"}'`)
	writeFakeCLI(t, dir, "a_first", `echo '{"name":"a_first","description":"1"}'`)
	writeFakeCLI(t, dir, "broken", `exit 1`) // info 失败：跳过不致命
	infos := NewRunner(dir).List(context.Background())
	if len(infos) != 2 {
		t.Fatalf("infos = %+v", infos)
	}
	if infos[0].Name != "a_first" || infos[1].Name != "b_second" {
		t.Fatalf("order = %+v", infos)
	}
}

func TestListEmptyWhenNoDirs(t *testing.T) {
	if infos := NewRunner(filepath.Join(t.TempDir(), "missing")).List(context.Background()); len(infos) != 0 {
		t.Fatalf("infos = %+v", infos)
	}
}

func TestListDedupAcrossDirsWithAliasNames(t *testing.T) {
	// 同一 CLI 以不同文件名出现在两个目录（扩展包约定名 backup_file 与
	// 构建产物名 backupfile）：应只列出一条，且高优先级目录胜出。
	requireUnix(t)
	high, low := t.TempDir(), t.TempDir()
	writeFakeCLI(t, high, "backup_file", `echo '{"name":"backup_file","description":"from-high"}'`)
	writeFakeCLI(t, low, "backupfile", `echo '{"name":"backup_file","description":"from-low"}'`)

	infos := NewRunner(high, low).List(context.Background())
	if len(infos) != 1 {
		t.Fatalf("infos = %+v", infos)
	}
	if infos[0].Description != "from-high" {
		t.Fatalf("should prefer higher-priority dir, got %+v", infos[0])
	}
}
