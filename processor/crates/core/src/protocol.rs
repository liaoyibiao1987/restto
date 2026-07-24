//! Netty 通信协议：长度帧 + JSON。
//!
//! 线路格式（大端）：
//! ```text
//! | magic: 4B = "RUST"(0x52555354) | length: 4B u32 | type: 1B | payload: JSON(length-1 字节) |
//! ```
//! `length` = type 字段(1) + payload 字节数。与 manager/web 的 Java Netty 编解码严格对齐。

use bytes::{BufMut, Bytes, BytesMut};
use serde::{Deserialize, Serialize};
use thiserror::Error;
use tokio::io::{AsyncRead, AsyncReadExt, AsyncWrite, AsyncWriteExt};

/// 魔数 "RUST"。
pub const MAGIC: [u8; 4] = [0x52, 0x55, 0x53, 0x54];

/// 头部固定长度：magic(4) + length(4) + type(1)。
pub const HEADER_LEN: usize = 9;

/// 单帧 payload 上限（256 MiB），防止异常长度导致内存爆炸。
pub const MAX_PAYLOAD_LEN: usize = 256 * 1024 * 1024;

/// 消息类型枚举。
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
#[repr(u8)]
pub enum MessageType {
    /// 客户端注册（C→S）。
    Register = 1,
    /// 注册应答（S→C）。
    RegisterAck = 2,
    /// 心跳（双向）。
    Heartbeat = 3,
    /// 任务下发（S→C）。
    TaskCommand = 4,
    /// 任务结果回传（C→S）。
    TaskResult = 5,
    /// 二进制下发分片（S→C）。
    BinaryPush = 6,
    /// 二进制下发应答（C→S）。
    BinaryAck = 7,
}

impl MessageType {
    /// 将原始字节还原为消息类型。
    pub fn from_u8(value: u8) -> Option<Self> {
        match value {
            1 => Some(Self::Register),
            2 => Some(Self::RegisterAck),
            3 => Some(Self::Heartbeat),
            4 => Some(Self::TaskCommand),
            5 => Some(Self::TaskResult),
            6 => Some(Self::BinaryPush),
            7 => Some(Self::BinaryAck),
            _ => None,
        }
    }
}

/// 协议层错误。
#[derive(Debug, Error)]
pub enum ProtocolError {
    #[error("invalid magic bytes")]
    InvalidMagic,
    #[error("invalid message type: {0}")]
    InvalidMessageType(u8),
    #[error("payload too large: {0} bytes")]
    PayloadTooLarge(usize),
    #[error("io error: {0}")]
    Io(#[from] std::io::Error),
    #[error("json error: {0}")]
    Json(#[from] serde_json::Error),
}

// ── 各消息 payload 结构（字段命名 snake_case，与 Java Jackson SNAKE_CASE 对齐）──

/// 节点注册消息。
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct RegisterMessage {
    pub node_name: String,
    pub node_token: String,
    pub version: String,
}

/// 注册应答。
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct RegisterAck {
    pub success: bool,
    pub message: String,
    pub node_id: Option<i64>,
}

/// 心跳。
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Heartbeat {
    pub timestamp: i64,
}

/// 任务下发指令。
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct TaskCommand {
    pub task_id: i64,
    pub module: String,
    pub args: serde_json::Value,
}

/// 任务执行结果。
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct TaskResult {
    pub task_id: i64,
    pub status: String,
    pub file_path: Option<String>,
    pub size: Option<u64>,
    pub checksum: Option<String>,
    pub error: Option<String>,
}

/// 二进制下发分片（data 为 base64）。
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct BinaryPush {
    pub version: String,
    pub checksum: String,
    pub chunk_index: u32,
    pub total_chunks: u32,
    pub data: String,
}

/// 二进制下发应答。
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct BinaryAck {
    pub version: String,
    pub success: bool,
    pub error: Option<String>,
}

/// 将 (类型, payload) 编码为完整帧字节。
///
/// - `message_type`: 消息类型。
/// - `payload`: 任何可序列化为 JSON 的结构。
/// 返回包含完整帧（含 magic/length/type）的 `Bytes`。
pub fn encode<T: Serialize>(
    message_type: MessageType,
    payload: &T,
) -> Result<Bytes, ProtocolError> {
    let json = serde_json::to_vec(payload)?;
    if json.len() > MAX_PAYLOAD_LEN {
        return Err(ProtocolError::PayloadTooLarge(json.len()));
    }
    let body_len = 1 + json.len();
    let mut buf = BytesMut::with_capacity(HEADER_LEN + json.len());
    buf.put_slice(&MAGIC);
    buf.put_u32(body_len as u32);
    buf.put_u8(message_type as u8);
    buf.put_slice(&json);
    Ok(buf.freeze())
}

/// 从异步流读取一帧，返回 (消息类型, JSON 值)。
///
/// 阻塞直到读满一帧或流结束（EOF 返回 `ProtocolError::Io` UnexpectedEof）。
pub async fn read_frame<R: AsyncRead + Unpin>(
    reader: &mut R,
) -> Result<(MessageType, serde_json::Value), ProtocolError> {
    let mut header = [0u8; HEADER_LEN];
    reader.read_exact(&mut header).await?;
    if header[0..4] != MAGIC {
        return Err(ProtocolError::InvalidMagic);
    }
    let body_len = u32::from_be_bytes([header[4], header[5], header[6], header[7]]) as usize;
    if body_len == 0 {
        return Err(ProtocolError::InvalidMessageType(0));
    }
    if body_len > 1 + MAX_PAYLOAD_LEN {
        return Err(ProtocolError::PayloadTooLarge(body_len));
    }
    let message_type =
        MessageType::from_u8(header[8]).ok_or(ProtocolError::InvalidMessageType(header[8]))?;
    let payload_len = body_len - 1;
    let mut payload = vec![0u8; payload_len];
    reader.read_exact(&mut payload).await?;
    let value: serde_json::Value = serde_json::from_slice(&payload)?;
    Ok((message_type, value))
}

/// 向异步流写入一帧并 flush。
pub async fn write_frame<W: AsyncWrite + Unpin, T: Serialize>(
    writer: &mut W,
    message_type: MessageType,
    payload: &T,
) -> Result<(), ProtocolError> {
    let bytes = encode(message_type, payload)?;
    writer.write_all(&bytes).await?;
    writer.flush().await?;
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[tokio::test]
    async fn encode_then_decode_roundtrip() {
        let msg = RegisterMessage {
            node_name: "n1".into(),
            node_token: "tok".into(),
            version: "0.1.0".into(),
        };
        let frame = encode(MessageType::Register, &msg).unwrap();
        let mut cursor = std::io::Cursor::new(frame);
        let (mt, value) = read_frame(&mut cursor).await.unwrap();
        assert_eq!(mt, MessageType::Register);
        assert_eq!(value["node_name"], "n1");
        assert_eq!(value["node_token"], "tok");
    }

    #[tokio::test]
    async fn rejects_bad_magic() {
        let mut buf = BytesMut::new();
        buf.put_u32(0xDEADBEEF);
        buf.put_u32(1);
        buf.put_u8(MessageType::Heartbeat as u8);
        let mut cursor = std::io::Cursor::new(buf);
        let err = read_frame(&mut cursor).await;
        assert!(matches!(err, Err(ProtocolError::InvalidMagic)));
    }
}
