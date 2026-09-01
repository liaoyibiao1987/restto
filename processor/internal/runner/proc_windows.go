//go:build windows

// proc_windows.go Windows 平台的子进程取消行为。
//
// Windows 无进程组信号语义，使用 exec.Cmd 默认取消（TerminateProcess 杀直接子进程），
// 依赖 WaitDelay 兜底回收其继承的管道句柄；CLI 自身的孙进程清理为尽力而为。
package runner

import "os/exec"

// procGroupWait Windows 下的取消策略：默认 Kill + WaitDelay（由调用方统一设置）。
func procGroupWait(*exec.Cmd) {}
