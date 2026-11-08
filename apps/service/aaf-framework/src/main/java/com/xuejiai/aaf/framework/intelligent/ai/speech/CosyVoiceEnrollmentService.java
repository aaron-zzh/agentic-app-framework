package com.xuejiai.aaf.framework.intelligent.ai.speech;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.alibaba.dashscope.audio.ttsv2.enrollment.Voice;
import com.alibaba.dashscope.audio.ttsv2.enrollment.VoiceEnrollmentParam;

import lombok.extern.slf4j.Slf4j;

/**
 * 基于 DashScope SDK 的 CosyVoice 声音复刻服务。
 *
 * <p>复刻的音色（voiceId）可直接传给 {@link DashScopeSpeechService#synthesize} 的 voice 参数用于 TTS 合成。
 *
 * <p>支持模型：cosyvoice-v3-plus / cosyvoice-v3-flash / cosyvoice-v3.5-plus / cosyvoice-v3.5-flash
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "spring.ai.dashscope.api-key", matchIfMissing = false)
public class CosyVoiceEnrollmentService {

    private static final String CLONE_MODEL = "voice-enrollment";

    private final com.alibaba.dashscope.audio.ttsv2.enrollment.VoiceEnrollmentService sdkService;

    public CosyVoiceEnrollmentService(@Value("${spring.ai.dashscope.api-key:}") String apiKey) {
        this.sdkService =
                new com.alibaba.dashscope.audio.ttsv2.enrollment.VoiceEnrollmentService(apiKey);
    }

    /**
     * 创建 CosyVoice 克隆音色。
     *
     * @param targetModel 驱动音色的 TTS 模型，如 cosyvoice-v3-plus
     * @param prefix 音色名称前缀（仅字母数字，≤10字符）
     * @param audioUrl 公网可访问的音频 URL
     * @param languageHint 音频语种，如 zh / en，null 时默认 zh
     * @return voiceId，如 cosyvoice-v3-plus-myvoice-xxx，可直接用于 TTS voice 参数
     */
    public String createVoice(
            String targetModel, String prefix, String audioUrl, String languageHint) {
        try {
            var param =
                    VoiceEnrollmentParam.builder()
                            .model(CLONE_MODEL)
                            .languageHints(
                                    Collections.singletonList(
                                            languageHint != null ? languageHint : "zh"))
                            .build();
            Voice voice = sdkService.createVoice(targetModel, prefix, audioUrl, param);
            log.info(
                    "[CosyVoiceEnrollment] 音色创建成功: voiceId={}, targetModel={}",
                    voice.getVoiceId(),
                    targetModel);
            return voice.getVoiceId();
        } catch (Exception e) {
            throw new RuntimeException("CosyVoice 音色创建失败: " + e.getMessage(), e);
        }
    }

    /**
     * 查询音色列表。
     *
     * @param prefix 按前缀筛选，null 表示不过滤
     * @param pageIndex 页码（从 0 开始）
     * @param pageSize 每页条数
     */
    public List<Voice> listVoices(String prefix, int pageIndex, int pageSize) {
        try {
            Voice[] voices = sdkService.listVoice(prefix, pageIndex, pageSize);
            return voices != null ? Arrays.asList(voices) : Collections.emptyList();
        } catch (Exception e) {
            throw new RuntimeException("查询音色列表失败: " + e.getMessage(), e);
        }
    }

    /** 删除指定音色。 */
    public void deleteVoice(String voiceId) {
        try {
            sdkService.deleteVoice(voiceId);
            log.info("[CosyVoiceEnrollment] 音色已删除: voiceId={}", voiceId);
        } catch (Exception e) {
            throw new RuntimeException("删除音色失败: " + e.getMessage(), e);
        }
    }
}
