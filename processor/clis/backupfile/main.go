// backupfile CLI 入口：独立可执行程序的命令行外壳。
//
// 子命令（约定见 clis/README.md，供 restto-client 的 runner 调用 / 人工使用）：
//
//	backupfile run [--args '<json>' | --args-file FILE | --args -]  执行备份，stdout 输出信封
//	backupfile info                                                  输出工具元信息 JSON
//	backupfile version                                               输出版本
//	backupfile --help                                                帮助
//
// 输出契约：stdout 只输出单个 JSON 文档（信封 / 元信息），日志一律写 stderr ——
// 上游 runner 依赖 stdout 的 JSON 纯净性。
package main

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"log/slog"
	"os"
	"os/signal"
	"syscall"

	"restto-client/internal/cliargs"
	"restto-client/internal/common"
)

// version CLI 版本（与 CLIENT_VERSION 环境变量联动，缺省 dev）。
var version = "dev"

func main() {
	os.Exit(run(os.Args[1:], os.Stdin, os.Stdout))
}

// initCLI 日志初始化：私有 slog → stderr（绝不能写 stdout，避免污染信封）。
func initCLI() {
	slog.SetDefault(slog.New(slog.NewTextHandler(os.Stderr, &slog.HandlerOptions{
		Level: levelFromEnv(),
	})))
}

// levelFromEnv 从 LOG_LEVEL 读取日志级别（debug/info/warn/error，缺省 info）。
func levelFromEnv() slog.Level {
	switch os.Getenv("LOG_LEVEL") {
	case "debug":
		return slog.LevelDebug
	case "warn":
		return slog.LevelWarn
	case "error":
		return slog.LevelError
	default:
		return slog.LevelInfo
	}
}

// run 解析并调度子命令；独立函数便于测试与控制退出码。
//
// 退出码：0 成功；1 工具执行失败（信封 ok=false）；2 用法错误。
func run(argv []string, stdin io.Reader, stdout io.Writer) int {
	initCLI()
	// 版本号允许经环境变量注入（与服务端注册的 CLIENT_VERSION 语义对齐）。
	if v := os.Getenv("CLIENT_VERSION"); v != "" {
		version = v
	}

	ctx, cancel := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer cancel()

	if len(argv) == 0 {
		usage(stdout)
		return 2
	}
	switch argv[0] {
	case "run":
		return runBackup(ctx, argv[1:], stdin, stdout)
	case "info":
		cliargs.WriteJSON(stdout, Tool{}.Info())
		return 0
	case "version", "-V", "--version":
		fmt.Fprintf(stdout, "%s %s\n", Name, version)
		return 0
	case "-h", "--help", "help":
		usage(stdout)
		return 0
	default:
		fmt.Fprintf(stdout, "unknown command: %s\n\n", argv[0])
		usage(stdout)
		return 2
	}
}

// runBackup 解析参数来源后执行备份并输出信封。
func runBackup(ctx context.Context, argv []string, stdin io.Reader, stdout io.Writer) int {
	var argsStr, argsFile string
	for i := 0; i < len(argv); i++ {
		arg := argv[i]
		switch arg {
		case "--args", "--args-file":
			if i+1 >= len(argv) {
				fmt.Fprintf(stdout, "%s requires a value\n", arg)
				return 2
			}
			i++
			if arg == "--args" {
				argsStr = argv[i]
			} else {
				argsFile = argv[i]
			}
		default:
			fmt.Fprintf(stdout, "unknown flag: %s\n", arg)
			return 2
		}
	}
	args, code := cliargs.ResolveArgs(argsStr, argsFile, stdout, os.ReadFile, stdin)
	if code != 0 {
		return code
	}

	slog.Info("backup_file starting", "args", redactArgs(args))
	out, terr := Tool{}.Run(ctx, args)
	if terr != nil {
		slog.Error("backup_file failed", "err", terr.Error())
	}
	cliargs.WriteJSON(stdout, common.BuildEnvelope(Name, out, terr))
	if terr != nil {
		return 1
	}
	slog.Info("backup_file finished", "file_path", out.FilePath, "size", out.Size)
	return 0
}

// redactArgs 生成用于日志的参数摘要（保持 JSON 结构，不含敏感字段；本工具无密码类参数）。
func redactArgs(args json.RawMessage) string {
	if len(args) == 0 || len(args) > 512 {
		return fmt.Sprintf("(%d bytes)", len(args))
	}
	return string(args)
}

// usage 打印命令帮助。
func usage(w io.Writer) {
	fmt.Fprint(w, `backupfile — backup_file 备份工具（独立可执行程序）

用法：
  backupfile run [--args '<json>' | --args-file FILE | --args -]  执行备份
  backupfile info                                                  输出参数 JSON Schema
  backupfile version                                               输出版本

参数（JSON）：{"path":"<源路径>","dest":"<产物路径.tar.gz>"}
输出为结构化 JSON 信封，成功退出码 0、工具失败 1、用法错误 2。
`)
}
