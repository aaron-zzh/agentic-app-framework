package com.xuejiai.aaf.module.voice.protocol;

/**
 * 语音会话协议 v3 的常量。协议契约见 {@code modbot/docs/aaf-device-integration.md}。
 *
 * <p>错误码与设备通用层 REST 保持一致：积分不足 11000018，语音网关失败 11000019。
 */
public final class VoiceProtocol {

    public static final int PROTO = 3;

    /** 二进制帧类型：客户端 → 服务端麦克风 PCM。 */
    public static final byte FRAME_MIC = 0x01;

    /** 二进制帧类型：服务端 → 客户端 TTS PCM。 */
    public static final byte FRAME_TTS = 0x02;

    /** 二进制帧头长度：type(1) + id(4, 大端)。 */
    public static final int FRAME_HEADER = 5;

    public static final String CODEC_PCM = "pcm_s16le";
    public static final int AUDIO_RATE = 16_000;

    public static final int ERR_CREDITS_INSUFFICIENT = 11_000_018;
    public static final int ERR_GATEWAY_FAILED = 11_000_019;
    public static final int ERR_BAD_REQUEST = 400;

    public static final String ENDPOINT_CLIENT = "client";
    public static final String ENDPOINT_SERVER = "server";

    private VoiceProtocol() {}
}
