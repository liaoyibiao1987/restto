//! rustto-modules: 备份功能模块集合。
//!
//! 每个模块独立、可扩展，对应 AGENT.MD 中"功能切割、按模块名调用"的 AI-CLI 架构。
//! 由 `cli` crate 通过命令行参数 / 任务指令调度。

pub mod backup_file;
pub mod backup_mysql;

/// 模块执行结果。
#[derive(Debug, Clone, serde::Serialize)]
pub struct ModuleOutput {
    /// 产物绝对路径。
    pub file_path: String,
    /// 产物字节数。
    pub size: u64,
    /// 产物 sha256。
    pub checksum: String,
}

/// 统一的模块错误。
#[derive(Debug, thiserror::Error)]
pub enum ModuleError {
    #[error("io error: {0}")]
    Io(#[from] std::io::Error),
    #[error("tar/gzip error: {0}")]
    Archive(String),
    #[error("subprocess failed ({code}): {stderr}")]
    Subprocess { code: i32, stderr: String },
    #[error("invalid params: {0}")]
    Params(String),
}
