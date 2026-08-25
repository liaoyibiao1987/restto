# internal/tools（工具注册中心）

把各工具包聚合为一个统一的注册表，供 `cmd/restto-client` 调度。

## 职责

- `Registry()` —— 返回所有内置工具（`[]common.Tool`，防御性副本）。
- `Lookup(name)` —— 按工具名查找（cli 的 `tool run`、daemon 的 `module` 调度都用它）。

它是 **agent 友好 CLI 的解耦点**：新增工具时，业务代码（cli / daemon）零改动。

## 当前已注册工具

| 工具名         | 包                     | 说明                         |
| -------------- | ---------------------- | ---------------------------- |
| `backup_file`  | internal/backupfile    | 文件 / 目录 tar.gz 备份      |
| `backup_mysql` | internal/backupmysql   | mysqldump 导出 + gzip 备份   |

## 新增一个工具（两步）

1. 新建包 `internal/<your-tool>/`，实现 [`common.Tool`](../common/) 接口：

   ```go
   type MyTool struct{}

   func (MyTool) Info() common.ToolInfo { /* name / description / params_schema */ }

   func (MyTool) Run(ctx context.Context, args json.RawMessage) (*common.ToolOutput, *common.ToolError) {
       // …
   }
   ```

2. 在本包 `tools.go` import 该包，并在 `Registry()` 里 append：

   ```go
   registry = []common.Tool{
       backupfile.Tool{},
       backupmysql.Tool{},
       mytool.MyTool{}, // 新增一行
   }
   ```

完成后 `tool list` / `tool schema my_tool` / `tool run my_tool` 立即可用，daemon 也能通过
`TASK_COMMAND.module = "my_tool"` 调度。每个工具包建议自带 `README.md`。

## 编译 / 测试

```bash
go build ./...
go test ./internal/tools/
```
