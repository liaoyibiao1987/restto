// Package runner 实现外部 CLI 可执行程序的调度器：按模块名定位并执行
// clis/ 下的第三方程序，解析其 stdout 信封，映射回统一的工具产物 / 错误模型。
//
// 这是 restto「中间人」角色的核心：主程序与 daemon 均不再进程内集成各备份模块，
// 而是经由本包以子进程方式运行外部 CLI（约定见 clis/README.md）：
//
//	<cli> run --args -   参数 JSON 从 stdin 读入；stdout 输出单个信封 JSON；日志走 stderr
//	<cli> info           stdout 输出 ToolInfo JSON（tool list / tool schema 用）
//
// 注意：本包在任务执行路径上只打 slog.Debug 日志 —— core/logger 会把日志双写到
// stdout，Info 级日志会污染 `restto-client tool run` 输出的信封 JSON。
package runner

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"log/slog"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"sort"
	"strings"
	"time"

	"restto-client/internal/common"
)

// infoTimeout 单次 `<cli> info` 调用的超时（tool list / schema 为交互命令，不允许久挂）。
const infoTimeout = 10 * time.Second

// stderrTailLimit CLI 异常退出时，错误信息携带的 stderr 末尾长度上限（字节）。
const stderrTailLimit = 2 * 1024

// waitDelay ctx 取消后 Wait 的兜底等待上限（防孤儿进程持有管道导致挂起）。
const waitDelay = 3 * time.Second

// Runner 外部 CLI 调度器。无状态、可并发复用（每个子进程独立）。
type Runner struct {
	// dirs 按优先级排列的 CLI 查找目录（不含 PATH）。
	dirs []string
	// lookUp PATH 兜底查找函数；生产为 exec.LookPath，测试可注入。
	lookUp func(string) (string, error)
}

// NewRunner 构造调度器；dirs 为按优先级排列的查找目录（空目录列表则仅依赖 PATH）。
func NewRunner(dirs ...string) *Runner {
	return &Runner{dirs: dirs, lookUp: exec.LookPath}
}

// DefaultDirs 返回默认查找目录（按优先级）：
//
//	<DATA_DIR>/clis   服务端下发的扩展包安装目标，优先级最高（可覆盖随包分发的版本）
//	bin/clis          开发仓布局（scripts/build.sh 的产物位置，相对当前工作目录）
//	$RESTTO_CLIS_DIR  可选的自定义目录（打包 / 特殊部署用）
//
// PATH 兜底由 Run/Info 内部经 exec.LookPath 完成（覆盖 Docker 镜像 /usr/local/bin 布局）。
func DefaultDirs(dataDir string) []string {
	dirs := []string{filepath.Join(dataDir, "clis"), filepath.Join("bin", "clis")}
	if custom := strings.TrimSpace(os.Getenv("RESTTO_CLIS_DIR")); custom != "" {
		dirs = append(dirs, custom)
	}
	return dirs
}

// ValidModuleName 校验模块（工具）名是否合法：以字母或数字开头，
// 其后为字母 / 数字 / 点 / 下划线 / 连字符，总长 1..64。
//
// 模块名来自服务端 TASK_COMMAND（管理员自由输入）与 BINARY_PUSH 的版本路由，
// 会拼入文件路径 —— 必须先过本校验以阻断路径穿越（如 ../x、a/b）。
func ValidModuleName(name string) bool {
	if len(name) == 0 || len(name) > 64 {
		return false
	}
	for i, r := range name {
		switch {
		case r >= 'a' && r <= 'z', r >= 'A' && r <= 'Z', r >= '0' && r <= '9':
			// 首字符与中间字符均可。
		case i > 0 && (r == '.' || r == '_' || r == '-'):
			// 分隔符不允许出现在首位。
		default:
			return false
		}
	}
	return true
}

// candidateNames 生成模块名对应的候选可执行文件名（按优先级）。
//
// 服务端模块名以下划线分隔（backup_file），而构建产物以目录名命名（backupfile），
// 因此先尝试精确名，再尝试去除下划线的变体；Windows 下 .exe 后缀优先。
func candidateNames(module, goos string) []string {
	stripped := strings.ReplaceAll(module, "_", "")
	if stripped == module {
		stripped = ""
	}
	bases := []string{module}
	if stripped != "" {
		bases = append(bases, stripped)
	}
	if goos != "windows" {
		return bases
	}
	// Windows：.exe 优先于无后缀。
	var out []string
	for _, base := range bases {
		out = append(out, base+".exe")
	}
	return append(out, bases...)
}

