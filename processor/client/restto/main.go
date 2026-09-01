// restto-client 主程序（中间人）：agent 友好的 CLI + 常驻 daemon。
//
// 命令结构（详见各分支）：
//   - daemon：常驻进程（注册 / 心跳 / 接收并执行任务 / 接收二进制下发与自升级）。
//   - tool list：列出所有可用 CLI（agent 发现）。
//   - tool schema <name>：打印某 CLI 的参数 JSON Schema（agent 自省）。
//   - tool run <name> [--args JSON | --args-file FILE | --args -]：以 JSON 参数运行 CLI。
//
// 工具本体是 clis/ 下的独立第三方可执行程序，经 internal/runner 以子进程调度
// （查找优先级见 runner.DefaultDirs）；本程序只负责解析参数、转发执行、
// 处理 CLI 输出并输出结构化 JSON 信封 { ok, tool, output?, error? }，
// 成功退出码 0、工具失败 1 —— 便于 agent / 脚本解析。
package main

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"log/slog"
	"os"
	"os/signal"
	"strings"
	"syscall"

	"restto-client/internal/cliargs"
	"restto-client/internal/common"
	"restto-client/internal/core/config"
	"restto-client/internal/core/logger"
	"restto-client/internal/daemon"
	"restto-client/internal/runner"
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

	// CLI 调度器一次构建，命令内复用（查找目录见 runner.DefaultDirs）。
	cliRun := runner.NewRunner(runner.DefaultDirs(cfg.DataDir)...)

	if len(argv) == 0 {
		usage(stdout)
		return 2
	}

	switch argv[0] {
	case "daemon":
		return runDaemon(ctx, cfg)

	case "tool":
		return runToolAction(ctx, cliRun, argv[1:], stdin, stdout)

	case "-h", "--help", "help":
		usage(stdout)
		return 0

	case "-V", "--version", "version":
		fmt.Fprintf(stdout, "restto-client %s\n", cfg.Version)
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
func runToolAction(ctx context.Context, cliRun *runner.Runner, argv []string, stdin io.Reader, stdout io.Writer) int {
	if len(argv) == 0 {
		usage(stdout)
		return 2
	}
	switch argv[0] {
	case "list":
		return toolList(ctx, cliRun, stdout)
	case "schema":
		if len(argv) < 2 {
			fmt.Fprintln(stdout, "usage: tool schema <name>")
			return 2
		}
		return toolSchema(ctx, cliRun, argv[1], stdout)
	case "run":
		return toolRun(ctx, cliRun, argv[1:], stdin, stdout)
	default:
		fmt.Fprintf(stdout, "unknown tool action: %s\n\n", argv[0])
		usage(stdout)
		return 2
	}
}

// toolList 列出所有可用的外部 CLI（扫描查找目录 + 逐个 info）。
func toolList(ctx context.Context, cliRun *runner.Runner, stdout io.Writer) int {
	infos := cliRun.List(ctx)
	list := make([]map[string]string, 0, len(infos))
	for _, info := range infos {
		list = append(list, map[string]string{"name": info.Name, "description": info.Description})
	}
	cliargs.WriteJSON(stdout, map[string]any{"tools": list})
	return 0
}

// toolSchema 打印某 CLI 的参数 JSON Schema。
func toolSchema(ctx context.Context, cliRun *runner.Runner, name string, stdout io.Writer) int {
	info, terr := cliRun.Info(ctx, name)
	if terr != nil {
		if terr.Kind == common.KindNotFound {
			fmt.Fprintf(stdout, "unknown tool: %s\n", name)
			return 2
		}
		slog.Error("cli info failed", "tool", name, "err", terr.Error())
		fmt.Fprintf(stdout, "tool error: %s\n", terr.Error())
		return 1
	}
	cliargs.WriteJSON(stdout, map[string]any{
		"name":          info.Name,
		"description":   info.Description,
		"params_schema": info.ParamsSchema,
	})
	return 0
}

// toolRun 处理 tool run：解析参数来源后按名运行外部 CLI。
func toolRun(ctx context.Context, cliRun *runner.Runner, argv []string, stdin io.Reader, stdout io.Writer) int {
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
	args, code := cliargs.ResolveArgs(argsStr, argsFile, stdout, os.ReadFile, stdin)
	if code != 0 {
		return code
	}
	return runToolByName(ctx, cliRun, name, args, stdout)
}

// runToolByName 经 runner 运行外部 CLI，打印结构化 JSON 信封；
// CLI 不存在退出码 2，CLI 执行失败退出码 1。
//
//   - name: CLI 模块名。
//   - args: 已解析的参数 JSON。
func runToolByName(ctx context.Context, cliRun *runner.Runner, name string, args json.RawMessage, stdout io.Writer) int {
	out, terr := cliRun.Run(ctx, name, args)
	if terr != nil && terr.Kind == common.KindNotFound {
		fmt.Fprintf(stdout, "unknown tool: %s\n", name)
		return 2
	}
	cliargs.WriteJSON(stdout, common.BuildEnvelope(name, out, terr))
	if terr != nil {
		return 1
	}
	return 0
}

// usage 打印命令帮助。
func usage(w io.Writer) {
	fmt.Fprint(w, `restto-client — restto 分布式备份系统客户端（中间人 + agent 友好 CLI）

用法：
  restto-client daemon                                  常驻：连接 Manager，接收并执行任务
  restto-client tool list                               列出所有可用 CLI（agent 发现）
  restto-client tool schema <name>                      打印某 CLI 的参数 JSON Schema
  restto-client tool run <name> [--args|--args-file]    以 JSON 参数运行外部 CLI

参数来源：--args '<json>' / --args-file ./a.json / --args -（stdin）/ 不给则 {}
输出为结构化 JSON 信封，成功退出码 0、失败 1、CLI 不存在 2。
CLI 查找目录：$DATA_DIR/clis → bin/clis → $RESTTO_CLIS_DIR → PATH。
`)
}
