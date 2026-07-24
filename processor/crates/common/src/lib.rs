//! rustto-common: 工具（Tool）公共框架。
//!
//! 定义统一的 `Tool` 契约、产物 / 错误模型与 JSON 参数解析辅助，供所有工具实现复用。
//! 对应 AGENT.MD 中"功能切割、按模块名调用"的 AI-CLI 架构的抽象基座：新增工具只需
//! 在自己的 crate 内实现 `Tool`，再到 `rustto-tools` 的 `registry()` 注册一行即可。

use async_trait::async_trait;
use serde_json::Value;

/// 工具执行产物。
///
/// 字段与 daemon 回传的 `TaskResult` 一一对应，保持稳定（服务端依赖此结构）。
#[derive(Debug, Clone, serde::Serialize, PartialEq, Eq)]
pub struct ToolOutput {
    /// 产物绝对路径。
    pub file_path: String,
    /// 产物字节数。
    pub size: u64,
    /// 产物 sha256。
    pub checksum: String,
}

/// 统一的工具错误。
#[derive(Debug, thiserror::Error)]
pub enum ToolError {
    /// 文件 / 目录 IO 错误。
    #[error("io error: {0}")]
    Io(#[from] std::io::Error),
    /// tar / gzip 等归档错误。
    #[error("archive error: {0}")]
    Archive(String),
    /// 子进程执行失败（如 mysqldump）。
    #[error("subprocess failed (code={code}): {stderr}")]
    Subprocess { code: i32, stderr: String },
    /// 参数缺失或非法。
    #[error("invalid params: {0}")]
    Params(String),
    /// 其它工具内部错误。
    #[error("{0}")]
    Other(String),
}

/// 工具静态元信息，供 agent 发现 / 自省（`tool list`、`tool schema`）。
#[derive(Debug, Clone, serde::Serialize)]
pub struct ToolInfo {
    /// 工具名（服务端按此名调度，必须全局唯一且稳定）。
    pub name: &'static str,
    /// 一句话功能描述。
    pub description: &'static str,
    /// 参数 JSON Schema（手写，`serde_json::Value`）。
    pub params_schema: Value,
}

/// 工具契约：所有工具实现此 trait，由 `rustto-tools::registry()` 统一注册，
/// cli / daemon 统一调度。
#[async_trait]
pub trait Tool: Send + Sync {
    /// 返回工具元信息。
    fn info(&self) -> ToolInfo;
    /// 以 JSON 参数执行工具，返回产物元信息。
    async fn run(&self, args: &Value) -> Result<ToolOutput, ToolError>;
}

/// 从 JSON 参数中取出必填字符串字段；缺失或类型不符时返回带字段名的 `Params` 错误。
///
/// - `args`: 任务参数 JSON。
/// - `key`: 字段名。
pub fn require_str(args: &Value, key: &str) -> Result<String, ToolError> {
    args.get(key)
        .and_then(|v| v.as_str())
        .map(|s| s.to_string())
        .ok_or_else(|| ToolError::Params(format!("missing or invalid args.{key}")))
}

#[cfg(test)]
mod tests {
    //! 覆盖 require_str 解析与 Tool 的 dyn 派发。

    use super::*;

    /// 测试用的最小工具实现，用于验证 trait 对象调度。
    struct EchoTool;

    #[async_trait]
    impl Tool for EchoTool {
        fn info(&self) -> ToolInfo {
            ToolInfo {
                name: "echo",
                description: "测试用",
                params_schema: serde_json::json!({ "type": "object" }),
            }
        }
        async fn run(&self, args: &Value) -> Result<ToolOutput, ToolError> {
            let msg = require_str(args, "msg")?;
            Ok(ToolOutput {
                file_path: msg,
                size: 0,
                checksum: "0".into(),
            })
        }
    }

    #[test]
    fn require_str_present() {
        let v = serde_json::json!({ "path": "/data" });
        assert_eq!(require_str(&v, "path").unwrap(), "/data");
    }

    #[test]
    fn require_str_missing_returns_params_error() {
        let v = serde_json::json!({});
        let err = require_str(&v, "path").unwrap_err();
        assert!(matches!(err, ToolError::Params(_)));
    }

    #[test]
    fn require_str_wrong_type_returns_params_error() {
        let v = serde_json::json!({ "path": 123 });
        let err = require_str(&v, "path").unwrap_err();
        assert!(matches!(err, ToolError::Params(_)));
    }

    #[tokio::test]
    async fn tool_dyn_dispatch_runs() {
        let tool: Box<dyn Tool> = Box::new(EchoTool);
        assert_eq!(tool.info().name, "echo");
        let out = tool.run(&serde_json::json!({ "msg": "/x" })).await.unwrap();
        assert_eq!(out.file_path, "/x");
    }

    #[tokio::test]
    async fn tool_dyn_dispatch_propagates_error() {
        let tool: Box<dyn Tool> = Box::new(EchoTool);
        let err = tool.run(&serde_json::json!({})).await.unwrap_err();
        assert!(matches!(err, ToolError::Params(_)));
    }
}
