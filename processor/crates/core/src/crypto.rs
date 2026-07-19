//! 哈希与校验工具：sha256（字节 / 文件）。

use sha2::{Digest, Sha256};
use std::io::Read;
use std::path::Path;

/// 计算字节数组的 sha256 十六进制摘要。
pub fn sha256_hex(data: &[u8]) -> String {
    let mut hasher = Sha256::new();
    hasher.update(data);
    hex::encode(hasher.finalize())
}

/// 计算文件的 sha256 十六进制摘要（流式读取，适合大文件）。
pub fn sha256_file<P: AsRef<Path>>(path: P) -> std::io::Result<String> {
    let mut file = std::fs::File::open(path)?;
    let mut hasher = Sha256::new();
    let mut buf = [0u8; 8192];
    loop {
        let n = file.read(&mut buf)?;
        if n == 0 {
            break;
        }
        hasher.update(&buf[..n]);
    }
    Ok(hex::encode(hasher.finalize()))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn sha256_known_vector() {
        // sha256("abc") 的已知值。
        let got = sha256_hex(b"abc");
        assert_eq!(
            got,
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
        );
    }
}
