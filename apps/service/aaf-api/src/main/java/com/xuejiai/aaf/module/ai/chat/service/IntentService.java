package com.xuejiai.aaf.module.ai.chat.service;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.xuejiai.aaf.module.ai.chat.vo.IntentResult;

/**
 * 意图识别服务，基于关键词匹配实现轻量分类。
 *
 * @author AaronZZH & Kiro
 */
@Service
public class IntentService {

    private static final List<String> QUERY_KEYWORDS = List.of("查找", "搜索", "查询", "最近");
    private static final List<String> CREATE_KEYWORDS = List.of("创建", "新建", "添加");
    private static final List<String> EDIT_KEYWORDS = List.of("修改", "更新", "编辑", "把");
    private static final List<String> NAVIGATE_KEYWORDS = List.of("打开", "跳转", "去", "导航");

    private static final Map<String, String> INTENT_ACTION_MAP =
            Map.of(
                    "QUERY", "search_entities",
                    "CREATE", "create_entity",
                    "EDIT", "edit_entity",
                    "NAVIGATE", "navigate_to",
                    "CHAT", "chat_reply");

    /**
     * 对用户输入进行意图分类
     *
     * @param userInput 用户输入文本
     * @return 意图识别结果
     */
    public IntentResult classify(String userInput) {
        if (userInput == null || userInput.isBlank()) {
            return new IntentResult("CHAT", 1.0, INTENT_ACTION_MAP.get("CHAT"));
        }

        if (containsAny(userInput, QUERY_KEYWORDS)) {
            return buildResult("QUERY");
        }
        if (containsAny(userInput, CREATE_KEYWORDS)) {
            return buildResult("CREATE");
        }
        if (containsAny(userInput, EDIT_KEYWORDS)) {
            return buildResult("EDIT");
        }
        if (containsAny(userInput, NAVIGATE_KEYWORDS)) {
            return buildResult("NAVIGATE");
        }

        return new IntentResult("CHAT", 0.5, INTENT_ACTION_MAP.get("CHAT"));
    }

    private IntentResult buildResult(String intent) {
        return new IntentResult(intent, 0.9, INTENT_ACTION_MAP.get(intent));
    }

    private boolean containsAny(String input, List<String> keywords) {
        return keywords.stream().anyMatch(input::contains);
    }
}
