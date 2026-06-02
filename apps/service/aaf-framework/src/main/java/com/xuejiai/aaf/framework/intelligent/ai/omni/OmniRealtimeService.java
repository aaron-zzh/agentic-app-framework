package com.xuejiai.aaf.framework.intelligent.ai.omni;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Qwen-Omni 实时多模态对话服务接口。
 *
 * <p>封装 DashScope OmniRealtimeConversation，提供音频/视频双向流式交互能力。 支持 VAD 自动检测和手动模式。
 */
public interface OmniRealtimeService {

    /** 创建一个实时会话，返回会话句柄。 */
    OmniSession createSession(SessionConfig config, Consumer<OmniEvent> eventCallback);

    /** 会话配置。 */
    record SessionConfig(
            /** 模型名称，如 qwen3-omni-flash-realtime */
            String model,
            /** 音色 */
            String voice,
            /** 是否开启 VAD */
            boolean enableTurnDetection,
            /** 是否开启输入音频转录 */
            boolean enableInputAudioTranscription,
            /** 系统指令 */
            String instructions,
            /** 输出模态：text / audio */
            List<String> modalities) {}

    /** 服务端推送事件。 */
    record OmniEvent(
            /** 事件类型：audio_transcript_delta / audio_delta / transcript_done / audio_done / error */
            String type,
            /** 文本内容（转录文本或错误信息） */
            String text,
            /** Base64 音频数据片段 */
            String audioData,
            /** 额外属性 */
            Map<String, Object> extra) {}

    /** 会话句柄，用于发送音频/视频和控制会话。 */
    interface OmniSession {

        /** 发送 Base64 编码的音频片段。 */
        void sendAudio(String audioBase64);

        /** 发送 Base64 编码的图片帧。 */
        void sendVideo(String imageBase64);

        /** 手动提交缓冲区（Manual 模式）。 */
        void commit();

        /** 手动触发模型响应（Manual 模式）。 */
        void createResponse();

        /** 取消正在进行的响应。 */
        void cancelResponse();

        /** 关闭会话。 */
        void close();

        /** 获取会话 ID。 */
        String getSessionId();
    }
}
