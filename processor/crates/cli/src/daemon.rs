//! 常驻 daemon：连接 Manager，注册 / 心跳 / 接收并执行任务 / 接收二进制下发。
//!
//! 任务调度经 [`rustto_tools::lookup`] 按 `module` 名解析为对应 `Tool` 执行，
//! 与 cli 的 `tool run` 共用同一套工具实现 —— 高内聚、行为一致。

use anyhow::{Context, Result};
use base64::Engine;
use rustto_common::ToolOutput;
use rustto_core::protocol::{
    self, BinaryAck, BinaryPush, MessageType, RegisterAck, RegisterMessage, TaskCommand, TaskResult,
};
use rustto_core::ClientConfig;
use rustto_tools as tools;
use std::collections::HashMap;
use std::path::PathBuf;
use std::sync::Arc;
use std::time::{SystemTime, UNIX_EPOCH};
use tokio::net::TcpStream;
use tokio::sync::{mpsc, Mutex};
use tokio::time::Duration;

/// daemon 入口：断线 5s 自动重连。
pub async fn run(cfg: ClientConfig) -> Result<()> {
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
    let (frame_tx, frame_rx) = mpsc::channel::<bytes::Bytes>(128);

    // 写出协程：从 channel 取帧写入 socket。
    spawn_writer(write_half, frame_rx);

    // 注册。
    let register = RegisterMessage {
        node_name: cfg.node_name.clone(),
        node_token: cfg.node_token.clone(),
        version: cfg.version.clone(),
    };
    send_frame(&frame_tx, MessageType::Register, &register).await?;

    // 心跳协程。
    spawn_heartbeat(frame_tx.clone(), cfg.heartbeat_interval_secs);

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
            MessageType::RegisterAck => handle_register_ack(payload)?,
            MessageType::Heartbeat => tracing::trace!("heartbeat from manager"),
            MessageType::TaskCommand => {
                if let Some(cmd) = parse_payload::<TaskCommand>(payload) {
                    tokio::spawn(handle_task_command(cmd, frame_tx.clone()));
                }
            }
            MessageType::BinaryPush => {
                if let Some(push) = parse_payload::<BinaryPush>(payload) {
                    tokio::spawn(handle_binary_push(
                        push,
                        frame_tx.clone(),
                        Arc::clone(&binary_cache),
                        data_dir.clone(),
                    ));
                }
            }
            other => tracing::warn!("ignoring unexpected message type: {:?}", other),
        }
    }
}

