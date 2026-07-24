//! rustto-tools: 工具聚合与注册中心。
//!
//! 汇集所有内置工具，对外暴露统一的 `registry()` / `lookup()`，
//! 供 cli（`tool list`/`tool run`）与 daemon（按 `module` 名调度）使用。
//!
//! # 新增工具
//! 1. 在 `crates/<your-tool>/` 新建 crate，实现 `rustto_common::Tool`；
//! 2. 在本 crate 的 `Cargo.toml` 加依赖，在 `registry()` 里 `push` 一行；
//! 3. 在 workspace `Cargo.toml` 的 `members` 加上该 crate 目录。
//!
//! 其余代码（cli / daemon）无需改动 —— 这正是 agent 友好 CLI 的解耦点。

use rustto_backup_file::BackupFileTool;
use rustto_backup_mysql::BackupMysqlTool;
use rustto_common::Tool;
use std::sync::Arc;

/// 构造内置工具集合。
///
/// 返回 `Arc<dyn Tool>`：daemon 可在多任务间共享同一份无状态工具实例，
/// 避免每条任务重建。顺序即 `tool list` 的展示顺序。
pub fn registry() -> Vec<Arc<dyn Tool>> {
    vec![
        Arc::new(BackupFileTool) as Arc<dyn Tool>,
        Arc::new(BackupMysqlTool) as Arc<dyn Tool>,
    ]
}

/// 按工具名查找，返回共享实例。
pub fn lookup(name: &str) -> Option<Arc<dyn Tool>> {
    registry().into_iter().find(|t| t.info().name == name)
}

#[cfg(test)]
mod tests {
    //! 覆盖 registry 完整性、按名查找与未知工具。

    use super::*;

    #[test]
    fn registry_is_non_empty() {
        assert!(!registry().is_empty());
    }

    #[test]
    fn registry_names_are_unique() {
        let names: Vec<&str> = registry().iter().map(|t| t.info().name).collect();
        let mut sorted = names.clone();
        sorted.sort();
        sorted.dedup();
        assert_eq!(names.len(), sorted.len(), "duplicate tool names: {names:?}");
    }

    #[test]
    fn lookup_backup_file() {
        let t = lookup("backup_file").expect("backup_file registered");
        assert_eq!(t.info().name, "backup_file");
    }

    #[test]
    fn lookup_backup_mysql() {
        assert!(lookup("backup_mysql").is_some());
    }

    #[test]
    fn lookup_unknown_returns_none() {
        assert!(lookup("nope").is_none());
    }
}
