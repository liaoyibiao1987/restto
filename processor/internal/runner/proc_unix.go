//go:build !windows

// proc_unix.go 类 Unix 平台的子进程组管理。
//
// CLI 往往还有自己的子进程（如 backupmysql 运行 mysqldump）；
// 仅杀直接子进程会留下孤儿进程且其继承的管道会阻塞 Wait。
// 因此：把 CLI 放入独立进程组，ctx 取消时对整个组发 SIGKILL，
// 并设置 WaitDelay 兜底（防御仍有描述符未关导致的 Wait 挂起）。
package runner

import (
	"os/exec"
	"syscall"
)

// procGroupWait 设置进程组与取消行为（Unix）。
func procGroupWait(cmd *exec.Cmd) {
	cmd.SysProcAttr = &syscall.SysProcAttr{Setpgid: true}
	cmd.Cancel = func() error {
		if cmd.Process == nil {
			return nil
		}
		// 负 PID = 进程组整体（CLI 及其派生的 mysqldump 等子进程）。
		return syscall.Kill(-cmd.Process.Pid, syscall.SIGKILL)
	}
}
