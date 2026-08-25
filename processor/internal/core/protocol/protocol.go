// Package protocol 实现 Netty 通信协议：长度帧 + JSON。
//
// 线路格式（大端）：
//
//	| magic: 4B = "RUST"(0x52555354) | length: 4B u32 | type: 1B | payload: JSON(length-1 字节) |
//
// length = type 字段(1) + payload 字节数。与 manager/web 的 Java Netty 编解码严格对齐。
package protocol

import (
	"encoding/binary"
	"encoding/json"
	"errors"
	"fmt"
	"io"
)

// Magic 魔数 "RUST"。
var Magic = [4]byte{0x52, 0x55, 0x53, 0x54}

// HeaderLen 头部固定长度：magic(4) + length(4) + type(1)。
const HeaderLen = 9

// MaxPayloadLen 单帧 payload 上限（256 MiB），防止异常长度导致内存爆炸。
const MaxPayloadLen = 256 * 1024 * 1024

// MessageType 消息类型枚举。
type MessageType uint8

// 各消息类型取值（与服务端 Java 协议枚举一一对应）。
const (
	// TypeRegister 客户端注册（C→S）。
	TypeRegister MessageType = 1
	// TypeRegisterAck 注册应答（S→C）。
	TypeRegisterAck MessageType = 2
	// TypeHeartbeat 心跳（双向）。
	TypeHeartbeat MessageType = 3
	// TypeTaskCommand 任务下发（S→C）。
	TypeTaskCommand MessageType = 4
	// TypeTaskResult 任务结果回传（C→S）。
	TypeTaskResult MessageType = 5
	// TypeBinaryPush 二进制下发分片（S→C）。
	TypeBinaryPush MessageType = 6
	// TypeBinaryAck 二进制下发应答（C→S）。
	TypeBinaryAck MessageType = 7
)

// MessageTypeFromByte 将原始字节还原为消息类型；未知值返回错误。
func MessageTypeFromByte(value byte) (MessageType, error) {
	switch value {
	case 1:
		return TypeRegister, nil
	case 2:
		return TypeRegisterAck, nil
	case 3:
		return TypeHeartbeat, nil
	case 4:
		return TypeTaskCommand, nil
	case 5:
		return TypeTaskResult, nil
	case 6:
		return TypeBinaryPush, nil
	case 7:
		return TypeBinaryAck, nil
	default:
		return 0, fmt.Errorf("invalid message type: %d", value)
	}
}

// String 返回消息类型名（日志友好）。
func (t MessageType) String() string {
	switch t {
	case TypeRegister:
		return "REGISTER"
	case TypeRegisterAck:
		return "REGISTER_ACK"
	case TypeHeartbeat:
		return "HEARTBEAT"
	case TypeTaskCommand:
		return "TASK_COMMAND"
	case TypeTaskResult:
		return "TASK_RESULT"
	case TypeBinaryPush:
		return "BINARY_PUSH"
	case TypeBinaryAck:
		return "BINARY_ACK"
	default:
		return fmt.Sprintf("UNKNOWN(%d)", uint8(t))
	}
}

// 各协议层错误。
var (
	// ErrInvalidMagic magic 字节不符。
	ErrInvalidMagic = errors.New("invalid magic bytes")
	// ErrEmptyBody 帧体长度为 0。
	ErrEmptyBody = errors.New("empty frame body")
)

// FrameHeaderTooLargeError payload 超限错误。
type FrameHeaderTooLargeError struct{ BodyLen int }

func (e *FrameHeaderTooLargeError) Error() string {
	return fmt.Sprintf("payload too large: %d bytes", e.BodyLen)
}

// PayloadTooLargeError payload 超限错误（编码侧）。
type PayloadTooLargeError struct{ Size int }

func (e *PayloadTooLargeError) Error() string {
	return fmt.Sprintf("payload too large: %d bytes", e.Size)
}

// 各消息 payload 结构（json tag snake_case，与 Java Jackson SNAKE_CASE 对齐）。

