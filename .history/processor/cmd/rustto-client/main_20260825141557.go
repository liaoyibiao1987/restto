// rustto-client 主程序：agent 友好的 CLI + 常驻 daemon。
//
// 命令结构（详见各分支）：
//   - daemon：常驻进程（注册 / 心跳 / 接收并执行任务 / 接收二进制下发）。
//   - tool list：列出所有已注册工具（agent 发现）。
//   - tool schema <name>：打印某工具的参数 JSON Schema（agent 自省）。
//   - tool run <name> [--args JSON | --args-file FILE | --args -]：以 JSON 参数运行工具。
//   - backup-file / backup-mysql：兼容旧命令的便捷封装（转 tool run）。
//   - run-module <name> --args JSON：兼容服务端旧调用方式。
//
// 工具运行统一输出结构化 JSON 信封 { ok, tool, output?, error? }，
// 成功退出码 0、工具失败 1 —— 便于 agent / 脚本解析。
package main

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"os"
	"os/signal"
	"strconv"
	"strings"
	"syscall"

	"rustto-client/internal/common"
	"rustto-client/internal/core/config"
	"rustto-client/internal/core/logger"
	"rustto-client/internal/daemon"
	"rustto-client/internal/tools"
)

func main() {
	os.Exit(run(os.Args[1:], os.Stdin, os.Stdout))
}

// run 解析并调度子命令；独立函数便于测试与控制退出码。
//
//   - argv: 去掉程序名后的参数。
//   - stdin / stdout: 注入的标准输入输出。
func run(argv []string, stdin io.Reader, stdout io.Writer) int {
	cfg := config.FromEnv()
	flush := logger.Init(cfg.LogDir, logger.LevelFromEnv())
	defer flush()

	ctx, cancel := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer cancel()

	if len(argv) == 0 {
		usage(stdout)
		return 2
	}

	switch argv[0] {
	case "daemon":
		return runDaemon(ctx, cfg)

	case "tool":
		return runToolAction(ctx, argv[1:], stdout)

	case "backup-file":
		args, ok := parseBackupFileFlags(argv[1:], stdout)
		if !ok {
			return 2
		}
		return runToolByName(ctx, "backup_file", args, stdout)

	case "backup-mysql":
		args, ok := parseBackupMysqlFlags(argv[1:], stdout)
		if !ok {
			return 2
		}
		return runToolByName(ctx, "backup_mysql", args, stdout)

	case "run-module":
		name, argsJSON, ok := parseRunModule(argv[1:], stdout)
		if !ok {
			return 2
		}
		args, err := parseJSONOrEmpty(argsJSON)
		if err != nil {
			fmt.Fprintf(stdout, "invalid --args json: %v\n", err)
			return 2
		}
		return runToolByName(ctx, name, args, stdout)

	case "-h", "--help", "help":
		usage(stdout)
		return 0

	case "-V", "--version", "version":
		fmt.Fprintf(stdout, "rustto-client %s\n", cfg.Version)
		return 0

	default:
		fmt.Fprintf(stdout, "unknown command: %s\n\n", argv[0])
		usage(stdout)
		return 2
	}
}

// runDaemon 启动常驻 daemon。
func runDaemon(ctx context.Context, cfg config.ClientConfig) int {
	if err := daemon.Run(ctx, cfg); err != nil && ctx.Err() == nil {
		return 1
	}
	return 0
}

// runToolAction 处理 tool 子命令。
func runToolAction(ctx context.Context, argv []string, stdout io.Writer) int {
	if len(argv) == 0 {
		usage(stdout)
		return 2
	}
	switch argv[0] {
	case "list":
		return toolList(stdout)
	case "schema":
		if len(argv) < 2 {
			fmt.Fprintln(stdout, "usage: tool schema <name>")
			return 2
		}
		return toolSchema(argv[1], stdout)
	case "run":
		return toolRun(ctx, argv[1:], stdout)
	default:
		fmt.Fprintf(stdout, "unknown tool action: %s\n\n", argv[0])
		usage(stdout)
		return 2
	}
}

// toolList 列出所有已注册工具。
func toolList(stdout io.Writer) int {
	list := make([]map[string]string, 0, len(tools.Registry()))
	for _, tool := range tools.Registry() {
		info := tool.Info()
		list = append(list, map[string]string{"name": info.Name, "description": info.Description})
	}
	writeJSON(stdout, map[string]any{"tools": list})
	return 0
}

// toolSchema 打印某工具的参数 JSON Schema。
func toolSchema(name string, stdout io.Writer) int {
	tool := tools.Lookup(name)
	if tool == nil {
		fmt.Fprintf(stdout, "unknown tool: %s\n", name)
		return 2
	}
	info := tool.Info()
	writeJSON(stdout, map[string]any{
		"name":          info.Name,
		"description":   info.Description,
		"params_schema": info.ParamsSchema,
	})
	return 0
}

