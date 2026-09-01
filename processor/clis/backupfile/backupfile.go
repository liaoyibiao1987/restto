// backupfile 是独立的第三方可执行程序：
// tar + gzip 打包文件 / 目录为 .tar.gz 备份产物。
//
// 实现 common.Tool，工具名 backup_file（与服务端下发的 module 名一致）；
// 由 restto-client 经 internal/runner 以子进程调度（约定见 clis/README.md），
// 也可单独编译、单独执行。底层 BackupPath 为纯函数，便于单独复用与单测。
package main

import (
	"archive/tar"
	"compress/gzip"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"strings"
	"time"

	"restto-client/internal/common"
	"restto-client/internal/core/crypto"
)

// timeNow 可在测试中被替换的时钟。
var timeNow = time.Now

// Name 工具名（保持稳定，服务端按此名调度）。
const Name = "backup_file"

// Tool 工具入口。
type Tool struct{}

// BackupPath 将 src（目录或单个文件）打包为 destFile（.tar.gz），返回产物元信息。
//
//   - src: 待备份的源路径。
//   - destFile: 产物路径（建议以 .tar.gz 结尾）。
func BackupPath(src, destFile string) (*common.ToolOutput, *common.ToolError) {
	info, err := os.Stat(src)
	if err != nil {
		if os.IsNotExist(err) {
			return nil, common.NewParamsError(fmt.Sprintf("source path not found: %s", src))
		}
		return nil, common.NewIOError(err.Error())
	}
	if parent := filepath.Dir(destFile); parent != "" && parent != "." {
		if err := os.MkdirAll(parent, 0o755); err != nil {
			return nil, common.NewIOError(err.Error())
		}
	}

	out, err := os.Create(destFile)
	if err != nil {
		return nil, common.NewIOError(err.Error())
	}
	defer out.Close() //nolint:errcheck // 创建失败已在下方转 ToolError

	gzWriter := gzip.NewWriter(out)
	defer gzWriter.Close() //nolint:errcheck // Close 仅刷缓冲，后续 fsync 由操作系统保证

	tarWriter := tar.NewWriter(gzWriter)
	defer tarWriter.Close() //nolint:errcheck

	// 归档内的根条目名取源路径最后一段（目录则含其自身）。
	arcName := filepath.Base(strings.TrimSuffix(filepath.Clean(src), "/"))
	if arcName == "" || arcName == "." || arcName == "/" {
		arcName = "."
	}

	if info.IsDir() {
		if terr := appendDirAll(tarWriter, arcName, src); terr != nil {
			return nil, terr
		}
	} else if terr := appendFile(tarWriter, arcName, src); terr != nil {
		return nil, terr
	}

	// 显式依次收尾（tar → gzip → file），任一失败转归档错误。
	if err := tarWriter.Close(); err != nil {
		return nil, common.NewArchiveError(err.Error())
	}
	if err := gzWriter.Close(); err != nil {
		return nil, common.NewArchiveError(err.Error())
	}
	if err := out.Close(); err != nil {
		return nil, common.NewIOError(err.Error())
	}

	return describeOutput(destFile)
}

// appendDirAll 递归写入目录（根条目名 root，保持与 tar 命令一致的相对路径）。
func appendDirAll(tarWriter *tar.Writer, root, srcDir string) *common.ToolError {
	walkErr := filepath.Walk(srcDir, func(path string, info os.FileInfo, err error) error {
		if err != nil {
			return err
		}
		rel, relErr := filepath.Rel(srcDir, path)
		if relErr != nil {
			return relErr
		}
		// 归档内路径：root/rel（rel 为 "." 时即 root 自身）。
		name := root
		if rel != "." {
			name = root + "/" + filepath.ToSlash(rel)
		}
		if info.IsDir() {
			return writeDirHeader(tarWriter, name, info)
		}
		if info.Mode()&os.ModeSymlink != 0 {
			return writeSymlinkHeader(tarWriter, name, path)
		}
		if !info.Mode().IsRegular() {
			return nil // 跳过 socket / device 等特殊文件
		}
		if err := writeFileHeader(tarWriter, name, info); err != nil {
			return err
		}
		return copyFileContent(tarWriter, path)
	})
	if walkErr != nil {
		if te, ok := walkErr.(*common.ToolError); ok {
			return te
		}
		return common.NewArchiveError(walkErr.Error())
	}
	return nil
}

