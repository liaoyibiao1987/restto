//! rustto-core: 客户端核心工具类（受保护，对应 AGENT.MD 中 src/core 不可修改边界）。
//!
//! 提供：Netty 通信协议编解码、配置加载、按日期滚动日志、sha256/文件校验。

pub mod config;
pub mod crypto;
pub mod logger;
pub mod protocol;

pub use config::ClientConfig;
