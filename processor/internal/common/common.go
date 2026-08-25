// Package common 定义工具（Tool）公共框架。
//
// 包含统一的 Tool 接口、产物 / 错误模型与 JSON 参数解析辅助，供所有工具实现复用。
// 对应 AGENT.MD 中"功能切割、按模块名调用"的 AI-CLI 架构的抽象基座：新增工具只需
// 在自己的包内实现 Tool，再到 tools 包的 Registry() 注册一行即可。
package common

import (
	"context"
	"encoding/json"
	"fmt"
)

// ToolOutput 工具执行产物。
//
// 字段与 daemon 回传的 TaskResult 一一对应，保持稳定（服务端依赖此结构）。
type ToolOutput struct {
	// FilePath 产物绝对路径。
	FilePath string `json:"file_path"`
	// Size 产物字节数。
	Size uint64 `json:"size"`
	// Checksum 产物 sha256。
	Checksum string `json:"checksum"`
}

// ToolErrorKind 工具错误类别，便于按类别区分处理。
type ToolErrorKind string

// 各错误类别取值。
const (
	// KindIO 文件 / 目录 IO 错误。
	KindIO ToolErrorKind = "io"
	// KindArchive tar / gzip 等归档错误。
	KindArchive ToolErrorKind = "archive"
	// KindSubprocess 子进程执行失败（如 mysqldump）。
	KindSubprocess ToolErrorKind = "subprocess"
	// KindParams 参数缺失或非法。
	KindParams ToolErrorKind = "params"
	// KindOther 其它工具内部错误。
	KindOther ToolErrorKind = "other"
)

// ToolError 统一的工具错误。
type ToolError struct {
	// Kind 错误类别。
	Kind ToolErrorKind
	// Message 错误详情（子进程错误含 stderr）。
	Message string
	// Code 子进程退出码（仅 KindSubprocess 有效）。
	Code int
}

// Error 实现 error 接口。
func (e *ToolError) Error() string {
	switch e.Kind {
	case KindSubprocess:
		return fmt.Sprintf("subprocess failed (code=%d): %s", e.Code, e.Message)
	default:
		return fmt.Sprintf("%s error: %s", e.Kind, e.Message)
	}
}

// NewIOError 构造 IO 类错误。
func NewIOError(message string) *ToolError {
	return &ToolError{Kind: KindIO, Message: message}
}

// NewArchiveError 构造归档类错误。
func NewArchiveError(message string) *ToolError {
	return &ToolError{Kind: KindArchive, Message: message}
}

// NewSubprocessError 构造子进程类错误。
func NewSubprocessError(code int, stderr string) *ToolError {
	return &ToolError{Kind: KindSubprocess, Code: code, Message: stderr}
}

// NewParamsError 构造参数类错误。
func NewParamsError(message string) *ToolError {
	return &ToolError{Kind: KindParams, Message: message}
}

// NewOtherError 构造其它类错误。
func NewOtherError(message string) *ToolError {
	return &ToolError{Kind: KindOther, Message: message}
}

// ToolInfo 工具静态元信息，供 agent 发现 / 自省（tool list、tool schema）。
type ToolInfo struct {
	// Name 工具名（服务端按此名调度，必须全局唯一且稳定）。
	Name string `json:"name"`
	// Description 一句话功能描述。
	Description string `json:"description"`
	// ParamsSchema 参数 JSON Schema。
	ParamsSchema map[string]any `json:"params_schema"`
}

// Tool 工具契约：所有工具实现此接口，由 tools.Registry() 统一注册，
// cli / daemon 统一调度。
type Tool interface {
	// Info 返回工具元信息。
	Info() ToolInfo
	// Run 以 JSON 参数执行工具，返回产物元信息。
	Run(ctx context.Context, args json.RawMessage) (*ToolOutput, *ToolError)
}

// RequireStr 从 JSON 参数中取出必填字符串字段；缺失或类型不符时返回带字段名的参数错误。
//
//   - args: 任务参数 JSON。
//   - key: 字段名。
func RequireStr(args json.RawMessage, key string) (string, *ToolError) {
	var m map[string]json.RawMessage
	if len(args) > 0 {
		if err := json.Unmarshal(args, &m); err != nil {
			return "", NewParamsError(fmt.Sprintf("invalid args json: %v", err))
		}
	}
	raw, ok := m[key]
	if !ok {
		return "", NewParamsError(fmt.Sprintf("missing or invalid args.%s", key))
	}
	var value string
	if err := json.Unmarshal(raw, &value); err != nil || value == "" {
		return "", NewParamsError(fmt.Sprintf("missing or invalid args.%s", key))
	}
	return value, nil
}
