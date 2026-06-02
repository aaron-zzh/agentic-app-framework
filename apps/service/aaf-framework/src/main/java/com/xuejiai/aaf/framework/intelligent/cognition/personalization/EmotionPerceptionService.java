/**
 * 情感感知服务。
 *
 * @author AaronZZH & Kiro
 */
package com.xuejiai.aaf.framework.intelligent.cognition.personalization;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import com.xuejiai.aaf.framework.intelligent.core.llm.LlmClient;
import com.xuejiai.aaf.framework.intelligent.core.llm.LlmClient.LlmMessage;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 情感分析：LLM 驱动情感分类 + 情感历史追踪 + 回复风格建议。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmotionPerceptionService {

    private final LlmClient llmClient;

    /** 用户情感历史（sessionId → 最近 N 条情感记录） */
    private final Map<String, LinkedList<EmotionState>> emotionHistory = new ConcurrentHashMap<>();

    private static final int MAX_HISTORY = 10;

    private static final String EMOTION_PROMPT =
            """
            分析用户输入的情感状态，只返回一个词：NEUTRAL/POSITIVE/FRUSTRATED/CONFUSED/URGENT
            用户输入：%s""";

    /** 分析用户情感状态（LLM 驱动，规则兜底）。 */
    public EmotionState analyze(String userInput) {
        try {
            var prompt = EMOTION_PROMPT.formatted(userInput);
            var response =
                    llmClient.call(List.of(LlmMessage.user(prompt)), "emotion_classify", null);
            return parseEmotion(response.trim());
        } catch (Exception e) {
            log.debug("LLM 情感分析降级为规则: {}", e.getMessage());
            return fallbackAnalyze(userInput);
        }
    }

    /** 分析并记录到历史 */
    public EmotionState analyzeAndTrack(String sessionId, String userInput) {
        var state = analyze(userInput);
        trackEmotion(sessionId, state);
        return state;
    }

    /** 获取情感历史 */
    public List<EmotionState> getHistory(String sessionId) {
        return emotionHistory.getOrDefault(sessionId, new LinkedList<>());
    }

    /** 获取情感趋势（最近 N 条中占比最高的情感） */
    public EmotionState getDominantEmotion(String sessionId) {
        var history = emotionHistory.get(sessionId);
        if (history == null || history.isEmpty()) return EmotionState.NEUTRAL;
        var counts = new java.util.EnumMap<EmotionState, Integer>(EmotionState.class);
        for (var e : history) counts.merge(e, 1, Integer::sum);
        return counts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(EmotionState.NEUTRAL);
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

    private void trackEmotion(String sessionId, EmotionState state) {
        emotionHistory.computeIfAbsent(sessionId, k -> new LinkedList<>());
        var history = emotionHistory.get(sessionId);
        history.addLast(state);
        if (history.size() > MAX_HISTORY) history.removeFirst();
    }

    private EmotionState parseEmotion(String response) {
        var upper = response.toUpperCase().replaceAll("[^A-Z]", "");
        try {
            return EmotionState.valueOf(upper);
        } catch (IllegalArgumentException e) {
            // 模糊匹配
            for (var state : EmotionState.values()) {
                if (response.toUpperCase().contains(state.name())) return state;
            }
            return EmotionState.NEUTRAL;
        }
    }

    /** 规则兜底 */
    private EmotionState fallbackAnalyze(String userInput) {
        var input = userInput.toLowerCase();
        if (containsAny(input, "急", "快", "赶紧", "马上", "urgent", "asap")) return EmotionState.URGENT;
        if (containsAny(input, "谢", "感谢", "太好了", "棒", "thanks", "great"))
            return EmotionState.POSITIVE;
        if (containsAny(input, "烦", "不行", "失败", "错误", "bug", "frustrated"))
            return EmotionState.FRUSTRATED;
        if (containsAny(input, "不懂", "不明白", "什么意思", "confused")) return EmotionState.CONFUSED;
        return EmotionState.NEUTRAL;
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
        NEUTRAL,
        POSITIVE,
        FRUSTRATED,
        CONFUSED,
        URGENT
    }

    /** 回复风格建议 */
    public record ResponseStyle(String tone, String density, String approach) {}
}
