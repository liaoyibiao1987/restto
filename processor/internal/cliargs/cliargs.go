// Package cliargs 定义 CLI 参数来源的共用解析逻辑。
//
// restto 主程序与 clis/ 下各第三方可执行程序均支持同一套参数约定：
//
//	--args '<json>'   内联 JSON
//	--args -          从 stdin 读取 JSON（管道输入）
//	--args-file FILE  从文件读取 JSON
//	缺省              等价于空对象 {}
//
// 抽取为共用包避免多处复制实现（AGENT.MD：禁止重复代码）。
package cliargs

import (
	"encoding/json"
	"fmt"
	"io"
)

// ResolveArgs 解析参数来源：内联 / 文件 / stdin / 空对象。
//
// 优先级：--args（值为 - 表示读 stdin）> --args-file > 空 {}。
// 返回解析后的 JSON 与退出码（0 成功；2 用法错误，此时已向 stdout 输出提示）。
//
//   - argsStr: --args 的值（空串表示未提供；"-" 表示 stdin）。
//   - argsFile: --args-file 的路径。
//   - readFile: 读文件函数（生产传 os.ReadFile，测试可注入）。
//   - stdin: 标准输入（用于 --args -）。
func ResolveArgs(argsStr, argsFile string, stdout io.Writer, readFile func(string) ([]byte, error), stdin io.Reader) (json.RawMessage, int) {
	if argsStr != "" {
		if argsStr == "-" {
			return ReadStdinJSON(stdin, stdout)
		}
		v, err := ParseJSONOrEmpty(argsStr)
		if err != nil {
			fmt.Fprintf(stdout, "invalid --args json: %v\n", err)
			return nil, 2
		}
		return v, 0
	}
	if argsFile != "" {
		data, err := readFile(argsFile)
		if err != nil {
			fmt.Fprintf(stdout, "read args-file %s: %v\n", argsFile, err)
			return nil, 2
		}
		v, jsonErr := ParseJSONOrEmpty(string(data))
		if jsonErr != nil {
			fmt.Fprintf(stdout, "invalid args-file json: %v\n", jsonErr)
			return nil, 2
		}
		return v, 0
	}
	return json.RawMessage(`{}`), 0
}

// ReadStdinJSON 从 stdin 读取并解析 JSON（用于 --args - 管道输入）；空白输入等价于 {}。
func ReadStdinJSON(stdin io.Reader, stdout io.Writer) (json.RawMessage, int) {
	data, err := io.ReadAll(stdin)
	if err != nil {
		fmt.Fprintf(stdout, "read stdin: %v\n", err)
		return nil, 2
	}
	if len(TrimSpace(string(data))) == 0 {
		return json.RawMessage(`{}`), 0
	}
	v, jsonErr := ParseJSONOrEmpty(string(data))
	if jsonErr != nil {
		fmt.Fprintf(stdout, "invalid stdin json: %v\n", jsonErr)
		return nil, 2
	}
	return v, 0
}

// ParseJSONOrEmpty 解析 JSON 字符串为 RawMessage；空串等价于 {}，非法 JSON 报错。
func ParseJSONOrEmpty(s string) (json.RawMessage, error) {
	s = TrimSpace(s)
	if s == "" {
		return json.RawMessage(`{}`), nil
	}
	if !json.Valid([]byte(s)) {
		return nil, fmt.Errorf("not valid json")
	}
	return json.RawMessage(s), nil
}

// WriteJSON 以缩进格式输出 JSON 到 w（两空格缩进 + 换行，与既有 CLI 输出格式一致）。
func WriteJSON(w io.Writer, v any) {
	data, err := json.MarshalIndent(v, "", "  ")
	if err != nil {
		return
	}
	fmt.Fprintln(w, string(data))
}

// TrimSpace 去除首尾空白（空格 / 制表符 / 换行 / 回车）。
func TrimSpace(s string) string {
	start, end := 0, len(s)
	for start < end && (s[start] == ' ' || s[start] == '\t' || s[start] == '\n' || s[start] == '\r') {
		start++
	}
	for end > start && (s[end-1] == ' ' || s[end-1] == '\t' || s[end-1] == '\n' || s[end-1] == '\r') {
		end--
	}
	return s[start:end]
}
