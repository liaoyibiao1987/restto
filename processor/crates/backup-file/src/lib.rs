//! backup_file 工具：tar + gzip 打包文件 / 目录为 `.tar.gz` 备份产物。
//!
//! 实现 `rustto_common::Tool`，工具名 `backup_file`（与服务端下发的 module 名一致）。
//! 低层 [`backup_path`] 为纯函数，便于单独复用与单测。

use async_trait::async_trait;
use flate2::write::GzEncoder;
use flate2::Compression;
use rustto_common::{require_str, Tool, ToolError, ToolInfo, ToolOutput};
use rustto_core::crypto::sha256_file;
use serde_json::{json, Value};
use std::fs::File;
use std::path::{Path, PathBuf};
use tar::Builder;

/// 工具名（保持稳定，服务端按此名调度）。
pub const NAME: &str = "backup_file";

/// 工具入口结构体。
pub struct BackupFileTool;

/// 将 `src`（目录或单个文件）打包为 `dest_file`（`.tar.gz`），返回产物元信息。
///
/// - `src`: 待备份的源路径。
/// - `dest_file`: 产物路径（建议以 `.tar.gz` 结尾）。
pub fn backup_path(src: &Path, dest_file: &Path) -> Result<ToolOutput, ToolError> {
    if !src.exists() {
        return Err(ToolError::Params(format!(
            "source path not found: {}",
            src.display()
        )));
    }
    if let Some(parent) = dest_file.parent() {
        std::fs::create_dir_all(parent)?;
    }

    let tar_gz = File::create(dest_file)?;
    let encoder = GzEncoder::new(tar_gz, Compression::default());
    let mut builder = Builder::new(encoder);
    let arc_name = src
        .file_name()
        .map(|n| n.to_string_lossy().to_string())
        .unwrap_or_else(|| ".".into());
    if src.is_dir() {
        builder
            .append_dir_all(&arc_name, src)
            .map_err(|e| ToolError::Archive(e.to_string()))?;
    } else {
        let mut f = File::open(src)?;
        builder
            .append_file(&arc_name, &mut f)
            .map_err(|e| ToolError::Archive(e.to_string()))?;
    }
    builder
        .into_inner()
        .map_err(|e| ToolError::Archive(e.to_string()))?
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
impl Tool for BackupFileTool {
    fn info(&self) -> ToolInfo {
        ToolInfo {
            name: NAME,
            description: "将指定文件或目录打包为 .tar.gz 备份产物。",
            params_schema: json!({
                "type": "object",
                "required": ["path", "dest"],
                "properties": {
                    "path": {
                        "type": "string",
                        "description": "待备份的源文件 / 目录绝对路径"
                    },
                    "dest": {
                        "type": "string",
                        "description": "产物路径，建议以 .tar.gz 结尾"
                    }
                }
            }),
        }
    }

    async fn run(&self, args: &Value) -> Result<ToolOutput, ToolError> {
        let path = PathBuf::from(require_str(args, "path")?);
        let dest = PathBuf::from(require_str(args, "dest")?);
        // tar/gzip 为同步 IO，放到 spawn_blocking 避免阻塞异步 runtime。
        tokio::task::spawn_blocking(move || backup_path(&path, &dest))
            .await
            .map_err(|e| ToolError::Other(format!("blocking join failed: {e}")))?
    }
}

#[cfg(test)]
mod tests {
    //! 覆盖打包成功、源缺失、Tool 契约调度与缺参校验。

    use super::*;

    fn tmp(suffix: &str) -> PathBuf {
        std::env::temp_dir().join(format!("rbk-file-{}-{suffix}", std::process::id()))
    }

    #[test]
    fn packs_a_directory() {
        let root = tmp("src");
        let src = root.join("src");
        std::fs::create_dir_all(&src).unwrap();
        std::fs::write(src.join("hello.txt"), b"hi").unwrap();
        let dest = root.join("out.tar.gz");

        let out = backup_path(&src, &dest).unwrap();
        assert!(dest.exists());
        assert!(out.size > 0);
        assert!(!out.checksum.is_empty());

        std::fs::remove_dir_all(&root).ok();
    }

    #[test]
    fn packs_a_single_file() {
        let root = tmp("single");
        std::fs::create_dir_all(&root).unwrap();
        let src = root.join("note.txt");
        std::fs::write(&src, b"hello").unwrap();
        let dest = root.join("out.tar.gz");

        let out = backup_path(&src, &dest).unwrap();
        assert_eq!(out.file_path, dest.to_string_lossy());
        assert!(dest.exists());

        std::fs::remove_dir_all(&root).ok();
    }

    #[test]
    fn errors_when_source_missing() {
        let root = tmp("missing");
        let dest = root.join("out.tar.gz");
        let err = backup_path(&root.join("nope"), &dest).unwrap_err();
        assert!(matches!(err, ToolError::Params(_)));
    }

    #[tokio::test]
    async fn tool_runs_with_valid_args() {
        let root = tmp("tool-ok");
        let src = root.join("src");
        std::fs::create_dir_all(&src).unwrap();
        std::fs::write(src.join("a.txt"), b"a").unwrap();
        let dest = root.join("out.tar.gz");

        let args = json!({ "path": src, "dest": dest });
        let out = BackupFileTool.run(&args).await.unwrap();
        assert!(out.size > 0);

        std::fs::remove_dir_all(&root).ok();
    }

    #[tokio::test]
    async fn tool_errors_on_missing_arg() {
        let args = json!({ "path": "/tmp/whatever" }); // 缺 dest
        let err = BackupFileTool.run(&args).await.unwrap_err();
        assert!(matches!(err, ToolError::Params(_)));
    }

    #[test]
    fn info_name_is_stable() {
        assert_eq!(BackupFileTool.info().name, "backup_file");
    }
}
