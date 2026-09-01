// Package upgrade 提供可执行文件的原子落盘安装。
//
// 两个使用场景（同一机制）：
//
//   - 扩展包安装：服务端 BINARY_PUSH（version 形如 module@ver）→ 装入 <DATA_DIR>/clis/<module>
//   - 自升级：    服务端 BINARY_PUSH（version 为纯版本号）→ 覆盖当前可执行文件
//
// 为什么必须「同目录临时文件 + rename」而不是直接覆盖写：
// Linux 上对正在运行的可执行文件直接写入会得到 ETXTBSY；
// rename 覆盖则换成新 inode，旧进程（若还在跑）继续用旧映像，天然原子。
package upgrade

import (
	"fmt"
	"log/slog"
	"os"
	"path/filepath"
)

// newTempFile 测试缝：默认 os.CreateTemp，测试可注入故障（如创建失败 / 已关闭的文件句柄）。
var newTempFile = os.CreateTemp

// stageFile 在 dir 内创建内容为 data、权限 0755 的临时文件并返回其路径。
// 独立为包级变量是为了测试可注入故障（与本仓 backupfile 的 timeNow 同类测试缝）。
var stageFile = func(dir string, data []byte) (string, error) {
	tmp, err := newTempFile(dir, ".tmp-clis-")
	if err != nil {
		return "", fmt.Errorf("create temp file: %w", err)
	}
	if _, err := tmp.Write(data); err != nil {
		tmp.Close()           //nolint:errcheck // 失败清理路径
		os.Remove(tmp.Name()) //nolint:errcheck // 尽力清理
		return "", fmt.Errorf("write temp file: %w", err)
	}
	if err := tmp.Chmod(0o755); err != nil {
		tmp.Close()           //nolint:errcheck // 失败清理路径
		os.Remove(tmp.Name()) //nolint:errcheck // 尽力清理
		return "", fmt.Errorf("chmod temp file: %w", err)
	}
	if err := tmp.Close(); err != nil {
		return "", fmt.Errorf("close temp file: %w", err)
	}
	return tmp.Name(), nil
}

// InstallExecutable 将 data 原子写入 destPath，权限 0755。
//
// 步骤：确保目录存在 → 同目录写临时文件 → rename 覆盖目标（Windows 下先让位旧文件）。
// 失败时清理临时文件，目标文件保持原状（不被破坏）。
func InstallExecutable(data []byte, destPath string) error {
	dir := filepath.Dir(destPath)
	if err := os.MkdirAll(dir, 0o755); err != nil {
		return fmt.Errorf("create dir %s: %w", dir, err)
	}

	tmpName, err := stageFile(dir, data)
	if err != nil {
		return fmt.Errorf("stage executable: %w", err)
	}
	// 失败路径统一清理临时文件，不留垃圾；成功后置空避免误删。
	defer func() {
		if tmpName != "" {
			if rmErr := os.Remove(tmpName); rmErr != nil && !os.IsNotExist(rmErr) {
				slog.Debug("remove temp install file failed", "path", tmpName, "err", rmErr)
			}
		}
	}()

	if err := parkOldExecutable(destPath); err != nil {
		return err
	}
	if err := os.Rename(tmpName, destPath); err != nil {
		return fmt.Errorf("rename into place: %w", err)
	}
	tmpName = "" // 已成功改名，避免 defer 误删
	slog.Info("executable installed", "path", destPath, "bytes", len(data))
	return nil
}
