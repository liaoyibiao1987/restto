//! 日志初始化：按日期滚动写入 `logs/`，同时输出到 stdout。
//!
//! 返回的 `WorkerGuard` 必须在程序生命周期内持有，否则后台刷新线程会丢失日志。

use std::path::Path;
use tracing_appender::non_blocking::WorkerGuard;
use tracing_subscriber::{fmt, layer::SubscriberExt, util::SubscriberInitExt, EnvFilter};

/// 初始化全局 tracing 订阅者。
///
/// - `log_dir`: 日志目录，按天滚动，文件名形如 `client.log.2026-07-18`。
/// 返回需持有的 `WorkerGuard`。
pub fn init(log_dir: &str) -> WorkerGuard {
    std::fs::create_dir_all(Path::new(log_dir)).ok();
    let file_appender = tracing_appender::rolling::daily(Path::new(log_dir), "client.log");
    let (non_blocking, guard) = tracing_appender::non_blocking(file_appender);
    let filter =
        EnvFilter::try_from_default_env().unwrap_or_else(|_| EnvFilter::new("info,rustto=debug"));

    tracing_subscriber::registry()
        .with(filter)
        .with(fmt::layer().with_writer(non_blocking).with_ansi(false))
        .with(fmt::layer().with_writer(std::io::stdout))
        .init();
    guard
}
