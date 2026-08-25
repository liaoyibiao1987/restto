// Package config 实现客户端配置：从 .env 与环境变量加载。
package config

import (
	"os"
	"strconv"
)

// ClientConfig 客户端运行配置。
type ClientConfig struct {
	// ManagerHost Manager 主机地址。
	ManagerHost string
	// ManagerPort Manager Netty 端口。
	ManagerPort uint16
	// NodeName 本节点名称（注册时上报）。
	NodeName string
	// NodeToken 节点 Token（注册时校验）。
	NodeToken string
	// Version 客户端版本号。
	Version string
	// LogDir 日志目录。
	LogDir string
	// DataDir 备份产物默认存放目录。
	DataDir string
	// HeartbeatIntervalSecs 心跳间隔（秒）。
	HeartbeatIntervalSecs uint64
}

// Default 返回默认配置。
func Default() ClientConfig {
	return ClientConfig{
		ManagerHost:           "127.0.0.1",
		ManagerPort:           9600,
		NodeName:              "default-node",
		NodeToken:             "",
		Version:               "0.1.0",
		LogDir:                "logs",
		DataDir:               "data",
		HeartbeatIntervalSecs: 15,
	}
}

// FromEnv 先加载 .env（若存在），再从环境变量读取配置，缺失项使用默认值。
func FromEnv() ClientConfig {
	LoadDotEnv(".env")
	cfg := Default()
	if v, ok := os.LookupEnv("MANAGER_HOST"); ok && v != "" {
		cfg.ManagerHost = v
	}
	if v, ok := os.LookupEnv("MANAGER_PORT"); ok {
		if port, err := strconv.ParseUint(v, 10, 16); err == nil {
			cfg.ManagerPort = uint16(port)
		}
	}
	if v, ok := os.LookupEnv("NODE_NAME"); ok && v != "" {
		cfg.NodeName = v
	}
	if v, ok := os.LookupEnv("NODE_TOKEN"); ok {
		cfg.NodeToken = v
	}
	if v, ok := os.LookupEnv("CLIENT_VERSION"); ok && v != "" {
		cfg.Version = v
	}
	if v, ok := os.LookupEnv("LOG_DIR"); ok && v != "" {
		cfg.LogDir = v
	}
	if v, ok := os.LookupEnv("DATA_DIR"); ok && v != "" {
		cfg.DataDir = v
	}
	if v, ok := os.LookupEnv("HEARTBEAT_INTERVAL_SECS"); ok {
		if n, err := strconv.ParseUint(v, 10, 64); err == nil {
			cfg.HeartbeatIntervalSecs = n
		}
	}
	return cfg
}

// LoadDotEnv 将 path 指向的 .env 文件按 KEY=VALUE 逐行加载进环境变量。
//
// 已存在的环境变量不覆盖；忽略空行与 # 注释；文件不存在时静默返回。
func LoadDotEnv(path string) {
	data, err := os.ReadFile(path)
	if err != nil {
		return
	}
	for _, line := range splitLines(string(data)) {
		line = trimSpaces(line)
		if line == "" || line[0] == '#' {
			continue
		}
		key, value, found := cut(line, "=")
		if !found {
			continue
		}
		key = trimSpaces(key)
		if key == "" {
			continue
		}
		value = trimQuotes(trimSpaces(value))
		if _, exists := os.LookupEnv(key); !exists {
			_ = os.Setenv(key, value)
		}
	}
}

// splitLines 按 \n 与 \r\n 切行。
func splitLines(s string) []string {
	var lines []string
	start := 0
	for i := 0; i < len(s); i++ {
		if s[i] == '\n' {
			line := s[start:i]
			if len(line) > 0 && line[len(line)-1] == '\r' {
				line = line[:len(line)-1]
			}
			lines = append(lines, line)
			start = i + 1
		}
	}
	if start < len(s) {
		lines = append(lines, s[start:])
	}
	return lines
}

// trimSpaces 去掉首尾空白（含 \t）。
func trimSpaces(s string) string {
	start, end := 0, len(s)
	for start < end && (s[start] == ' ' || s[start] == '\t') {
		start++
	}
	for end > start && (s[end-1] == ' ' || s[end-1] == '\t') {
		end--
	}
	return s[start:end]
}

// trimQuotes 去掉成对的首尾单/双引号。
func trimQuotes(s string) string {
	if len(s) >= 2 {
		first, last := s[0], s[len(s)-1]
		if (first == '"' && last == '"') || (first == '\'' && last == '\'') {
			return s[1 : len(s)-1]
		}
	}
	return s
}

// cut 等价 strings.Cut（避免在热路径引入额外抽象）。
func cut(s, sep string) (before, after string, found bool) {
	for i := 0; i+len(sep) <= len(s); i++ {
		if s[i:i+len(sep)] == sep {
			return s[:i], s[i+len(sep):], true
		}
	}
	return s, "", false
}
