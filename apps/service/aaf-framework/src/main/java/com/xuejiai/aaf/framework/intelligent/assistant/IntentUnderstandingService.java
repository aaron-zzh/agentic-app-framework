/**
 * 意图理解服务。
 *
 * @author AaronZZH & Kiro
 */
package com.xuejiai.aaf.framework.intelligent.assistant;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

/** 意图理解：多轮意图跟踪、意图消歧、槽位填充。 通过 LLM 分析用户输入，提取意图和槽位。 */
@Service
@RequiredArgsConstructor
public class IntentUnderstandingService {

    /** 分析用户意图。 当前使用规则匹配，后续可升级为 LLM 意图分类。 */
    public IntentResult analyze(String userInput, List<String> conversationHistory) {
        var result = new IntentResult();
        result.setRawInput(userInput);

        // 简单意图分类（后续由 LLM 驱动）
        if (userInput.contains("?")
                || userInput.contains("？")
                || userInput.startsWith("什么")
                || userInput.startsWith("如何")
                || userInput.startsWith("为什么")) {
            result.setIntent("question");
        } else if (userInput.startsWith("/") || userInput.startsWith("@")) {
            result.setIntent("command");
        } else {
            result.setIntent("conversation");
        }

        result.setConfidence(0.8);
        return result;
    }

    /** 槽位填充：从对话上下文中提取缺失参数 */
    public Map<String, String> fillSlots(
            IntentResult intent, Map<String, String> requiredSlots, List<String> history) {
        // TODO: 通过 LLM 从上下文中提取槽位值
        return Map.of();
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
