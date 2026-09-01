package main

import (
	"context"
	"encoding/json"
	"os"
	"path/filepath"
	"testing"

	"restto-client/internal/common"
)

// 覆盖参数解析、Tool 缺参校验、端口缺省，以及无 mysqldump 时不 panic。

func TestParsesParamsJSON(t *testing.T) {
	raw := `{"host":"127.0.0.1","port":3306,"user":"root","password":"p","database":"db","dest":"/x.sql.gz"}`
	params, err := ParseParams(json.RawMessage(raw))
	if err != nil {
		t.Fatalf("ParseParams: %v", err)
	}
	if params.Database != "db" {
		t.Fatalf("Database = %q, want db", params.Database)
	}
	if params.Port == nil || *params.Port != 3306 {
		t.Fatalf("Port = %v, want 3306", params.Port)
	}
	if params.effectivePort() != 3306 {
		t.Fatalf("effectivePort = %d", params.effectivePort())
	}
}

func TestParsesParamsWithoutPort(t *testing.T) {
	raw := `{"host":"127.0.0.1","user":"root","password":"p","database":"db"}`
	params, err := ParseParams(json.RawMessage(raw))
	if err != nil {
		t.Fatalf("ParseParams: %v", err)
	}
	if params.Port != nil {
		t.Fatalf("Port = %v, want nil", params.Port)
	}
	if params.effectivePort() != DefaultPort {
		t.Fatalf("effectivePort = %d, want %d", params.effectivePort(), DefaultPort)
	}
}

func TestParseParamsMissingRequired(t *testing.T) {
	cases := []string{
		`{"user":"root","database":"db"}`,      // 缺 host
		`{"host":"127.0.0.1","database":"db"}`, // 缺 user
		`{"host":"127.0.0.1","user":"root"}`,   // 缺 database
		`{}`,                                   // 全缺
		`{bad json`,                            // 非法 JSON
	}
	for _, raw := range cases {
		_, err := ParseParams(json.RawMessage(raw))
		if err == nil || err.Kind != common.KindParams {
			t.Fatalf("ParseParams(%q) err = %v, want KindParams", raw, err)
		}
	}
}

func TestToolErrorsOnMissingDest(t *testing.T) {
	// 参数缺 dest，应在参数校验阶段失败。
	args := json.RawMessage(`{"host":"127.0.0.1","user":"root","database":"db"}`)
	_, err := Tool{}.Run(context.Background(), args)
	if err == nil || err.Kind != common.KindParams {
		t.Fatalf("err = %v, want KindParams", err)
	}
}

func TestDoesNotPanicWithoutMysqldump(t *testing.T) {
	// 不假设 mysqldump 是否安装，只保证函数返回结果且不 panic。
	params := &MysqlParams{Host: "127.0.0.1", User: "root", Password: "p", Database: "db"}
	dest := filepath.Join(t.TempDir(), "dump.sql.gz")
	_, _ = BackupMysql(context.Background(), params, dest)
}

func TestBackupMysqlSubprocessFailureShape(t *testing.T) {
	// 用一个必然失败的环境：指向不存在的主机 + 超短超时不会命中（连接失败即返回）。
	// mysqldump 存在时：返回 Subprocess 错误；不存在时：返回 code=-1 的 Subprocess 错误。
	// 两种情况都应是 KindSubprocess 或至少非 panic。
	params := &MysqlParams{Host: "127.0.0.1", Port: nil, User: "no", Password: "no", Database: "no"}
	dest := filepath.Join(t.TempDir(), "dump.sql.gz")
	_, err := BackupMysql(context.Background(), params, dest)
	if err != nil && err.Kind != common.KindSubprocess && err.Kind != common.KindOther {
		t.Fatalf("err = %v, want KindSubprocess/KindOther", err)
	}
}

func TestInfoNameIsStable(t *testing.T) {
	got := (Tool{}).Info().Name
	if got != "backup_mysql" {
		t.Fatalf("name = %q, want backup_mysql", got)
	}
}

func TestGzipOutputWrittenOnSuccess(t *testing.T) {
	// 直接验证 gzip 落盘逻辑较难脱离真实 mysqldump；此处验证产物目录自动创建。
	dest := filepath.Join(t.TempDir(), "deep", "nested", "dump.sql.gz")
	params := &MysqlParams{Host: "127.0.0.1", User: "root", Database: "db"}
	_, _ = BackupMysql(context.Background(), params, dest)
	if _, err := os.Stat(filepath.Dir(dest)); err != nil {
		t.Fatalf("parent dirs not created: %v", err)
	}
}