/// 启动写出协程。
fn spawn_writer(
    mut write_half: tokio::net::tcp::OwnedWriteHalf,
    mut frame_rx: mpsc::Receiver<bytes::Bytes>,
) {
    tokio::spawn(async move {
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
}

/// 启动周期心跳协程。
fn spawn_heartbeat(hb_tx: mpsc::Sender<bytes::Bytes>, interval_secs: u64) {
    tokio::spawn(async move {
        let mut ticker = tokio::time::interval(Duration::from_secs(interval_secs.max(1)));
        ticker.tick().await; // 跳过立即触发
        loop {
            ticker.tick().await;
            let hb = protocol::Heartbeat {
                timestamp: now_secs(),
            };
            let frame = match protocol::encode(MessageType::Heartbeat, &hb) {
                Ok(f) => f,
                Err(_) => continue,
            };
            if hb_tx.send(frame).await.is_err() {
                break;
            }
        }
    });
}

/// 处理注册应答：失败则终止当前会话。
fn handle_register_ack(payload: serde_json::Value) -> Result<()> {
    let ack: RegisterAck = serde_json::from_value(payload).unwrap_or(RegisterAck {
        success: false,
        message: "invalid ack".into(),
        node_id: None,
    });
    if ack.success {
        tracing::info!("registered ok: {}", ack.message);
        Ok(())
    } else {
        tracing::error!("register rejected: {}", ack.message);
        Err(anyhow::anyhow!("register rejected"))
    }
}

/// 反序列化一帧 payload；失败时记 warn 并返回 None（主循环继续）。
fn parse_payload<T: serde::de::DeserializeOwned>(payload: serde_json::Value) -> Option<T> {
    match serde_json::from_value(payload) {
        Ok(v) => Some(v),
        Err(e) => {
            tracing::warn!("bad payload: {e}");
            None
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

/// 执行单个任务指令并回传 TaskResult（按 module 名调度到注册的工具）。
async fn handle_task_command(cmd: TaskCommand, tx: mpsc::Sender<bytes::Bytes>) {
    tracing::info!("task #{} module={} executing", cmd.task_id, cmd.module);
    let result = run_module(&cmd.module, &cmd.args)
        .await
        .map(|out| out.to_task_result(cmd.task_id, "success"))
        .unwrap_or_else(|e| {
            tracing::error!("task #{} failed: {e}", cmd.task_id);
            failed_result(cmd.task_id, &e)
        });
    if let Err(e) = send_frame(&tx, MessageType::TaskResult, &result).await {
        tracing::error!("failed to report task result: {e}");
    }
}

/// 按 module 名查找并运行工具（与 cli 的 `tool run` 同源）。
async fn run_module(
    name: &str,
    args: &serde_json::Value,
) -> std::result::Result<ToolOutput, String> {
    match tools::lookup(name) {
        Some(tool) => tool.run(args).await.map_err(|e| e.to_string()),
        None => Err(format!("unknown module: {name}")),
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
        persist_received_binary(&bytes, &push.version, &data_dir);
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
        error: if ok {
            None
        } else {
            Some("checksum mismatch".into())
        },
    };
    let _ = send_frame(&tx, MessageType::BinaryAck, &ack).await;
}

/// 将收齐并通过校验的二进制写入 data_dir。
fn persist_received_binary(bytes: &[u8], version: &str, data_dir: &PathBuf) {
    std::fs::create_dir_all(data_dir).ok();
    let dest = data_dir.join(format!("rustto-client-{version}.bin"));
    if let Err(e) = std::fs::write(&dest, bytes) {
        tracing::error!("write received binary failed: {e}");
    } else {
        tracing::info!(
            "received new binary v{version} -> {} ({} bytes)",
            dest.display(),
            bytes.len()
        );
    }
}

/// 构造失败任务结果。
fn failed_result(task_id: i64, error: &str) -> TaskResult {
    TaskResult {
        task_id,
        status: "failed".into(),
        file_path: None,
        size: None,
        checksum: None,
        error: Some(error.into()),
    }
}

/// 现在的 Unix 秒。
fn now_secs() -> i64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|d| d.as_secs() as i64)
        .unwrap_or(0)
}

/// 把工具产物映射为成功 TaskResult。
trait IntoTaskResult {
    fn to_task_result(&self, task_id: i64, status: &str) -> TaskResult;
}

impl IntoTaskResult for ToolOutput {
    fn to_task_result(&self, task_id: i64, status: &str) -> TaskResult {
        TaskResult {
            task_id,
            status: status.into(),
            file_path: Some(self.file_path.clone()),
            size: Some(self.size),
            checksum: Some(self.checksum.clone()),
            error: None,
        }
    }
}

#[cfg(test)]
mod tests {
    //! 覆盖产物→TaskResult 映射与失败结果构造。

    use super::*;
    use rustto_common::ToolOutput;

    #[test]
    fn output_maps_to_success_result() {
        let out = ToolOutput {
            file_path: "/a.tar.gz".into(),
            size: 5,
            checksum: "ff".into(),
        };
        let r = out.to_task_result(7, "success");
        assert_eq!(r.task_id, 7);
        assert_eq!(r.status, "success");
        assert_eq!(r.file_path.as_deref(), Some("/a.tar.gz"));
        assert_eq!(r.size, Some(5));
        assert_eq!(r.checksum.as_deref(), Some("ff"));
        assert!(r.error.is_none());
    }

    #[test]
    fn failed_result_shape() {
        let r = failed_result(9, "boom");
        assert_eq!(r.task_id, 9);
        assert_eq!(r.status, "failed");
        assert_eq!(r.error.as_deref(), Some("boom"));
        assert!(r.file_path.is_none());
    }
}
