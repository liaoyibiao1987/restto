package config

import (
	"os"
	"path/filepath"
	"testing"
)

// 覆盖默认值、.env 加载、环境变量覆盖与优先级。

func TestDefaultValues(t *testing.T) {
	cfg := Default()
	if cfg.ManagerPort != 9600 {
		t.Fatalf("ManagerPort = %d, want 9600", cfg.ManagerPort)
	}
	if cfg.HeartbeatIntervalSecs != 15 {
		t.Fatalf("HeartbeatIntervalSecs = %d, want 15", cfg.HeartbeatIntervalSecs)
	}
}

func TestLoadDotEnvParsesFile(t *testing.T) {
	dir := t.TempDir()
	envFile := filepath.Join(dir, ".env")
	content := "# 注释\n" +
		"MANAGER_HOST=10.0.0.1\n" +
		"NODE_NAME='quoted-node'\n" +
		"CLIENT_VERSION=\"1.2.3\"\n" +
		"\n" +
		"BADLINE\n"
	if err := os.WriteFile(envFile, []byte(content), 0o600); err != nil {
		t.Fatalf("write env: %v", err)
	}

	t.Setenv("MANAGER_HOST", "") // 清空后 LookupEnv 仍存在，验证“存在即不覆盖”
	LoadDotEnv(envFile)

	// 已存在的环境变量（即便为空）不被覆盖。
	if got := os.Getenv("MANAGER_HOST"); got != "" {
		t.Fatalf("existing env should not be overwritten, got %q", got)
	}
	if got := os.Getenv("NODE_NAME"); got != "quoted-node" {
		t.Fatalf("NODE_NAME = %q, want quoted-node", got)
	}
	if got := os.Getenv("CLIENT_VERSION"); got != "1.2.3" {
		t.Fatalf("CLIENT_VERSION = %q, want 1.2.3", got)
	}
}

func TestLoadDotEnvMissingFileIsSilent(t *testing.T) {
	LoadDotEnv(filepath.Join(t.TempDir(), "nope", ".env"))
}

func TestFromEnvOverridesDefaults(t *testing.T) {
	t.Setenv("MANAGER_HOST", "192.168.1.10")
	t.Setenv("MANAGER_PORT", "9700")
	t.Setenv("NODE_NAME", "node-a")
	t.Setenv("NODE_TOKEN", "tok")
	t.Setenv("HEARTBEAT_INTERVAL_SECS", "30")

	cfg := FromEnv()
	if cfg.ManagerHost != "192.168.1.10" {
		t.Fatalf("ManagerHost = %q", cfg.ManagerHost)
	}
	if cfg.ManagerPort != 9700 {
		t.Fatalf("ManagerPort = %d, want 9700", cfg.ManagerPort)
	}
	if cfg.NodeName != "node-a" || cfg.NodeToken != "tok" {
		t.Fatalf("node identity mismatch: %+v", cfg)
	}
	if cfg.HeartbeatIntervalSecs != 30 {
		t.Fatalf("HeartbeatIntervalSecs = %d, want 30", cfg.HeartbeatIntervalSecs)
	}
}

func TestFromEnvInvalidPortFallsBack(t *testing.T) {
	t.Setenv("MANAGER_PORT", "not-a-port")
	cfg := FromEnv()
	if cfg.ManagerPort != 9600 {
		t.Fatalf("ManagerPort = %d, want fallback 9600", cfg.ManagerPort)
	}
}
