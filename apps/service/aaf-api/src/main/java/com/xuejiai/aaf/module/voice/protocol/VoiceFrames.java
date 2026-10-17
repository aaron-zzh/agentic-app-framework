package com.xuejiai.aaf.module.voice.protocol;

import java.nio.ByteBuffer;

/** 二进制帧编解码：{@code [type u8][id u32 大端][payload]}。 */
public final class VoiceFrames {

    /** 解码后的帧。 */
    public record Frame(byte type, int id, byte[] payload) {}

    private VoiceFrames() {}

    /** 解码入站帧；长度不足帧头返回 null。 */
    public static Frame decode(ByteBuffer buffer) {
        if (buffer.remaining() < VoiceProtocol.FRAME_HEADER) {
            return null;
        }
        var type = buffer.get();
        var id = buffer.getInt();
        var payload = new byte[buffer.remaining()];
        buffer.get(payload);
        return new Frame(type, id, payload);
    }

    /** 编码出站帧。 */
    public static byte[] encode(byte type, int id, byte[] payload, int offset, int length) {
        var frame = new byte[VoiceProtocol.FRAME_HEADER + length];
        frame[0] = type;
        frame[1] = (byte) (id >>> 24);
        frame[2] = (byte) (id >>> 16);
        frame[3] = (byte) (id >>> 8);
        frame[4] = (byte) id;
        System.arraycopy(payload, offset, frame, VoiceProtocol.FRAME_HEADER, length);
        return frame;
    }
}
