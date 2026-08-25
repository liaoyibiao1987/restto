package protocol

import (
	"bytes"
	"encoding/json"
	"errors"
	"testing"
)

// 覆盖编码→解码往返、坏 magic 拒收、超限拒收与 JSON 校验。

func TestEncodeThenDecodeRoundtrip(t *testing.T) {
	msg := RegisterMessage{NodeName: "n1", NodeToken: "tok", Version: "0.1.0"}
	frame, err := Encode(TypeRegister, msg)
	if err != nil {
		t.Fatalf("encode: %v", err)
	}
	mt, payload, err := ReadFrame(bytes.NewReader(frame))
	if err != nil {
		t.Fatalf("read frame: %v", err)
	}
	if mt != TypeRegister {
		t.Fatalf("message type = %v, want %v", mt, TypeRegister)
	}
	var got RegisterMessage
	if err := json.Unmarshal(payload, &got); err != nil {
		t.Fatalf("unmarshal: %v", err)
	}
	if got.NodeName != "n1" || got.NodeToken != "tok" {
		t.Fatalf("roundtrip mismatch: %+v", got)
	}
}

func TestRejectsBadMagic(t *testing.T) {
	buf := bytes.NewBuffer(nil)
	buf.Write([]byte{0xDE, 0xAD, 0xBE, 0xEF})
	buf.Write([]byte{0, 0, 0, 1})
	buf.Write([]byte{byte(TypeHeartbeat)})
	_, _, err := ReadFrame(buf)
	if !errors.Is(err, ErrInvalidMagic) {
		t.Fatalf("err = %v, want ErrInvalidMagic", err)
	}
}

func TestRejectsUnknownMessageType(t *testing.T) {
	frame, err := Encode(TypeHeartbeat, Heartbeat{Timestamp: 1})
	if err != nil {
		t.Fatalf("encode: %v", err)
	}
	frame[8] = 0xFF
	_, _, err = ReadFrame(bytes.NewReader(frame))
	if err == nil {
		t.Fatal("expected error for unknown message type")
	}
}

func TestRejectsOversizedFrameHeader(t *testing.T) {
	buf := bytes.NewBuffer(nil)
	buf.Write(Magic[:])
	buf.Write([]byte{0x7F, 0xFF, 0xFF, 0xFF}) // bodyLen 远超上限
	buf.WriteByte(byte(TypeHeartbeat))
	_, _, err := ReadFrame(buf)
	var tooLarge *FrameHeaderTooLargeError
	if !errors.As(err, &tooLarge) {
		t.Fatalf("err = %v, want FrameHeaderTooLargeError", err)
	}
}

func TestRejectsInvalidPayloadJSON(t *testing.T) {
	buf := bytes.NewBuffer(nil)
	buf.Write(Magic[:])
	buf.Write([]byte{0, 0, 0, 4}) // bodyLen = 1 + 3 字节非法 JSON
	buf.WriteByte(byte(TypeHeartbeat))
	buf.WriteString("abc") // 非法 JSON
	_, _, err := ReadFrame(buf)
	if err == nil {
		t.Fatal("expected error for invalid payload json")
	}
}

func TestWriteFrameFlushes(t *testing.T) {
	var buf flushBuffer
	if err := WriteFrame(&buf, TypeHeartbeat, Heartbeat{Timestamp: 42}); err != nil {
		t.Fatalf("write frame: %v", err)
	}
	if !buf.flushed {
		t.Fatal("expected flush to be called")
	}
	mt, payload, err := ReadFrame(&buf.Buffer)
	if err != nil {
		t.Fatalf("read back: %v", err)
	}
	if mt != TypeHeartbeat {
		t.Fatalf("message type = %v", mt)
	}
	var hb Heartbeat
	if err := json.Unmarshal(payload, &hb); err != nil {
		t.Fatalf("unmarshal: %v", err)
	}
	if hb.Timestamp != 42 {
		t.Fatalf("timestamp = %d, want 42", hb.Timestamp)
	}
}

func TestTaskCommandArgsRawPassthrough(t *testing.T) {
	cmd := TaskCommand{TaskID: 7, Module: "backup_file", Args: json.RawMessage(`{"path":"/data"}`)}
	frame, err := Encode(TypeTaskCommand, cmd)
	if err != nil {
		t.Fatalf("encode: %v", err)
	}
	_, payload, err := ReadFrame(bytes.NewReader(frame))
	if err != nil {
		t.Fatalf("read frame: %v", err)
	}
	var got TaskCommand
	if err := json.Unmarshal(payload, &got); err != nil {
		t.Fatalf("unmarshal: %v", err)
	}
	if got.Module != "backup_file" || string(got.Args) != `{"path":"/data"}` {
		t.Fatalf("args passthrough mismatch: %+v", got)
	}
}

// flushBuffer 包装 bytes.Buffer 并记录 Flush 调用。
type flushBuffer struct {
	bytes.Buffer
	flushed bool
}

func (b *flushBuffer) Flush() error {
	b.flushed = true
	return nil
}
