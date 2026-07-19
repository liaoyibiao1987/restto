//! rustto-client: 客户端主二进制。
//!
//! 命令行架构（对应 AGENT.MD 的 AI-CLI 架构：按模块名调度）：
//! - `daemon`：常驻进程，连接 Manager，注册/心跳/接收任务并执行、接收二进制下发。
//! - `backup-file --path <p> --dest <d>`：单次文件/目录备份。
//! - `backup-mysql ...`：单次 MySQL 备份。
//! - `run-module <name> --args <json>`：通用模块调度（服务端按模块名调用）。

use anyhow::{Context, Result};
use clap::{Parser, Subcommand};
use rustto_core::protocol::{
    self, BinaryAck, BinaryPush, MessageType, RegisterAck, RegisterMessage, TaskCommand,
    TaskResult,
};
use rustto_core::{logger, ClientConfig};
use rustto_modules::backup_file;
use rustto_modules::backup_mysql::{self, MysqlParams};
use rustto_modules::ModuleOutput;
use base64::Engine;
use serde_json::Value;
use std::collections::HashMap;
use std::path::PathBuf;
use std::sync::Arc;
use std::time::{SystemTime, UNIX_EPOCH};
use tokio::net::TcpStream;
use tokio::sync::{mpsc, Mutex};
use tokio::time::Duration;

#[derive(Parser, Debug)]
#[command(name = "rustto-client", version, about = "rustto 分布式备份系统客户端")]
struct Cli {
    #[command(subcommand)]
    command: Command,
}

#[derive(Subcommand, Debug)]
enum Command {
    /// 常驻：连接 Manager，接收并执行备份任务。
    Daemon,
    /// 单次文件/目录备份。
    BackupFile {
        #[arg(long)]
        path: PathBuf,
        #[arg(long)]
        dest: PathBuf,
    },
    /// 单次 MySQL 数据库备份。
    BackupMysql {
        #[arg(long, default_value = "127.0.0.1")]
        host: String,
        #[arg(long, default_value_t = 3306)]
        port: u16,
        #[arg(long)]
        user: String,
        #[arg(long, default_value = "")]
        password: String,
        #[arg(long)]
        database: String,
        #[arg(long)]
        dest: PathBuf,
    },
    /// 通用模块调度：按模块名 + JSON 参数执行（供服务端按名调用）。
    RunModule {
        name: String,
        #[arg(long, default_value = "{}")]
        args: String,
    },
}

/// 现在的 Unix 秒。
fn now_secs() -> i64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|d| d.as_secs() as i64)
        .unwrap_or(0)
}

/// 根据模块名调度执行，返回产物元数据。
///
/// - `name`: 模块名（backup_file / backup_mysql）。
/// - `args`: 任务参数 JSON。
async fn dispatch_module(name: &str, args: &Value) -> std::result::Result<ModuleOutput, String> {
    match name {
        "backup_file" => {
            let path = args
                .get("path")
                .and_then(|v| v.as_str())
                .ok_or_else(|| "missing args.path".to_string())?;
            let dest = args
                .get("dest")
                .and_then(|v| v.as_str())
                .ok_or_else(|| "missing args.dest".to_string())?;
            backup_file::backup_path(PathBuf::from(path).as_path(), PathBuf::from(dest).as_path())
                .map_err(|e| e.to_string())
        }
        "backup_mysql" => {
            let params: MysqlParams = serde_json::from_value(args.clone())
                .map_err(|e| format!("invalid mysql params: {e}"))?;
            let dest = args
                .get("dest")
                .and_then(|v| v.as_str())
                .ok_or_else(|| "missing args.dest".to_string())?;
            let dest = PathBuf::from(dest);
            backup_mysql::backup_mysql(&params, &dest)
                .await
                .map_err(|e| e.to_string())
        }
        other => Err(format!("unknown module: {other}")),
    }
}

