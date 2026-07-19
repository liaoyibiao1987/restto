//! MySQL 数据库备份模块：调用系统 `mysqldump` 导出，再 gzip 压缩落盘。
//!
//! 要求目标机已安装 `mysqldump` 并在 PATH 中可用。

use crate::{ModuleError, ModuleOutput};
use rustto_core::crypto::sha256_file;
use serde::Deserialize;
use std::io::Write;
use std::path::Path;
use tokio::process::Command;

/// MySQL 备份参数。
#[derive(Debug, Clone, Deserialize)]
pub struct MysqlParams {
    pub host: String,
    pub port: Option<u16>,
    pub user: String,
    pub password: String,
    pub database: String,
}

/// 执行一次 `mysqldump` 并把结果 gzip 压缩写入 `dest_file`。
///
/// - `params`: 连接参数（来自任务 args JSON）。
/// - `dest_file`: 产物路径（建议 `.sql.gz`）。
/// 返回产物路径、大小、sha256。
pub async fn backup_mysql(
    params: &MysqlParams,
    dest_file: &Path,
) -> Result<ModuleOutput, ModuleError> {
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
        .map_err(|e| ModuleError::Subprocess {
            code: -1,
            stderr: format!("failed to spawn mysqldump (is it installed?): {e}"),
        })?;

    if !output.status.success() {
        return Err(ModuleError::Subprocess {
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
        .map_err(|e| ModuleError::Archive(e.to_string()))?;
    encoder
        .finish()
        .map_err(|e| ModuleError::Archive(e.to_string()))?;

    let size = std::fs::metadata(dest_file)?.len();
    let checksum = sha256_file(dest_file)?;
    Ok(ModuleOutput {
        file_path: dest_file.to_string_lossy().to_string(),
        size,
        checksum,
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parses_params_json() {
        let json = r#"{"host":"127.0.0.1","port":3306,"user":"root","password":"p","database":"db"}"#;
        let p: MysqlParams = serde_json::from_str(json).unwrap();
        assert_eq!(p.database, "db");
        assert_eq!(p.port, Some(3306));
    }

    #[tokio::test]
    async fn does_not_panic_without_mysqldump() {
        // 不假设 mysqldump 是否安装，只保证函数不 panic、返回 Result。
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
}