// resolve 按目录优先级 + PATH 定位模块对应的可执行文件。
//
// 命中条件：regular 文件且带可执行位。未找到返回 KindNotFound 错误。
func (r *Runner) resolve(module string) (string, *common.ToolError) {
	if !ValidModuleName(module) {
		return "", common.NewNotFoundError(fmt.Sprintf("unknown module: %s", module))
	}
	names := candidateNames(module, runtime.GOOS)
	for _, dir := range r.dirs {
		for _, name := range names {
			path := filepath.Join(dir, name)
			if isExecutableFile(path) {
				return path, nil
			}
		}
	}
	// PATH 兜底（Docker 镜像把 CLI 装进 /usr/local/bin 的场景）。
	for _, name := range names {
		if path, err := r.lookUp(name); err == nil && isExecutableFile(path) {
			return path, nil
		}
	}
	return "", common.NewNotFoundError(fmt.Sprintf("unknown module: %s", module))
}

// isExecutableFile 判断路径是否为带可执行位的普通文件。
func isExecutableFile(path string) bool {
	info, err := os.Stat(path)
	if err != nil || info.IsDir() {
		return false
	}
	return info.Mode().Perm()&0o111 != 0
}

// Run 执行模块对应的外部 CLI 并解析信封结果。
//
// 进程契约：`<cli> run --args -`，参数 JSON 经 stdin 传入（避开命令行长度限制）；
// stdout 为信封 JSON，stderr 为日志（异常时取末尾附入错误信息）。
// ctx 取消会杀死子进程（exec.CommandContext）。
func (r *Runner) Run(ctx context.Context, module string, args json.RawMessage) (*common.ToolOutput, *common.ToolError) {
	path, terr := r.resolve(module)
	if terr != nil {
		return nil, terr
	}
	if len(args) == 0 {
		args = json.RawMessage(`{}`) // 与进程内实现 RequireStr(nil,...) 的行为对齐
	}

	slog.Debug("runner exec cli", "module", module, "path", path)
	stdout, stderr, exitErr := runCLI(ctx, path, []string{"run", "--args", "-"}, args)
	if ctx.Err() != nil {
		return nil, common.NewOtherError(fmt.Sprintf("module %s canceled: %v", module, ctx.Err()))
	}
	if exitErr != nil {
		slog.Debug("cli exited with error", "module", module, "err", exitErr.Error(), "stderr_bytes", stderr.Len())
	}

	// 信封优先：只要 stdout 是合法信封，以信封结论为准（即使退出码非 0）。
	if env, err := common.ParseEnvelope(stdout.Bytes()); err == nil {
		if env.Ok {
			return env.Output, nil
		}
		return nil, common.NewExternalError(*env.Error)
	}

	// 信封不可解析：属于 CLI 异常（崩溃 / 输出被污染），附带退出码与 stderr 末尾。
	var code int
	var exitErrDetail string
	if exitErr != nil {
		code, exitErrDetail = exitCodeOf(exitErr)
	}
	return nil, common.NewExternalError(fmt.Sprintf(
		"cli %s exited code=%d: stderr tail: %s (%s)",
		module, code, tail(stderr.String(), stderrTailLimit), exitErrDetail))
}

// Info 执行 `<cli> info` 获取模块元信息（tool schema 用）。
func (r *Runner) Info(ctx context.Context, module string) (*common.ToolInfo, *common.ToolError) {
	path, terr := r.resolve(module)
	if terr != nil {
		return nil, terr
	}
	ctx, cancel := context.WithTimeout(ctx, infoTimeout)
	defer cancel()
	stdout, _, exitErr := runCLI(ctx, path, []string{"info"}, nil)
	if ctx.Err() != nil {
		return nil, common.NewOtherError(fmt.Sprintf("module %s info canceled: %v", module, ctx.Err()))
	}
	if exitErr != nil {
		code, detail := exitCodeOf(exitErr)
		return nil, common.NewExternalError(fmt.Sprintf("cli %s info exited code=%d: %s", module, code, detail))
	}
	var info common.ToolInfo
	if err := json.Unmarshal(stdout.Bytes(), &info); err != nil || info.Name == "" {
		return nil, common.NewExternalError(fmt.Sprintf("cli %s info output invalid", module))
	}
	return &info, nil
}

