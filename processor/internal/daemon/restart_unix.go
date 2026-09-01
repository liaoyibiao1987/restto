//go:build !windows

package daemon

import (
	"os"
	"syscall"
)

// execSelf 以新二进制重启自身：syscall.Exec 原地替换进程映像（同 PID，
// 对 systemd / 容器编排透明）。Go 运行时打开的 fd 均带 O_CLOEXEC，
// 旧连接的 socket 不会泄漏到新进程。
func execSelf(exePath string) error {
	return syscall.Exec(exePath, os.Args, os.Environ())
}
