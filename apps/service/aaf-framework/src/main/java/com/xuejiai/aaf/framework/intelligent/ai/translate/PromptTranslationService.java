package com.xuejiai.aaf.framework.intelligent.ai.translate;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversation;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationParam;
import com.alibaba.dashscope.common.MultiModalMessage;
import com.alibaba.dashscope.common.Role;
import com.alibaba.dashscope.exception.ApiException;
import com.alibaba.dashscope.exception.NoApiKeyException;

import lombok.extern.slf4j.Slf4j;

/**
 * Prompt 翻译服务——将中文提示词翻译为英文，用于提升 Gemini 等对中文不友好的模型的生成质量。
 *
 * <p>使用百炼 MultiModalConversation SDK 同步调用（qwen3.7-plus）， 无中文时直接返回原文，API Key 未配置时降级返回原文。
 */
@Slf4j
@Service
public class PromptTranslationService {

    /** 翻译模型，可通过 aaf.ai.translate-model 覆盖 */
    @Value("${aaf.ai.translate-model:qwen3.7-plus}")
    private String translateModel;

    @Value("${spring.ai.dashscope.api-key:}")
    private String apiKey;

    /** 将 prompt 翻译为英文。无中文或 API Key 未配置时返回原文，翻译失败时警告并返回原文。 */
    public String toEnglish(String prompt) {
        if (prompt == null || prompt.isBlank()) return prompt;
        if (!containsChinese(prompt)) return prompt;
        if (apiKey == null || apiKey.isBlank()) return prompt;
        try {
            var systemMsg =
                    MultiModalMessage.builder()
                            .role(Role.SYSTEM.getValue())
                            .content(
                                    List.of(
                                            Map.of(
                                                    "text",
                                                    "Translate the following Chinese image generation prompt to English. Output only the translated text, no explanation.")))
                            .build();
            var userMsg =
                    MultiModalMessage.builder()
                            .role(Role.USER.getValue())
                            .content(Collections.singletonList(Map.of("text", prompt)))
                            .build();

            var param =
                    MultiModalConversationParam.builder()
                            .apiKey(apiKey)
                            .model(translateModel)
                            .messages(List.of(systemMsg, userMsg))
                            .maxLength(512)
                            .build();

            var result = new MultiModalConversation().call(param);
            var content = result.getOutput().getChoices().get(0).getMessage().getContent();
            if (content != null && !content.isEmpty()) {
                String translated = (String) content.get(0).get("text");
                if (translated != null && !translated.isBlank()) {
                    log.debug("[PromptTranslation] {} → {}", prompt, translated);
                    return translated.trim();
                }
            }
        } catch (ApiException | NoApiKeyException e) {
            log.warn("[PromptTranslation] 翻译失败，使用原文: {}", e.getMessage());
        } catch (Exception e) {
            log.warn("[PromptTranslation] 翻译异常，使用原文: {}", e.getMessage());
        }
        return prompt;
    }

    private static boolean containsChinese(String text) {
        return text.chars().anyMatch(c -> c >= 0x4E00 && c <= 0x9FFF);
    }
}
