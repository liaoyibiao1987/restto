//! 文件 / 目录备份模块：tar + gzip 打包指定路径。

use crate::{ModuleError, ModuleOutput};
use flate2::write::GzEncoder;
use flate2::Compression;
use rustto_core::crypto::sha256_file;
use std::fs::File;
use std::path::Path;
use tar::Builder;

/// 将 `src` 目录（或单个文件）打包为 `dest_file`（.tar.gz）。
///
/// - `src`: 待备份的源路径。
/// - `dest_file`: 产物路径（建议以 `.tar.gz` 结尾）。
/// 返回产物路径、大小、sha256。
pub fn backup_path(src: &Path, dest_file: &Path) -> Result<ModuleOutput, ModuleError> {
    if !src.exists() {
        return Err(ModuleError::Params(format!(
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
            .map_err(|e| ModuleError::Archive(e.to_string()))?;
    } else {
        let mut f = File::open(src)?;
        builder
            .append_file(&arc_name, &mut f)
            .map_err(|e| ModuleError::Archive(e.to_string()))?;
    }
    builder
        .into_inner()
        .map_err(|e| ModuleError::Archive(e.to_string()))?
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
    fn packs_a_directory() {
        let tmp = std::env::temp_dir().join(format!("rbk-{}", std::process::id()));
        let src = tmp.join("src");
        std::fs::create_dir_all(&src).unwrap();
        std::fs::write(src.join("hello.txt"), b"hi").unwrap();
        let dest = tmp.join("out.tar.gz");
        let out = backup_path(&src, &dest).unwrap();
        assert!(dest.exists());
        assert!(out.size > 0);
        assert!(!out.checksum.is_empty());
        std::fs::remove_dir_all(&tmp).ok();
    }

    #[test]
    fn errors_when_source_missing() {
        let tmp = std::env::temp_dir().join(format!("rbk-missing-{}", std::process::id()));
        let dest = tmp.join("out.tar.gz");
        let err = backup_path(&tmp.join("nope"), &dest);
        assert!(matches!(err, Err(ModuleError::Params(_))));
    }
}
