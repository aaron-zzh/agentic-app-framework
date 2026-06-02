package com.xuejiai.aaf.module.ui.aiui;

import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

/** AI UI 服务，调用 LLM 生成组件定义和布局优化。 */
@Service
@RequiredArgsConstructor
public class AiuiService {

    private final ChatClient chatClient;

    private static final String GENERATE_SYSTEM_PROMPT =
            """
            你是一个 UI 组件生成助手。根据用户的自然语言描述，生成符合 AAF EntityDef JSON 格式的实体定义。
            输出必须是合法 JSON，包含 name、slug、fields 数组。每个 field 包含 name、type、label。
            只输出 JSON，不要其他文字。
            """;

    private static final String LAYOUT_SYSTEM_PROMPT =
            """
            你是一个 UI 布局优化助手。根据给定的字段列表，输出优化后的布局配置 JSON。
            布局配置包含 rows 数组，每个 row 包含 columns 数组，每个 column 指定 fieldName 和 span（1-24栅格）。
            只输出 JSON，不要其他文字。
            """;

    private static final String RECOMMEND_SYSTEM_PROMPT =
            """
            你是一个 UI 组件推荐助手。根据上下文信息，推荐适合的组件列表。
            输出 JSON 数组，每项包含 component（组件名）、reason（推荐理由）。
            只输出 JSON，不要其他文字。
            """;

    /** 根据自然语言描述生成 EntityDef JSON */
    public AiuiGenerateVO generate(AiuiGenerateDTO dto) {
        var result =
                chatClient
                        .prompt()
                        .system(GENERATE_SYSTEM_PROMPT)
                        .user(dto.prompt())
                        .call()
                        .content();
        return new AiuiGenerateVO(result, null);
    }

    /** 优化字段布局 */
    public String optimizeLayout(List<String> fields) {
        var userPrompt = "字段列表: " + String.join(", ", fields);
        return chatClient.prompt().system(LAYOUT_SYSTEM_PROMPT).user(userPrompt).call().content();
    }

    /** 根据上下文推荐组件 */
    public String recommend(Map<String, Object> context) {
        var userPrompt = "上下文: " + context.toString();
        return chatClient
                .prompt()
                .system(RECOMMEND_SYSTEM_PROMPT)
                .user(userPrompt)
                .call()
                .content();
    }
}
