//go:build !windows

// park_unix.go 类 Unix 平台：rename 可直接覆盖正在运行的可执行文件（换新 inode），无需让位。
package upgrade

// parkOldExecutable Unix 下为空操作。
func parkOldExecutable(string) error { return nil }
