//go:build windows

// park_windows.go Windows 平台：不允许覆盖正在运行的 exe，
// 先把旧目标改名为 <dest>.old 让位（旧文件留给系统回收 / 人工清理）。
package upgrade

import (
	"fmt"
	"os"
)

// parkOldExecutable 目标已存在时先改名为 .old，否则空操作。
func parkOldExecutable(destPath string) error {
	if _, err := os.Stat(destPath); err != nil {
		return nil // 目标不存在，无需让位
	}
	if err := os.Rename(destPath, destPath+".old"); err != nil {
		return fmt.Errorf("park old executable: %w", err)
	}
	return nil
}
