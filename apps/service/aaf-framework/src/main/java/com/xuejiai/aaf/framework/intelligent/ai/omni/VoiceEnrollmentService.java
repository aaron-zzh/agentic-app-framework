package com.xuejiai.aaf.framework.intelligent.ai.omni;

import java.util.List;

/**
 * 声音复刻服务接口。
 *
 * <p>基于 qwen-voice-enrollment 模型，无需训练即可从短音频克隆音色。 创建的 voice 可直接传入 {@link
 * OmniRealtimeService.SessionConfig#voice()} 使用。
 *
 * <p>工作流：先调用 {@link #createVoice} 获取 voice 名称，再传入 Omni 对话接口。 两步必须使用相同的 {@code targetModel}。
 */
public interface VoiceEnrollmentService {

    /**
     * 创建音色。
     *
     * @param request 创建请求，包含音频数据和目标模型
     * @return 音色名称，可直接用于 OmniRealtimeService 的 voice 参数
     */
    String createVoice(CreateVoiceRequest request);

    /**
     * 分页查询已创建的音色列表。
     *
     * @param pageIndex 页码（从 0 开始）
     * @param pageSize 每页条数
     * @return 音色列表
     */
    List<VoiceInfo> listVoices(int pageIndex, int pageSize);

    /**
     * 删除指定音色，释放对应额度。
     *
     * @param voice 待删除的音色名称
     */
    void deleteVoice(String voice);

    /** 创建音色请求。 */
    record CreateVoiceRequest(
            /**
             * 驱动音色的全模态模型，必须与后续调用 Omni 接口时的模型一致。 可选值：qwen3.5-omni-plus-realtime /
             * qwen3.5-omni-flash-realtime / qwen3.5-omni-plus / qwen3.5-omni-flash
             */
            String targetModel,
            /** 音色别名（仅允许数字、大小写字母和下划线，不超过16个字符） */
            String preferredName,
            /** 音频数据，支持两种格式： 1. Data URL：{@code data:<mediatype>;base64,<data>} 2. 公网可访问的音频 URL */
            String audioData,
            /** 可选：与音频内容匹配的文本，用于服务端校验 */
            String text,
            /** 可选：音频语种，如 zh / en / ja，中文方言如 Sichuan */
            String language) {

        /** 最常用构造：只传必填三项。 */
        public CreateVoiceRequest(String targetModel, String preferredName, String audioData) {
            this(targetModel, preferredName, audioData, null, null);
        }
    }

    /** 音色信息。 */
    record VoiceInfo(
            /** 音色名称 */
            String voice,
            /** 创建时间，格式 yyyy-MM-dd HH:mm:ss */
            String gmtCreate,
            /** 驱动该音色的全模态模型 */
            String targetModel) {}
}
