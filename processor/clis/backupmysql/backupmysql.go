// backupmysql 是独立的第三方可执行程序：
// 调用系统 mysqldump 导出数据库，再 gzip 压缩为 .sql.gz 产物。
//
// 实现 common.Tool，工具名 backup_mysql（与服务端下发的 module 名一致）；
// 由 restto-client 经 internal/runner 以子进程调度（约定见 clis/README.md），
// 也可单独编译、单独执行。要求目标机已安装 mysqldump 并在 PATH 中可用。
package main

import (
	"bytes"
	"compress/gzip"
	"context"
	"encoding/json"
	"fmt"
	"os"
	"os/exec"
	"path/filepath"

	"restto-client/internal/common"
	"restto-client/internal/core/crypto"
)

// Name 工具名（保持稳定，服务端按此名调度）。
const Name = "backup_mysql"

// DefaultPort 缺省 MySQL 端口。
const DefaultPort = 3306

// Tool 工具入口。
type Tool struct{}

// MysqlParams MySQL 备份连接参数（来自任务 args JSON）。
type MysqlParams struct {
	// Host 数据库主机。
	Host string `json:"host"`
	// Port 端口，缺省 3306。
	Port *uint16 `json:"port"`
	// User 账号。
	User string `json:"user"`
	// Password 密码（明文，经任务参数下发，传输层由 Netty 协议保障）。
	Password string `json:"password"`
	// Database 目标数据库名。
	Database string `json:"database"`
}

// effectivePort 返回生效端口。
func (p *MysqlParams) effectivePort() uint16 {
	if p.Port != nil {
		return *p.Port
	}
	return DefaultPort
}

// BackupMysql 执行一次 mysqldump，把结果 gzip 压缩写入 destFile，返回产物元信息。
//
//   - ctx: 取消时终止子进程。
//   - params: 连接参数。
//   - destFile: 产物路径（建议 .sql.gz）。
func BackupMysql(ctx context.Context, params *MysqlParams, destFile string) (*common.ToolOutput, *common.ToolError) {
	if parent := filepath.Dir(destFile); parent != "" && parent != "." {
		if err := os.MkdirAll(parent, 0o755); err != nil {
			return nil, common.NewIOError(err.Error())
		}
	}

	port := fmt.Sprintf("%d", params.effectivePort())
	cmd := exec.CommandContext(ctx, "mysqldump",
		"-h", params.Host,
		"-P", port,
		"-u", params.User,
		fmt.Sprintf("--password=%s", params.Password),
		"--single-transaction",
		"--quick",
		params.Database,
	)
	var stdout, stderr bytes.Buffer
	cmd.Stdout = &stdout
	cmd.Stderr = &stderr

	if err := cmd.Run(); err != nil {
		code := -1
		if exitErr, ok := err.(*exec.ExitError); ok {
			code = exitErr.ExitCode()
		}
		if ctx.Err() != nil {
			return nil, common.NewOtherError(fmt.Sprintf("mysqldump canceled: %v", ctx.Err()))
		}
		return nil, common.NewSubprocessError(code, stderr.String())
	}

	// gzip 压缩 dump 输出。
	out, err := os.Create(destFile)
	if err != nil {
		return nil, common.NewIOError(err.Error())
	}
	gzWriter := gzip.NewWriter(out)
	if _, err := gzWriter.Write(stdout.Bytes()); err != nil {
		_ = gzWriter.Close()
		_ = out.Close()
		return nil, common.NewArchiveError(err.Error())
	}
	if err := gzWriter.Close(); err != nil {
		_ = out.Close()
		return nil, common.NewArchiveError(err.Error())
	}
	if err := out.Close(); err != nil {
		return nil, common.NewIOError(err.Error())
	}

	stat, err := os.Stat(destFile)
	if err != nil {
		return nil, common.NewIOError(err.Error())
	}
	checksum, err := crypto.Sha256File(destFile)
	if err != nil {
		return nil, common.NewIOError(err.Error())
	}
	abs, err := filepath.Abs(destFile)
	if err != nil {
		abs = destFile
	}
	return &common.ToolOutput{
		FilePath: abs,
		Size:     uint64(stat.Size()),
		Checksum: checksum,
	}, nil
}

// ParseParams 从任务 args JSON 解析连接参数（含必填校验）。
func ParseParams(args json.RawMessage) (*MysqlParams, *common.ToolError) {
	var params MysqlParams
	if len(args) > 0 {
		if err := json.Unmarshal(args, &params); err != nil {
			return nil, common.NewParamsError(fmt.Sprintf("invalid mysql params: %v", err))
		}
	}
	if params.Host == "" || params.User == "" || params.Database == "" {
		return nil, common.NewParamsError("invalid mysql params: host/user/database required")
	}
	return &params, nil
}

// Info 返回工具元信息。
func (Tool) Info() common.ToolInfo {
	return common.ToolInfo{
		Name:        Name,
		Description: "通过 mysqldump 导出数据库并 gzip 压缩为 .sql.gz 产物。",
		ParamsSchema: map[string]any{
			"type":     "object",
			"required": []string{"host", "user", "database", "dest"},
			"properties": map[string]any{
				"host":     map[string]any{"type": "string", "description": "数据库主机", "default": "127.0.0.1"},
				"port":     map[string]any{"type": "integer", "description": "端口", "default": 3306},
				"user":     map[string]any{"type": "string", "description": "账号"},
				"password": map[string]any{"type": "string", "description": "密码（明文）", "default": ""},
				"database": map[string]any{"type": "string", "description": "目标数据库名"},
				"dest":     map[string]any{"type": "string", "description": "产物路径，建议 .sql.gz 结尾"},
			},
		},
	}
}

// Run 以 JSON 参数执行工具。
func (Tool) Run(ctx context.Context, args json.RawMessage) (*common.ToolOutput, *common.ToolError) {
	params, err := ParseParams(args)
	if err != nil {
		return nil, err
	}
	dest, err := common.RequireStr(args, "dest")
	if err != nil {
		return nil, err
	}
	return BackupMysql(ctx, params, dest)
}