#[tokio::main]
async fn main() -> Result<()> {
    let cli = Cli::parse();
    let cfg = ClientConfig::from_env();
    let _guard = logger::init(&cfg.log_dir);

    match cli.command {
        Command::Daemon => run_daemon(cfg).await,
        Command::BackupFile { path, dest } => {
            let out = backup_file::backup_path(&path, &dest).context("backup-file failed")?;
            println!("{}", serde_json::to_string_pretty(&out)?);
            Ok(())
        }
        Command::BackupMysql {
            host,
            port,
            user,
            password,
            database,
            dest,
        } => {
            let params = MysqlParams {
                host,
                port: Some(port),
                user,
                password,
                database,
            };
            let out = backup_mysql::backup_mysql(&params, &dest)
                .await
                .context("backup-mysql failed")?;
            println!("{}", serde_json::to_string_pretty(&out)?);
            Ok(())
        }
        Command::RunModule { name, args } => {
            let args_value: Value = serde_json::from_str(&args).context("invalid --args json")?;
            let out = dispatch_module(&name, &args_value)
                .await
                .map_err(anyhow::Error::msg)?;
            println!("{}", serde_json::to_string_pretty(&out)?);
            Ok(())
        }
    }
}

/// daemon 主循环：断线自动重连。
async fn run_daemon(cfg: ClientConfig) -> Result<()> {
    tracing::info!(
        "starting daemon, manager={}:{}, node={}",
        cfg.manager_host,
        cfg.manager_port,
        cfg.node_name
    );
    loop {
        if let Err(e) = connect_and_serve(&cfg).await {
            tracing::error!("session ended with error: {e:#}");
        }
        tracing::info!("reconnecting in 5s...");
        tokio::time::sleep(Duration::from_secs(5)).await;
    }
}

/// 建立一次会话：注册 → 心跳 → 接收并执行任务，直到连接断开。
async fn connect_and_serve(cfg: &ClientConfig) -> Result<()> {
    let stream = TcpStream::connect((cfg.manager_host.as_str(), cfg.manager_port))
        .await
        .context("connect manager failed")?;
    tracing::info!("connected to manager");

    let (read_half, write_half) = stream.into_split();
    let (frame_tx, mut frame_rx) = mpsc::channel::<bytes::Bytes>(128);

    // 写出协程：从 channel 取帧写入 socket。
    tokio::spawn(async move {
        let mut write_half = write_half;
        while let Some(frame) = frame_rx.recv().await {
            if tokio::io::AsyncWriteExt::write_all(&mut write_half, &frame)
                .await
                .is_err()
            {
                break;
            }
            let _ = tokio::io::AsyncWriteExt::flush(&mut write_half).await;
        }
    });

    // 发送注册。
    let register = RegisterMessage {
        node_name: cfg.node_name.clone(),
        node_token: cfg.node_token.clone(),
        version: cfg.version.clone(),
    };
    send_frame(&frame_tx, MessageType::Register, &register).await?;

    // 心跳协程。
    let hb_tx = frame_tx.clone();
    let hb_interval = cfg.heartbeat_interval_secs;
    tokio::spawn(async move {
        let mut ticker = tokio::time::interval(Duration::from_secs(hb_interval.max(1)));
        ticker.tick().await; // 跳过立即触发
        loop {
            ticker.tick().await;
            let hb = protocol::Heartbeat { timestamp: now_secs() };
            let frame = match protocol::encode(MessageType::Heartbeat, &hb) {
                Ok(f) => f,
                Err(_) => continue,
            };
            if hb_tx.send(frame).await.is_err() {
                break;
            }
        }
    });

    // 二进制分片累积缓存：<version, bytes>。
    let binary_cache: Arc<Mutex<HashMap<String, Vec<u8>>>> = Arc::new(Mutex::new(HashMap::new()));
    let data_dir = PathBuf::from(&cfg.data_dir);

    // 主读循环。
    let mut reader = read_half;
    loop {
        let (msg_type, payload) = match protocol::read_frame(&mut reader).await {
            Ok(v) => v,
            Err(e) => return Err(anyhow::anyhow!("read frame failed: {e}")),
        };
        match msg_type {
            MessageType::RegisterAck => {
                let ack: RegisterAck = serde_json::from_value(payload).unwrap_or(RegisterAck {
                    success: false,
                    message: "invalid ack".into(),
                    node_id: None,
                });
                if ack.success {
                    tracing::info!("registered ok: {}", ack.message);
                } else {
                    tracing::error!("register rejected: {}", ack.message);
                    return Err(anyhow::anyhow!("register rejected"));
                }
            }
            MessageType::Heartbeat => {
                tracing::trace!("heartbeat from manager");
            }
            MessageType::TaskCommand => {
                let cmd: TaskCommand = match serde_json::from_value(payload) {
                    Ok(c) => c,
                    Err(e) => {
                        tracing::warn!("bad TaskCommand payload: {e}");
                        continue;
                    }
                };
                let tx = frame_tx.clone();
                let cache_dir = data_dir.clone();
                tokio::spawn(async move {
                    handle_task_command(cmd, tx, cache_dir).await;
                });
            }
            MessageType::BinaryPush => {
                let push: BinaryPush = match serde_json::from_value(payload) {
                    Ok(p) => p,
                    Err(e) => {
                        tracing::warn!("bad BinaryPush payload: {e}");
                        continue;
                    }
                };
                let tx = frame_tx.clone();
                let cache = Arc::clone(&binary_cache);
                let dir = data_dir.clone();
                tokio::spawn(async move {
                    handle_binary_push(push, tx, cache, dir).await;
                });
            }
            other => {
                tracing::warn!("ignoring unexpected message type: {:?}", other);
            }
        }
    }
}

