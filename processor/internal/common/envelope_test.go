package common

import (
	"encoding/json"
	"strings"
	"testing"
)

// 覆盖信封契约：BuildEnvelope 金样（字节级兼容锁）、ParseEnvelope 往返与异常、
// 新错误类别的原文透传。

func TestBuildEnvelopeGoldenSuccess(t *testing.T) {
	// 金样：与旧版 restto 主程序 buildEnvelope + MarshalIndent 的输出逐字节一致。
	out := &ToolOutput{FilePath: "/tmp/out.tar.gz", Size: 144, Checksum: "abc123"}
	data, err := json.MarshalIndent(BuildEnvelope("backup_file", out, nil), "", "  ")
	if err != nil {
		t.Fatalf("marshal: %v", err)
	}
	want := `{
  "ok": true,
  "output": {
    "file_path": "/tmp/out.tar.gz",
    "size": 144,
    "checksum": "abc123"
  },
  "tool": "backup_file"
}`
	if string(data) != want {
		t.Fatalf("golden mismatch:\ngot:  %s\nwant: %s", data, want)
	}
}

func TestBuildEnvelopeGoldenFailure(t *testing.T) {
	data, err := json.MarshalIndent(BuildEnvelope("backup_mysql", nil, NewParamsError("missing or invalid args.host")), "", "  ")
	if err != nil {
		t.Fatalf("marshal: %v", err)
	}
	want := `{
  "error": "params error: missing or invalid args.host",
  "ok": false,
  "tool": "backup_mysql"
}`
	if string(data) != want {
		t.Fatalf("golden mismatch:\ngot:  %s\nwant: %s", data, want)
	}
}

func TestBuildEnvelopeNilOutputAllowed(t *testing.T) {
	// 成功但 output 为 nil 指针：序列化为 null，调用方负责不产生该状态（防御）。
	env := BuildEnvelope("x", (*ToolOutput)(nil), nil)
	if env["ok"] != true {
		t.Fatal("ok should be true")
	}
}

func TestParseEnvelopeRoundTrip(t *testing.T) {
	// 成功信封往返。
	okJSON := `{"ok":true,"tool":"backup_file","output":{"file_path":"/a","size":7,"checksum":"c"}}`
	env, err := ParseEnvelope([]byte(okJSON))
	if err != nil {
		t.Fatalf("parse: %v", err)
	}
	if !env.Ok || env.Tool != "backup_file" || env.Output.FilePath != "/a" || env.Output.Size != 7 || env.Output.Checksum != "c" {
		t.Fatalf("env = %+v", env)
	}

	// 失败信封往返。
	msg := "params error: missing or invalid args.path"
	failJSON := `{"ok":false,"tool":"backup_file","error":"` + msg + `"}`
	env, err = ParseEnvelope([]byte(failJSON))
	if err != nil {
		t.Fatalf("parse: %v", err)
	}
	if env.Ok || *env.Error != msg {
		t.Fatalf("env = %+v", env)
	}
}

func TestParseEnvelopeRejectsBadInput(t *testing.T) {
	cases := []string{
		``,                        // 空输入
		`not json`,                // 非 JSON
		`[1,2,3]`,                 // 非对象
		`{"tool":"x"}`,            // 缺 ok
		`{"ok":"yes","tool":"x"}`, // ok 类型错误
		`{"ok":true,"tool":"x"}`,  // ok=true 缺 output
		`{"ok":false,"tool":"x"}`, // ok=false 缺 error
		`{"ok":true,"tool":"x","output":{"file_path":"a","size":1,"checksum":"c"},"extra":"ignored"}`, // 多余字段容忍
	}
	for i, in := range cases {
		env, err := ParseEnvelope([]byte(in))
		last := i == len(cases)-1
		if last {
			// 最后一个用例：多余字段应被容忍。
			if err != nil || env == nil || !env.Ok {
				t.Fatalf("case %d should parse: %v", i, err)
			}
			continue
		}
		if err == nil {
			t.Fatalf("case %d (%s) should error, got %+v", i, in, env)
		}
	}
}

func TestExternalErrorPassthrough(t *testing.T) {
	// KindExternal / KindNotFound 的 Error() 原文透传，不加前缀。
	msg := "params error: missing or invalid args.path"
	if got := NewExternalError(msg).Error(); got != msg {
		t.Fatalf("external = %q, want %q", got, msg)
	}
	nf := "unknown module: backup_file"
	if got := NewNotFoundError(nf).Error(); got != nf {
		t.Fatalf("not_found = %q, want %q", got, nf)
	}
	// 其余类别保持原格式，确认未破坏既有行为。
	if got := NewIOError("boom").Error(); got != "io error: boom" {
		t.Fatalf("io = %q", got)
	}
	if !strings.Contains(NewSubprocessError(3, "bad").Error(), "code=3") {
		t.Fatal("subprocess format broken")
	}
}
