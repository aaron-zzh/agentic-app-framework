package com.xuejiai.aaf.module.ai.chat.service;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

/** 提供欢迎页静态建议，不参与 Assistant 执行。 */
@Service
public class WelcomeSuggestionService {

    private static final Map<String, List<Map<String, String>>> DEFAULT_SUGGESTIONS =
            Map.of(
                    "default",
                            List.of(
                                    Map.of("prompt", "帮我生成一段营销文案"),
                                    Map.of("prompt", "用 AI 写一篇博客文章"),
                                    Map.of("prompt", "如何用 AI 提升工作效率？")),
                    "kiro",
                            List.of(
                                    Map.of("prompt", "帮我生成一个 REST 接口"),
                                    Map.of("prompt", "用 AI 分析并重构这段代码"),
                                    Map.of("prompt", "自动生成单元测试")));

    public List<Map<String, String>> getWelcomeSuggestions(String agentId) {
        return DEFAULT_SUGGESTIONS.getOrDefault(agentId, DEFAULT_SUGGESTIONS.get("default"));
    }
}
