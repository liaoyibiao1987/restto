// envelope.go 定义 restto 与第三方 CLI 可执行程序之间的「信封」契约。
//
// 约定：clis/ 下每个 CLI 以 `run --args -` 方式被调用，把参数 JSON 从 stdin 读入，
// 执行完毕后在 stdout 输出**单个 JSON 文档**（信封），日志一律写 stderr：
//
//	成功：{"ok":true,"tool":"backup_file","output":{"file_path":..,"size":..,"checksum":..}}
//	失败：{"ok":false,"tool":"backup_file","error":"..."}
//
// restto 主程序解析该信封（ParseEnvelope），再用 BuildEnvelope 重新渲染为自己的输出，
// 保证对外 JSON 形状与历史上进程内工具的输出字节级一致。
package common

import (
	"encoding/json"
	"fmt"
)

// KindExternal 外部 CLI 回传的错误：Message 原样透传（错误文案与进程内实现保持一致，
// 服务端 TASK_RESULT.error 看到的字符串不变）。
const KindExternal ToolErrorKind = "external"

// KindNotFound 模块名对应的 CLI 未安装 / 未找到。
const KindNotFound ToolErrorKind = "not_found"

// NewExternalError 构造外部 CLI 回传类错误（Message 为信封 error 原文）。
func NewExternalError(message string) *ToolError {
	return &ToolError{Kind: KindExternal, Message: message}
}

// NewNotFoundError 构造模块未找到类错误。
func NewNotFoundError(message string) *ToolError {
	return &ToolError{Kind: KindNotFound, Message: message}
}

// Envelope 信封解析视图（仅用于解析 CLI stdout；构造输出请用 BuildEnvelope）。
type Envelope struct {
	// Ok 工具是否执行成功。
	Ok bool `json:"ok"`
	// Tool 工具（模块）名。
	Tool string `json:"tool"`
	// Output 成功时的产物元信息。
	Output *ToolOutput `json:"output,omitempty"`
	// Error 失败时的错误文案（CLI 进程内 ToolError.Error() 的原文）。
	Error *string `json:"error,omitempty"`
}

// BuildEnvelope 构造统一的工具运行结果信封。
//
// 返回 map[string]any（而非结构体）：encoding/json 对 map 按键字典序输出，
// 与历史上 restto 主程序的 buildEnvelope 字节级一致；退出码语义由调用方维护。
func BuildEnvelope(tool string, out *ToolOutput, terr *ToolError) map[string]any {
	if terr != nil {
		return map[string]any{"ok": false, "tool": tool, "error": terr.Error()}
	}
	return map[string]any{
		"ok":     true,
		"tool":   tool,
		"output": out,
	}
}

// ParseEnvelope 解析 CLI stdout 的信封 JSON。
//
// 校验规则：必须是合法 JSON 对象且含布尔型 ok 字段；ok=true 时要求 output 存在。
// 不满足时返回错误（视为 CLI 输出异常，由 runner 兜底处理）。
func ParseEnvelope(data []byte) (*Envelope, error) {
	var env Envelope
	if err := json.Unmarshal(data, &env); err != nil {
		return nil, fmt.Errorf("invalid envelope json: %w", err)
	}
	// json.Unmarshal 对缺失的 bool 字段零值为 false，无法区分"缺失"与"false"；
	// 用辅助结构探测字段是否真的存在。
	var probe struct {
		Ok     *bool       `json:"ok"`
		Output *ToolOutput `json:"output"`
		Error  *string     `json:"error"`
		Tool   *string     `json:"tool"`
	}
	if err := json.Unmarshal(data, &probe); err != nil {
		return nil, fmt.Errorf("invalid envelope json: %w", err)
	}
	if probe.Ok == nil {
		return nil, fmt.Errorf("envelope missing ok field")
	}
	if env.Ok && env.Output == nil {
		return nil, fmt.Errorf("envelope ok=true but output missing")
	}
	if !env.Ok && env.Error == nil {
		return nil, fmt.Errorf("envelope ok=false but error missing")
	}
	return &env, nil
}
