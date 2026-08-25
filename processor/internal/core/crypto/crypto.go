// Package crypto 提供哈希与校验工具：sha256（字节 / 文件）。
package crypto

import (
	"crypto/sha256"
	"encoding/hex"
	"io"
	"os"
)

// Sha256Hex 计算字节数组的 sha256 十六进制摘要。
func Sha256Hex(data []byte) string {
	sum := sha256.Sum256(data)
	return hex.EncodeToString(sum[:])
}

// Sha256File 计算文件的 sha256 十六进制摘要（流式读取，适合大文件）。
func Sha256File(path string) (string, error) {
	file, err := os.Open(path)
	if err != nil {
		return "", err
	}
	defer file.Close() //nolint:errcheck // 只读句柄，关闭失败无需上报

	hasher := sha256.New()
	buf := make([]byte, 64*1024)
	for {
		n, readErr := file.Read(buf)
		if n > 0 {
			hasher.Write(buf[:n]) // sha256 的 Write 永不返回错误
		}
		if readErr == io.EOF {
			break
		}
		if readErr != nil {
			return "", readErr
		}
	}
	return hex.EncodeToString(hasher.Sum(nil)), nil
}
