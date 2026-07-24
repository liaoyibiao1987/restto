//! backup_mysql 工具：调用系统 `mysqldump` 导出数据库，再 gzip 压缩为 `.sql.gz` 产物。
//!
//! 实现 `rustto_common::Tool`，工具名 `backup_mysql`（与服务端下发的 module 名一致）。
//! 要求目标机已安装 `mysqldump` 并在 PATH 中可用。

use async_trait::async_trait;
use rustto_common::{Tool, ToolError, ToolInfo, ToolOutput};
use rustto_core::crypto::sha256_file;
use serde::Deserialize;
use serde_json::{json, Value};
use std::io::Write;
use std::path::Path;
use tokio::process::Command;

/// 工具名（保持稳定，服务端按此名调度）。
pub const NAME: &str = "backup_mysql";

/// 工具入口结构体。
pub struct BackupMysqlTool;

/// MySQL 备份连接参数（来自任务 args JSON）。
#[derive(Debug, Clone, Deserialize)]
pub struct MysqlParams {
    /// 数据库主机。
    pub host: String,
    /// 端口，缺省 3306。
    pub port: Option<u16>,
    /// 账号。
    pub user: String,
    /// 密码（明文，经任务参数下发，传输层由 Netty 协议保障）。
    pub password: String,
    /// 目标数据库名。
    pub database: String,
}

/// 执行一次 `mysqldump`，把结果 gzip 压缩写入 `dest_file`，返回产物元信息。
///
/// - `params`: 连接参数。
/// - `dest_file`: 产物路径（建议 `.sql.gz`）。
pub async fn backup_mysql(params: &MysqlParams, dest_file: &Path) -> Result<ToolOutput, ToolError> {
    if let Some(parent) = dest_file.parent() {
        std::fs::create_dir_all(parent)?;
    }

    let port = params.port.unwrap_or(3306).to_string();
    let output = Command::new("mysqldump")
        .args([
            "-h",
            &params.host,
            "-P",
            &port,
            "-u",
            &params.user,
            &format!("--password={}", params.password),
            "--single-transaction",
            "--quick",
            &params.database,
        ])
        .output()
        .await
        .map_err(|e| ToolError::Subprocess {
            code: -1,
            stderr: format!("failed to spawn mysqldump (is it installed?): {e}"),
        })?;

    if !output.status.success() {
        return Err(ToolError::Subprocess {
            code: output.status.code().unwrap_or(-1),
            stderr: String::from_utf8_lossy(&output.stderr).to_string(),
        });
    }

    // gzip 压缩 dump 输出。
    let dump = output.stdout;
    let mut encoder = flate2::write::GzEncoder::new(
        std::fs::File::create(dest_file)?,
        flate2::Compression::default(),
    );
    encoder
        .write_all(&dump)
        .map_err(|e| ToolError::Archive(e.to_string()))?;
    encoder
        .finish()
        .map_err(|e| ToolError::Archive(e.to_string()))?;

    let size = std::fs::metadata(dest_file)?.len();
    let checksum = sha256_file(dest_file)?;
    Ok(ToolOutput {
        file_path: dest_file.to_string_lossy().to_string(),
        size,
        checksum,
    })
}

#[async_trait]
impl Tool for BackupMysqlTool {
    fn info(&self) -> ToolInfo {
        ToolInfo {
            name: NAME,
            description: "通过 mysqldump 导出数据库并 gzip 压缩为 .sql.gz 产物。",
            params_schema: json!({
                "type": "object",
                "required": ["host", "user", "database", "dest"],
                "properties": {
                    "host":     { "type": "string", "description": "数据库主机",   "default": "127.0.0.1" },
                    "port":     { "type": "integer","description": "端口",         "default": 3306 },
                    "user":     { "type": "string", "description": "账号" },
                    "password": { "type": "string", "description": "密码（明文）", "default": "" },
                    "database": { "type": "string", "description": "目标数据库名" },
                    "dest":     { "type": "string", "description": "产物路径，建议 .sql.gz 结尾" }
                }
            }),
        }
    }

    async fn run(&self, args: &Value) -> Result<ToolOutput, ToolError> {
        let params: MysqlParams = serde_json::from_value(args.clone())
            .map_err(|e| ToolError::Params(format!("invalid mysql params: {e}")))?;
        let dest = args
            .get("dest")
            .and_then(|v| v.as_str())
            .ok_or_else(|| ToolError::Params("missing args.dest".into()))?;
        backup_mysql(&params, Path::new(dest)).await
    }
}

#[cfg(test)]
mod tests {
    //! 覆盖参数反序列化、Tool 缺参校验，以及无 mysqldump 时不 panic。

    use super::*;

    #[test]
    fn parses_params_json() {
        let json = r#"{"host":"127.0.0.1","port":3306,"user":"root","password":"p","database":"db","dest":"/x.sql.gz"}"#;
        let p: MysqlParams = serde_json::from_str(json).unwrap();
        assert_eq!(p.database, "db");
        assert_eq!(p.port, Some(3306));
    }

    #[test]
    fn parses_params_without_port() {
        let json = r#"{"host":"127.0.0.1","user":"root","password":"p","database":"db","dest":"/x.sql.gz"}"#;
        let p: MysqlParams = serde_json::from_str(json).unwrap();
        assert_eq!(p.port, None);
    }

    #[tokio::test]
    async fn tool_errors_on_missing_dest() {
        // 参数缺 dest，应在反序列化阶段失败（Params）。
        let args = json!({ "host": "127.0.0.1", "user": "root", "database": "db" });
        let err = BackupMysqlTool.run(&args).await.unwrap_err();
        assert!(matches!(err, ToolError::Params(_)));
    }

    #[tokio::test]
    async fn does_not_panic_without_mysqldump() {
        // 不假设 mysqldump 是否安装，只保证函数返回 Result 且不 panic。
        let params = MysqlParams {
            host: "127.0.0.1".into(),
            port: Some(3306),
            user: "root".into(),
            password: "p".into(),
            database: "db".into(),
        };
        let dest = std::env::temp_dir().join(format!("rbk-mysql-{}.sql.gz", std::process::id()));
        let _ = backup_mysql(&params, &dest).await;
    }

    #[test]
    fn info_name_is_stable() {
        assert_eq!(BackupMysqlTool.info().name, "backup_mysql");
    }
}
