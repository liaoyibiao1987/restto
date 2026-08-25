package common

import (
	"context"
	"encoding/json"
	"testing"
)

// 覆盖 RequireStr 解析与 Tool 的接口派发。

// echoTool 测试用的最小工具实现，用于验证接口调度。
type echoTool struct{}

func (echoTool) Info() ToolInfo {
	return ToolInfo{
		Name:         "echo",
		Description:  "测试用",
		ParamsSchema: map[string]any{"type": "object"},
	}
}

func (echoTool) Run(_ context.Context, args json.RawMessage) (*ToolOutput, *ToolError) {
	msg, err := RequireStr(args, "msg")
	if err != nil {
		return nil, err
	}
	return &ToolOutput{FilePath: msg, Size: 0, Checksum: "0"}, nil
}

func TestRequireStrPresent(t *testing.T) {
	v := json.RawMessage(`{"path":"/data"}`)
	got, err := RequireStr(v, "path")
	if err != nil {
		t.Fatalf("RequireStr: %v", err)
	}
	if got != "/data" {
		t.Fatalf("got %q, want /data", got)
	}
}

func TestRequireStrMissingReturnsParamsError(t *testing.T) {
	_, err := RequireStr(json.RawMessage(`{}`), "path")
	if err == nil || err.Kind != KindParams {
		t.Fatalf("err = %v, want KindParams", err)
	}
}

func TestRequireStrWrongTypeReturnsParamsError(t *testing.T) {
	_, err := RequireStr(json.RawMessage(`{"path":123}`), "path")
	if err == nil || err.Kind != KindParams {
		t.Fatalf("err = %v, want KindParams", err)
	}
}

func TestRequireStrEmptyStringReturnsParamsError(t *testing.T) {
	_, err := RequireStr(json.RawMessage(`{"path":""}`), "path")
	if err == nil || err.Kind != KindParams {
		t.Fatalf("err = %v, want KindParams", err)
	}
}

func TestRequireStrInvalidJSONReturnsParamsError(t *testing.T) {
	_, err := RequireStr(json.RawMessage(`{bad`), "path")
	if err == nil || err.Kind != KindParams {
		t.Fatalf("err = %v, want KindParams", err)
	}
}

func TestToolInterfaceDispatchRuns(t *testing.T) {
	var tool Tool = echoTool{}
	if tool.Info().Name != "echo" {
		t.Fatalf("name = %q", tool.Info().Name)
	}
	out, err := tool.Run(context.Background(), json.RawMessage(`{"msg":"/x"}`))
	if err != nil {
		t.Fatalf("run: %v", err)
	}
	if out.FilePath != "/x" {
		t.Fatalf("FilePath = %q, want /x", out.FilePath)
	}
}

func TestToolInterfaceDispatchPropagatesError(t *testing.T) {
	var tool Tool = echoTool{}
	_, err := tool.Run(context.Background(), json.RawMessage(`{}`))
	if err == nil || err.Kind != KindParams {
		t.Fatalf("err = %v, want KindParams", err)
	}
}

func TestToolErrorMessages(t *testing.T) {
	sub := NewSubprocessError(2, "boom")
	if got := sub.Error(); got != "subprocess failed (code=2): boom" {
		t.Fatalf("subprocess message = %q", got)
	}
	io := NewIOError("disk full")
	if got := io.Error(); got != "io error: disk full" {
		t.Fatalf("io message = %q", got)
	}
}