// toolRun 处理 tool run：解析参数来源后按名运行。
func toolRun(ctx context.Context, argv []string, stdout io.Writer) int {
	name := ""
	var argsStr, argsFile string
	for i := 0; i < len(argv); i++ {
		arg := argv[i]
		var value string
		hasValue := false
		if arg == "--args" || arg == "--args-file" {
			if i+1 >= len(argv) {
				fmt.Fprintf(stdout, "%s requires a value\n", arg)
				return 2
			}
			i++
			value = argv[i]
			hasValue = true
		}
		switch {
		case arg == "--args" && hasValue:
			argsStr = value
		case arg == "--args-file" && hasValue:
			argsFile = value
		case strings.HasPrefix(arg, "-") && arg != "-":
			fmt.Fprintf(stdout, "unknown flag: %s\n", arg)
			return 2
		case name == "":
			name = arg
		default:
			fmt.Fprintf(stdout, "unexpected argument: %s\n", arg)
			return 2
		}
	}
	if name == "" {
		fmt.Fprintln(stdout, "usage: tool run <name> [--args JSON | --args-file FILE | --args -]")
		return 2
	}
	args, code := resolveArgs(argsStr, argsFile, stdout, os.ReadFile, os.Stdin)
	if code != 0 {
		return code
	}
	return runToolByName(ctx, name, args, stdout)
}

// runToolByName 按名查找并运行工具，打印结构化 JSON 信封；工具失败时退出码 1。
//
//   - name: 工具名（未注册则报错）。
//   - args: 已解析的参数 JSON。
func runToolByName(ctx context.Context, name string, args json.RawMessage, stdout io.Writer) int {
	tool := tools.Lookup(name)
	if tool == nil {
		fmt.Fprintf(stdout, "unknown tool: %s\n", name)
		return 2
	}
	out, err := tool.Run(ctx, args)
	writeJSON(stdout, buildEnvelope(name, out, err))
	if err != nil {
		return 1
	}
	return 0
}

// buildEnvelope 构造统一的工具运行结果信封（纯函数，便于单测）。
func buildEnvelope(name string, out *common.ToolOutput, err *common.ToolError) map[string]any {
	if err != nil {
		return map[string]any{"ok": false, "tool": name, "error": err.Error()}
	}
	return map[string]any{
		"ok":     true,
		"tool":   name,
		"output": out,
	}
}

// resolveArgs 解析 tool run 的参数来源：内联 / 文件 / stdin / 空对象。
//
// 优先级：--args（值为 - 表示读 stdin）> --args-file > 空 {}。
func resolveArgs(argsStr, argsFile string, stdout io.Writer, readFile func(string) ([]byte, error), stdin io.Reader) (json.RawMessage, int) {
	if argsStr != "" {
		if argsStr == "-" {
			return readStdinJSON(stdin, stdout)
		}
		v, err := parseJSONOrEmpty(argsStr)
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
		v, jsonErr := parseJSONOrEmpty(string(data))
		if jsonErr != nil {
			fmt.Fprintf(stdout, "invalid args-file json: %v\n", jsonErr)
			return nil, 2
		}
		return v, 0
	}
	return json.RawMessage(`{}`), 0
}

// readStdinJSON 从 stdin 读取并解析 JSON（用于 --args - 管道输入）。
func readStdinJSON(stdin io.Reader, stdout io.Writer) (json.RawMessage, int) {
	data, err := io.ReadAll(stdin)
	if err != nil {
		fmt.Fprintf(stdout, "read stdin: %v\n", err)
		return nil, 2
	}
	if len(trimSpace(string(data))) == 0 {
		return json.RawMessage(`{}`), 0
	}
	v, jsonErr := parseJSONOrEmpty(string(data))
	if jsonErr != nil {
		fmt.Fprintf(stdout, "invalid stdin json: %v\n", jsonErr)
		return nil, 2
	}
	return v, 0
}

// parseJSONOrEmpty 解析 JSON 字符串为 RawMessage。
func parseJSONOrEmpty(s string) (json.RawMessage, error) {
	s = trimSpace(s)
	if s == "" {
		return json.RawMessage(`{}`), nil
	}
	if !json.Valid([]byte(s)) {
		return nil, fmt.Errorf("not valid json")
	}
	return json.RawMessage(s), nil
}

