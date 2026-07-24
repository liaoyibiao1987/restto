# rustto-tools（工具注册中心）

把各工具 crate 聚合为一个统一的注册表，供 `rustto-cli` 调度。

## 职责

- `registry()` —— 返回所有内置工具（`Arc<dyn Tool>`）。
- `lookup(name)` —— 按工具名查找（cli 的 `tool run`、daemon 的 `module` 调度都用它）。

它是 **agent 友好 CLI 的解耦点**：新增工具时，业务代码（cli / daemon）零改动。

## 当前已注册工具

| 工具名         | crate                   | 说明                         |
| -------------- | ----------------------- | ---------------------------- |
| `backup_file`  | rustto-backup-file      | 文件 / 目录 tar.gz 备份      |
| `backup_mysql` | rustto-backup-mysql     | mysqldump 导出 + gzip 备份   |

## 新增一个工具（三步）

1. 新建 crate，实现 [`rustto_common::Tool`](../common/src/lib.rs)：

   ```rust
   #[async_trait]
   impl Tool for MyTool {
       fn info(&self) -> ToolInfo { /* name / description / params_schema */ }
       async fn run(&self, args: &Value) -> Result<ToolOutput, ToolError> { /* … */ }
   }
   ```

2. 在本 crate 的 `Cargo.toml` 加依赖，并在 `registry()` 里 `push`：

   ```rust
   vec![ /* …, */ Arc::new(MyTool) as Arc<dyn Tool> ]
   ```

3. 在 workspace `Cargo.toml` 的 `members` 追加新 crate 目录。

完成后 `tool list` / `tool schema my_tool` / `tool run my_tool` 立即可用，daemon 也能通过
`TASK_COMMAND.module = "my_tool"` 调度。每个工具 crate 建议自带 `README.md` 与 `build.sh`。

## 编译 / 测试

```bash
cargo build -p rustto-tools
cargo test  -p rustto-tools
```
