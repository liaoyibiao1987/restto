package tools

import "testing"

// 覆盖注册表完整性、按名查找与未知工具。

func TestRegistryIsNotEmpty(t *testing.T) {
	if len(Registry()) == 0 {
		t.Fatal("registry should not be empty")
	}
}

func TestRegistryNamesAreUnique(t *testing.T) {
	seen := map[string]bool{}
	for _, tool := range Registry() {
		name := tool.Info().Name
		if seen[name] {
			t.Fatalf("duplicate tool name: %s", name)
		}
		seen[name] = true
	}
}

func TestLookupBackupFile(t *testing.T) {
	tool := Lookup("backup_file")
	if tool == nil {
		t.Fatal("backup_file not registered")
	}
	if tool.Info().Name != "backup_file" {
		t.Fatalf("name = %q", tool.Info().Name)
	}
}

func TestLookupBackupMysql(t *testing.T) {
	if Lookup("backup_mysql") == nil {
		t.Fatal("backup_mysql not registered")
	}
}

func TestLookupUnknownReturnsNil(t *testing.T) {
	if Lookup("nope") != nil {
		t.Fatal("unknown tool should return nil")
	}
}

func TestRegistryCopyIsSafe(t *testing.T) {
	first := Registry()
	first[0] = nil // 篡改副本
	second := Registry()
	if second[0] == nil {
		t.Fatal("registry must return a defensive copy")
	}
}
