/**
 * 情感感知服务。
 *
 * @author AaronZZH & Kiro
 */
package com.xuejiai.aaf.framework.intelligent.assistant;

import org.springframework.stereotype.Service;

import lombok.Getter;

/**
 * 情感分析、语气适配、个性化回复风格。
 * 通过文本情绪分类驱动回应风格/信息密度自适应。
 */
@Service
public class EmotionPerceptionService {

    /**
     * 分析用户情感状态。
     * 当前使用关键词规则，后续可升级为 LLM 情感分类。
     */
    public EmotionState analyze(String userInput) {
        var input = userInput.toLowerCase();

        if (containsAny(input, "急", "快", "赶紧", "马上", "urgent", "asap")) {
            return EmotionState.URGENT;
        }
        if (containsAny(input, "谢", "感谢", "太好了", "棒", "thanks", "great")) {
            return EmotionState.POSITIVE;
        }
        if (containsAny(input, "烦", "不行", "失败", "错误", "bug", "问题", "frustrated")) {
            return EmotionState.FRUSTRATED;
        }
        if (containsAny(input, "不懂", "不明白", "什么意思", "confused")) {
            return EmotionState.CONFUSED;
        }

        return EmotionState.NEUTRAL;
    }

    /** 根据情感状态推荐回复风格 */
    public ResponseStyle suggestStyle(EmotionState emotion) {
        return switch (emotion) {
            case URGENT -> new ResponseStyle("concise", "high", "direct");
            case POSITIVE -> new ResponseStyle("friendly", "medium", "encouraging");
            case FRUSTRATED -> new ResponseStyle("empathetic", "low", "solution-focused");
            case CONFUSED -> new ResponseStyle("explanatory", "low", "step-by-step");
            case NEUTRAL -> new ResponseStyle("professional", "medium", "balanced");
        };
    }

    private boolean containsAny(String text, String... keywords) {
        for (var kw : keywords) {
            if (text.contains(kw)) return true;
        }
        return false;
    }

    /** 情感状态 */
    @Getter
    public enum EmotionState {
        NEUTRAL, POSITIVE, FRUSTRATED, CONFUSED, URGENT
    }

    /** 回复风格建议 */
    public record ResponseStyle(String tone, String density, String approach) {}
}
