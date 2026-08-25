package daemon

import (
	"encoding/json"
	"testing"

	"rustto-client/internal/common"
)

// 覆盖产物→TaskResult 映射与失败结果构造。

func TestOutputMapsToSuccessResult(t *testing.T) {
	out := &common.ToolOutput{FilePath: "/a.tar.gz", Size: 5, Checksum: "ff"}
	r := outputToTaskResult(out, 7, "success")
	if r.TaskID != 7 {
		t.Fatalf("TaskID = %d", r.TaskID)
	}
	if r.Status != "success" {
		t.Fatalf("Status = %q", r.Status)
	}
	if r.FilePath == nil || *r.FilePath != "/a.tar.gz" {
		t.Fatalf("FilePath = %v", r.FilePath)
	}
	if r.Size == nil || *r.Size != 5 {
		t.Fatalf("Size = %v", r.Size)
	}
	if r.Checksum == nil || *r.Checksum != "ff" {
		t.Fatalf("Checksum = %v", r.Checksum)
	}
	if r.Error != nil {
		t.Fatalf("Error = %v, want nil", r.Error)
	}
}

func TestFailedResultShape(t *testing.T) {
	r := failedResult(9, "boom")
	if r.TaskID != 9 {
		t.Fatalf("TaskID = %d", r.TaskID)
	}
	if r.Status != "failed" {
		t.Fatalf("Status = %q", r.Status)
	}
	if r.Error == nil || *r.Error != "boom" {
		t.Fatalf("Error = %v", r.Error)
	}
	if r.FilePath != nil || r.Size != nil || r.Checksum != nil {
		t.Fatalf("success fields should be nil: %+v", r)
	}
}

func TestTaskResultJSONIsSnakeCase(t *testing.T) {
	// 与 Java Jackson SNAKE_CASE 对齐的关键字段名。
	r := failedResult(3, "x")
	data, err := json.Marshal(r)
	if err != nil {
		t.Fatalf("marshal: %v", err)
	}
	var m map[string]any
	if err := json.Unmarshal(data, &m); err != nil {
		t.Fatalf("unmarshal: %v", err)
	}
	for _, key := range []string{"task_id", "status", "error", "file_path", "size", "checksum"} {
		if _, ok := m[key]; !ok {
			t.Fatalf("json missing snake_case key %q: %s", key, data)
		}
	}
}

func TestRunModuleUnknown(t *testing.T) {
	out, errMsg := runModule(nil, "nope", nil)
	if out != nil {
		t.Fatal("out should be nil for unknown module")
	}
	if errMsg != "unknown module: nope" {
		t.Fatalf("errMsg = %q", errMsg)
	}
}

func TestMax64(t *testing.T) {
	if max64(3, 7) != 7 || max64(7, 3) != 7 || max64(0, 0) != 0 {
		t.Fatal("max64 broken")
	}
}
