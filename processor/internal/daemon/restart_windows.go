//go:build windows

package daemon

import (
	"os"
	"os/exec"
)

// execSelf Windows 无 syscall.Exec：spawn 新进程后退出旧进程。
// 旧映像在替换阶段已被 park 成 <name>.old，新进程可正常启动。
func execSelf(exePath string) error {
	cmd := exec.Command(exePath, os.Args[1:]...)
	cmd.Stdout = os.Stdout
	cmd.Stderr = os.Stderr
	cmd.Stdin = os.Stdin
	if err := cmd.Start(); err != nil {
		return err
	}
	os.Exit(0)
	return nil
}