// parseBackupFileFlags 解析 backup-file 兼容命令参数。
func parseBackupFileFlags(argv []string, stdout io.Writer) (json.RawMessage, bool) {
	var path, dest string
	for i := 0; i < len(argv); i++ {
		switch argv[i] {
		case "--path":
			if i+1 >= len(argv) {
				fmt.Fprintln(stdout, "--path requires a value")
				return nil, false
			}
			i++
			path = argv[i]
		case "--dest":
			if i+1 >= len(argv) {
				fmt.Fprintln(stdout, "--dest requires a value")
				return nil, false
			}
			i++
			dest = argv[i]
		default:
			fmt.Fprintf(stdout, "unexpected argument: %s\n", argv[i])
			return nil, false
		}
	}
	if path == "" || dest == "" {
		fmt.Fprintln(stdout, "usage: backup-file --path P --dest D")
		return nil, false
	}
	return marshalArgs(map[string]string{"path": path, "dest": dest})
}

// parseBackupMysqlFlags 解析 backup-mysql 兼容命令参数。
func parseBackupMysqlFlags(argv []string, stdout io.Writer) (json.RawMessage, bool) {
	var host, user, password, database, dest string
	port := "3306"
	for i := 0; i < len(argv); i++ {
		need := func() string {
			if i+1 >= len(argv) {
				return ""
			}
			i++
			return argv[i]
		}
		switch argv[i] {
		case "--host":
			host = need()
		case "--port":
			port = need()
		case "--user":
			user = need()
		case "--password":
			password = need()
		case "--database":
			database = need()
		case "--dest":
			dest = need()
		default:
			fmt.Fprintf(stdout, "unexpected argument: %s\n", argv[i])
			return nil, false
		}
	}
	if host == "" {
		host = "127.0.0.1"
	}
	if user == "" || database == "" || dest == "" {
		fmt.Fprintln(stdout, "usage: backup-mysql --host H --port P --user U --password P --database D --dest F")
		return nil, false
	}
	args := map[string]any{"host": host, "user": user, "password": password, "database": database, "dest": dest}
	if n, err := strconv.Atoi(port); err == nil {
		args["port"] = n
	} else {
		fmt.Fprintf(stdout, "invalid --port: %s\n", port)
		return nil, false
	}
	return marshalArgs(args)
}

// parseRunModule 解析 run-module 兼容命令参数。
func parseRunModule(argv []string, stdout io.Writer) (name, argsJSON string, ok bool) {
	argsJSON = "{}"
	for i := 0; i < len(argv); i++ {
		switch argv[i] {
		case "--args":
			if i+1 >= len(argv) {
				fmt.Fprintln(stdout, "--args requires a value")
				return "", "", false
			}
			i++
			argsJSON = argv[i]
		default:
			if name == "" {
				name = argv[i]
			} else {
				fmt.Fprintf(stdout, "unexpected argument: %s\n", argv[i])
				return "", "", false
			}
		}
	}
	if name == "" {
		fmt.Fprintln(stdout, "usage: run-module <name> --args '<json>'")
		return "", "", false
	}
	return name, argsJSON, true
}

// marshalArgs 把 map 序列化为参数 JSON（失败时理论上不可达）。
func marshalArgs(m any) (json.RawMessage, bool) {
	data, err := json.Marshal(m)
	if err != nil {
		return nil, false
	}
	return data, true
}

// writeJSON 以缩进格式输出 JSON 到 w。
func writeJSON(w io.Writer, v any) {
	data, err := json.MarshalIndent(v, "", "  ")
	if err != nil {
		return
	}
	fmt.Fprintln(w, string(data))
}

// trimSpace 去除首尾空白。
func trimSpace(s string) string {
	start, end := 0, len(s)
	for start < end && (s[start] == ' ' || s[start] == '\t' || s[start] == '\n' || s[start] == '\r') {
		start++
	}
	for end > start && (s[end-1] == ' ' || s[end-1] == '\t' || s[end-1] == '\n' || s[end-1] == '\r') {
		end--
	}
	return s[start:end]
}

// usage 打印命令帮助。
func usage(w io.Writer) {
	fmt.Fprint(w, `rustto-client — rustto 分布式备份系统客户端（agent 友好 CLI）

用法：
  rustto-client daemon                                  常驻：连接 Manager，接收并执行任务
  rustto-client tool list                               列出所有工具（agent 发现）
  rustto-client tool schema <name>                      打印某工具的参数 JSON Schema
  rustto-client tool run <name> [--args|--args-file]    以 JSON 参数运行工具
  rustto-client backup-file --path P --dest D           兼容：单次文件 / 目录备份
  rustto-client backup-mysql --host .. --database .. .. 兼容：单次 MySQL 备份
  rustto-client run-module <name> --args '<json>'       兼容：按模块名调度

参数来源：--args '<json>' / --args-file ./a.json / --args -（stdin）/ 不给则 {}
输出为结构化 JSON 信封，成功退出码 0、失败 1。
`)
}