// RegisterMessage 节点注册消息。
type RegisterMessage struct {
	NodeName  string `json:"node_name"`
	NodeToken string `json:"node_token"`
	Version   string `json:"version"`
}

// RegisterAck 注册应答。
type RegisterAck struct {
	Success bool   `json:"success"`
	Message string `json:"message"`
	NodeID  *int64 `json:"node_id"`
}

// Heartbeat 心跳。
type Heartbeat struct {
	Timestamp int64 `json:"timestamp"`
}

// TaskCommand 任务下发指令。
type TaskCommand struct {
	TaskID int64           `json:"task_id"`
	Module string          `json:"module"`
	Args   json.RawMessage `json:"args"`
}

// TaskResult 任务执行结果。
type TaskResult struct {
	TaskID   int64   `json:"task_id"`
	Status   string  `json:"status"`
	FilePath *string `json:"file_path"`
	Size     *uint64 `json:"size"`
	Checksum *string `json:"checksum"`
	Error    *string `json:"error"`
}

// BinaryPush 二进制下发分片（data 为 base64）。
type BinaryPush struct {
	Version     string `json:"version"`
	Checksum    string `json:"checksum"`
	ChunkIndex  uint32 `json:"chunk_index"`
	TotalChunks uint32 `json:"total_chunks"`
	Data        string `json:"data"`
}

// BinaryAck 二进制下发应答。
type BinaryAck struct {
	Version string  `json:"version"`
	Success bool    `json:"success"`
	Error   *string `json:"error"`
}

// Encode 将 (类型, payload) 编码为完整帧字节。
//
// - messageType: 消息类型。
// - payload: 任何可序列化为 JSON 的结构。
// 返回包含完整帧（含 magic/length/type）的字节切片。
func Encode(messageType MessageType, payload any) ([]byte, error) {
	body, err := json.Marshal(payload)
	if err != nil {
		return nil, fmt.Errorf("json error: %w", err)
	}
	if len(body) > MaxPayloadLen {
		return nil, &PayloadTooLargeError{Size: len(body)}
	}
	frame := make([]byte, 0, HeaderLen+len(body))
	frame = append(frame, Magic[:]...)
	frame = binary.BigEndian.AppendUint32(frame, uint32(1+len(body)))
	frame = append(frame, byte(messageType))
	frame = append(frame, body...)
	return frame, nil
}

// ReadFrame 从流读取一帧，返回 (消息类型, JSON 值)。
//
// 阻塞直到读满一帧或流结束（EOF 转为 io.ErrUnexpectedEOF）。
func ReadFrame(reader io.Reader) (MessageType, json.RawMessage, error) {
	header := make([]byte, HeaderLen)
	if _, err := io.ReadFull(reader, header); err != nil {
		return 0, nil, err
	}
	if string(header[0:4]) != string(Magic[:]) {
		return 0, nil, ErrInvalidMagic
	}
	bodyLen := int(binary.BigEndian.Uint32(header[4:8]))
	if bodyLen == 0 {
		return 0, nil, ErrEmptyBody
	}
	if bodyLen > 1+MaxPayloadLen {
		return 0, nil, &FrameHeaderTooLargeError{BodyLen: bodyLen}
	}
	messageType, err := MessageTypeFromByte(header[8])
	if err != nil {
		return 0, nil, err
	}
	payload := make([]byte, bodyLen-1)
	if _, err := io.ReadFull(reader, payload); err != nil {
		return 0, nil, err
	}
	// 校验 JSON 合法性，尽早暴露坏帧。
	var probe json.RawMessage = payload
	if !json.Valid(probe) {
		return 0, nil, fmt.Errorf("json error: invalid payload json")
	}
	return messageType, probe, nil
}

// WriteFrame 向流写入一帧并（若为 Flusher）flush。
func WriteFrame(writer io.Writer, messageType MessageType, payload any) error {
	frame, err := Encode(messageType, payload)
	if err != nil {
		return err
	}
	if _, err = writer.Write(frame); err != nil {
		return err
	}
	if f, ok := writer.(interface{ Flush() error }); ok {
		return f.Flush()
	}
	return nil
}