// writeDirHeader 写入目录条目头。
func writeDirHeader(tarWriter *tar.Writer, name string, info os.FileInfo) error {
	hdr := &tar.Header{
		Name:     name + "/",
		Mode:     int64(info.Mode().Perm()),
		ModTime:  info.ModTime(),
		Typeflag: tar.TypeDir,
	}
	return tarWriter.WriteHeader(hdr)
}

// writeFileHeader 写入普通文件条目头。
func writeFileHeader(tarWriter *tar.Writer, name string, info os.FileInfo) error {
	hdr := &tar.Header{
		Name:     name,
		Mode:     int64(info.Mode().Perm()),
		Size:     info.Size(),
		ModTime:  info.ModTime(),
		Typeflag: tar.TypeReg,
	}
	return tarWriter.WriteHeader(hdr)
}

// writeSymlinkHeader 写入软链条目头（保留链接目标，不解引用）。
func writeSymlinkHeader(tarWriter *tar.Writer, name, linkPath string) error {
	target, err := os.Readlink(linkPath)
	if err != nil {
		return err
	}
	hdr := &tar.Header{
		Name:     name,
		Mode:     0o777,
		ModTime:  timeNow(),
		Typeflag: tar.TypeSymlink,
		Linkname: target,
	}
	return tarWriter.WriteHeader(hdr)
}

// copyFileContent 把文件内容写入归档。
func copyFileContent(tarWriter *tar.Writer, path string) error {
	file, err := os.Open(path)
	if err != nil {
		return err
	}
	defer file.Close() //nolint:errcheck

	_, err = io.Copy(tarWriter, file)
	return err
}

// appendFile 写入单个文件条目（根条目名 name）。
func appendFile(tarWriter *tar.Writer, name, path string) *common.ToolError {
	info, err := os.Stat(path)
	if err != nil {
		return common.NewIOError(err.Error())
	}
	if err := writeFileHeader(tarWriter, name, info); err != nil {
		return common.NewArchiveError(err.Error())
	}
	if err := copyFileContent(tarWriter, path); err != nil {
		return common.NewArchiveError(err.Error())
	}
	return nil
}

// describeOutput 统计产物大小与 sha256，组装 ToolOutput。
func describeOutput(destFile string) (*common.ToolOutput, *common.ToolError) {
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

// Info 返回工具元信息。
func (Tool) Info() common.ToolInfo {
	return common.ToolInfo{
		Name:        Name,
		Description: "将指定文件或目录打包为 .tar.gz 备份产物。",
		ParamsSchema: map[string]any{
			"type":     "object",
			"required": []string{"path", "dest"},
			"properties": map[string]any{
				"path": map[string]any{
					"type":        "string",
					"description": "待备份的源文件 / 目录绝对路径",
				},
				"dest": map[string]any{
					"type":        "string",
					"description": "产物路径，建议以 .tar.gz 结尾",
				},
			},
		},
	}
}

// Run 以 JSON 参数执行工具。
func (Tool) Run(ctx context.Context, args json.RawMessage) (*common.ToolOutput, *common.ToolError) {
	path, err := common.RequireStr(args, "path")
	if err != nil {
		return nil, err
	}
	dest, err := common.RequireStr(args, "dest")
	if err != nil {
		return nil, err
	}
	// 打包为磁盘 IO，放到独立 goroutine 隔离；ctx 取消时中断。
	type result struct {
		out *common.ToolOutput
		err *common.ToolError
	}
	done := make(chan result, 1)
	go func() {
		out, err := BackupPath(path, dest)
		done <- result{out, err}
	}()
	select {
	case <-ctx.Done():
		return nil, common.NewOtherError(fmt.Sprintf("backup canceled: %v", ctx.Err()))
	case r := <-done:
		return r.out, r.err
	}
}
