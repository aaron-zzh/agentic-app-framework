/**
 * 意图理解服务。
 *
 * @author AaronZZH & Kiro
 */
package com.xuejiai.aaf.framework.intelligent.cognition.personalization;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.xuejiai.aaf.framework.intelligent.core.llm.LlmClient;
import com.xuejiai.aaf.framework.intelligent.core.llm.LlmClient.LlmMessage;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/** 意图理解：LLM 驱动意图分类 + 多轮意图跟踪 + 消歧 + 槽位填充。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IntentUnderstandingService {

    private final LlmClient llmClient;

    private static final String INTENT_PROMPT =
            """
            分析用户输入的意图，返回 JSON 格式（不要其他内容）：
            {"intent":"意图类型","confidence":0.0-1.0,"slots":{},"needsClarification":false,"clarificationQuestion":""}

            意图类型：question（提问）、command（指令）、creation（创建）、modification（修改）、deletion（删除）、navigation（导航）、conversation（闲聊）

            对话历史：
            %s

            用户输入：%s""";

    /** 分析用户意图（LLM 驱动，规则兜底）。 */
    public IntentResult analyze(String userInput, List<String> conversationHistory) {
        try {
            var historyText =
                    conversationHistory != null && !conversationHistory.isEmpty()
                            ? String.join(
                                    "\n",
                                    conversationHistory.subList(
                                            Math.max(0, conversationHistory.size() - 5),
                                            conversationHistory.size()))
                            : "无";
            var prompt = INTENT_PROMPT.formatted(historyText, userInput);
            var response =
                    llmClient.call(List.of(LlmMessage.user(prompt)), "intent_classify", null);
            return parseIntentResponse(response, userInput);
        } catch (Exception e) {
            log.warn("LLM 意图分类失败，降级为规则匹配: {}", e.getMessage());
            return fallbackAnalyze(userInput);
        }
    }

    /** 槽位填充：从对话上下文中提取缺失参数 */
    public Map<String, String> fillSlots(
            IntentResult intent, Map<String, String> requiredSlots, List<String> history) {
        if (requiredSlots == null || requiredSlots.isEmpty()) return Map.of();
        try {
            var prompt =
                    "从以下对话中提取参数，返回 JSON：\n需要的参数：%s\n对话：%s\n用户最新输入：%s"
                            .formatted(
                                    requiredSlots.keySet(),
                                    String.join("\n", history),
                                    intent.getRawInput());
            var response = llmClient.call(List.of(LlmMessage.user(prompt)), "slot_fill", null);
            return parseSlots(response);
        } catch (Exception e) {
            log.warn("槽位填充失败: {}", e.getMessage());
            return Map.of();
        }
    }

    private IntentResult parseIntentResponse(String response, String rawInput) {
        var result = new IntentResult();
        result.setRawInput(rawInput);
        // 简单 JSON 解析（避免引入额外依赖）
        result.setIntent(extractJsonField(response, "intent", "conversation"));
        result.setConfidence(extractJsonDouble(response, "confidence", 0.8));
        result.setNeedsClarification(response.contains("\"needsClarification\":true"));
        result.setClarificationQuestion(extractJsonField(response, "clarificationQuestion", null));
        return result;
    }

    /** 规则兜底（LLM 不可用时） */
    private IntentResult fallbackAnalyze(String userInput) {
        var result = new IntentResult();
        result.setRawInput(userInput);
        if (userInput.contains("?")
                || userInput.contains("？")
                || userInput.startsWith("什么")
                || userInput.startsWith("如何")) {
            result.setIntent("question");
        } else if (userInput.startsWith("/") || userInput.startsWith("@")) {
            result.setIntent("command");
        } else {
            result.setIntent("conversation");
        }
        result.setConfidence(0.6);
        return result;
    }

    private String extractJsonField(String json, String field, String defaultValue) {
        var pattern = "\"" + field + "\":\"([^\"]+)\"";
        var matcher = java.util.regex.Pattern.compile(pattern).matcher(json);
        return matcher.find() ? matcher.group(1) : defaultValue;
    }

    private double extractJsonDouble(String json, String field, double defaultValue) {
        var pattern = "\"" + field + "\":(\\d+\\.?\\d*)";
        var matcher = java.util.regex.Pattern.compile(pattern).matcher(json);
        return matcher.find() ? Double.parseDouble(matcher.group(1)) : defaultValue;
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> parseSlots(String json) {
        var result = new HashMap<String, String>();
        var pattern = java.util.regex.Pattern.compile("\"(\\w+)\":\"([^\"]+)\"");
        var matcher = pattern.matcher(json);
        while (matcher.find()) {
            result.put(matcher.group(1), matcher.group(2));
        }
        return result;
    }

    /** 意图分析结果 */
    @Getter
    @Setter
    public static class IntentResult {
        private String rawInput;
        private String intent;
        private double confidence;
        private Map<String, String> slots = Map.of();
        private boolean needsClarification;
        private String clarificationQuestion;
    }
}