// List 扫描查找目录收集已安装的 CLI，逐个执行 `<cli> info` 获取元信息。
//
// 去重（同名取最高优先级目录中的那个）；单个 info 失败仅 Debug 记录并跳过，
// 不让个别异常 CLI 拖垮整体发现；结果按 Name 排序保证输出稳定。
func (r *Runner) List(ctx context.Context) []common.ToolInfo {
	// 去重键为去下划线规范化文件名（backup_file 与 backupfile 视为同一 CLI），
	// 先遍历到的目录优先级更高（见 DefaultDirs 的顺序）。
	seen := map[string]bool{}
	var paths []string
	for _, dir := range r.dirs {
		entries, err := os.ReadDir(dir)
		if err != nil {
			continue // 目录不存在属正常（例如尚未安装扩展包）。
		}
		for _, entry := range entries {
			name := entry.Name()
			key := strings.ReplaceAll(name, "_", "")
			if seen[key] || !isExecutableFile(filepath.Join(dir, name)) {
				continue
			}
			seen[key] = true
			paths = append(paths, filepath.Join(dir, name))
		}
	}
	var infos []common.ToolInfo
	byName := map[string]bool{}
	for _, path := range paths {
		ctx, cancel := context.WithTimeout(ctx, infoTimeout)
		var info common.ToolInfo
		stdout, _, exitErr := runCLI(ctx, path, []string{"info"}, nil)
		cancel()
		if exitErr != nil || json.Unmarshal(stdout.Bytes(), &info) != nil || info.Name == "" {
			slog.Debug("cli info failed, skip", "path", path)
			continue
		}
		if byName[info.Name] {
			continue // CLI 自报名重复，防御性去重。
		}
		byName[info.Name] = true
		infos = append(infos, info)
	}
	sort.Slice(infos, func(i, j int) bool { return infos[i].Name < infos[j].Name })
	return infos
}

// runCLI 以给定参数运行 CLI；args 非空时以 `run --args -` 形式经 stdin 传入 JSON。
//
// stdout/stderr 均入内存缓冲（信封很小）；Stdin 用 bytes.Reader，
// os/exec 会在独立 goroutine 中拷贝，无「写 stdin / 读 stdout」死锁风险。
func runCLI(ctx context.Context, path string, argv []string, args json.RawMessage) (*bytes.Buffer, *bytes.Buffer, error) {
	cmd := exec.CommandContext(ctx, path, argv...)
	if args != nil {
		cmd.Stdin = bytes.NewReader(args)
	}
	// 取消时杀整个子进程组（Unix），并兜底防止孤儿进程持有的管道阻塞 Wait。
	procGroupWait(cmd)
	cmd.WaitDelay = waitDelay
	var stdout, stderr bytes.Buffer
	cmd.Stdout = &stdout
	cmd.Stderr = &stderr
	err := cmd.Start()
	if err != nil {
		return &stdout, &stderr, fmt.Errorf("start: %w", err)
	}
	err = cmd.Wait()
	return &stdout, &stderr, err
}

// exitCodeOf 从 exec 返回的错误中提取退出码与可读描述。
func exitCodeOf(err error) (int, string) {
	var exitErr *exec.ExitError
	if errors.As(err, &exitErr) {
		return exitErr.ExitCode(), exitErr.String()
	}
	return -1, err.Error()
}

// tail 截取字符串末尾至多 limit 字节（按 UTF-8 安全边界回退避免截断多字节字符）。
func tail(s string, limit int) string {
	if len(s) <= limit {
		return s
	}
	cut := len(s) - limit
	for cut < len(s) && !utf8RuneStart(s[cut]) {
		cut++
	}
	return s[cut:]
}

// utf8RuneStart 判断字节是否为 UTF-8 序列的首字节。
func utf8RuneStart(b byte) bool {
	return b&0xC0 != 0x80
}