/// 编码并以帧形式投递到写出 channel。
async fn send_frame<T: serde::Serialize>(
    tx: &mpsc::Sender<bytes::Bytes>,
    message_type: MessageType,
    payload: &T,
) -> Result<()> {
    let frame = protocol::encode(message_type, payload).context("encode frame failed")?;
    tx.send(frame)
        .await
        .map_err(|_| anyhow::anyhow!("writer channel closed"))?;
    Ok(())
}

/// 执行单个任务指令并回传 TaskResult。
async fn handle_task_command(
    cmd: TaskCommand,
    tx: mpsc::Sender<bytes::Bytes>,
    _data_dir: PathBuf,
) {
    tracing::info!("task #{} module={} executing", cmd.task_id, cmd.module);
    let result = match dispatch_module(&cmd.module, &cmd.args).await {
        Ok(ModuleOutput {
            file_path,
            size,
            checksum,
        }) => TaskResult {
            task_id: cmd.task_id,
            status: "success".into(),
            file_path: Some(file_path),
            size: Some(size),
            checksum: Some(checksum),
            error: None,
        },
        Err(e) => {
            tracing::error!("task #{} failed: {e}", cmd.task_id);
            TaskResult {
                task_id: cmd.task_id,
                status: "failed".into(),
                file_path: None,
                size: None,
                checksum: None,
                error: Some(e),
            }
        }
    };
    if let Err(e) = send_frame(&tx, MessageType::TaskResult, &result).await {
        tracing::error!("failed to report task result: {e}");
    }
}

/// 累积二进制分片，收齐后校验 sha256 并落盘，回传 BinaryAck。
async fn handle_binary_push(
    push: BinaryPush,
    tx: mpsc::Sender<bytes::Bytes>,
    cache: Arc<Mutex<HashMap<String, Vec<u8>>>>,
    data_dir: PathBuf,
) {
    let chunk = match base64::engine::general_purpose::STANDARD.decode(&push.data) {
        Ok(c) => c,
        Err(e) => {
            tracing::warn!("binary chunk decode failed: {e}");
            return;
        }
    };
    let mut map = cache.lock().await;
    let buf = map.entry(push.version.clone()).or_default();
    buf.extend_from_slice(&chunk);
    if push.chunk_index + 1 < push.total_chunks {
        return; // 还有后续分片。
    }
    let bytes = map.remove(&push.version).unwrap_or_default();
    drop(map);

    let actual = rustto_core::crypto::sha256_hex(&bytes);
    let ok = actual == push.checksum;
    if ok {
        std::fs::create_dir_all(&data_dir).ok();
        let dest = data_dir.join(format!("rustto-client-{}.bin", push.version));
        if let Err(e) = std::fs::write(&dest, &bytes) {
            tracing::error!("write received binary failed: {e}");
        } else {
            tracing::info!(
                "received new binary v{} -> {} ({} bytes)",
                push.version,
                dest.display(),
                bytes.len()
            );
        }
    } else {
        tracing::error!(
            "binary v{} checksum mismatch (expected {}, got {})",
            push.version,
            push.checksum,
            actual
        );
    }
    let ack = BinaryAck {
        version: push.version,
        success: ok,
        error: if ok { None } else { Some("checksum mismatch".into()) },
    };
    let _ = send_frame(&tx, MessageType::BinaryAck, &ack).await;
}
