package com.xuejiai.aaf.module.tool.websearch;

import java.util.List;
import java.util.Map;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.engine.tool.ToolCallDispatcher.ToolCallResult;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 联网搜索工具——检索互联网公开网页，供 AI Agent 通过 Function Calling 调用。
 *
 * <p>工具名：{@code webSearch}，返回标题、链接、摘要与发布时间。查询词会发送到第三方搜索服务， 因此拒绝包含手机号、身份证号的查询词，避免个人敏感信息外泄。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSearchTool {

    static final String TOOL_NAME = "webSearch";

    /** 查询词长度上限，与 IQS 接口限制（1~1024 字符）一致。 */
    private static final int MAX_QUERY_LENGTH = 1024;

    private static final int DEFAULT_COUNT = 5;
    private static final int MAX_COUNT = 10;

    /** 单条摘要最大长度，控制回灌给模型的上下文体积。 */
    private static final int MAX_SNIPPET_LENGTH = 500;

    private final IqsWebSearchClient searchClient;

    @Tool(
            description =
                    "联网搜索互联网公开网页，获取最新资讯、政策、新闻、百科等实时信息。"
                            + "返回结果列表（标题、链接、摘要、发布时间），回答时请引用来源链接。"
                            + "参数：query（搜索关键词，必填，简明扼要）、count（返回条数，1-10，默认 5）。"
                            + "严禁在 query 中包含用户姓名、手机号、身份证号、详细病史等个人隐私信息，"
                            + "只保留检索所需的通用关键词。")
    public String webSearch(
            @ToolParam(description = "搜索关键词，不得包含个人隐私信息") String query,
            @ToolParam(description = "返回条数，1-10，默认 5", required = false) Integer count) {
        if (query == null || query.isBlank()) {
            return error("INVALID_QUERY", "搜索关键词不能为空");
        }
        var trimmed = query.trim();
        if (trimmed.length() > MAX_QUERY_LENGTH) {
            return error("INVALID_QUERY", "搜索关键词不能超过 " + MAX_QUERY_LENGTH + " 个字符");
        }
        if (ToolQueryGuard.containsSensitiveNumber(trimmed)) {
            return error("SENSITIVE_QUERY", "搜索关键词包含手机号或身份证号，请去除个人隐私信息后重试");
        }
        var limit = count == null ? DEFAULT_COUNT : Math.min(MAX_COUNT, Math.max(1, count));
        try {
            var result = searchClient.search(trimmed, limit);
            List<Map<String, String>> items =
                    result.items().stream()
                            .map(
                                    item ->
                                            Map.of(
                                                    "title", item.title(),
                                                    "link", item.link(),
                                                    "snippet",
                                                            truncate(
                                                                    item.snippet(),
                                                                    MAX_SNIPPET_LENGTH),
                                                    "publishedTime", item.publishedTime(),
                                                    "source", item.hostname()))
                            .toList();
            var output = JsonUtils.toJsonString(Map.of("count", items.size(), "results", items));
            return JsonUtils.toJsonString(ToolCallResult.success(TOOL_NAME, output));
        } catch (Exception e) {
            // 查询词可能含用户输入，日志只记录长度，不记录原文
            log.error(
                    "[WebSearchTool] 搜索失败: queryLength={}, err={}",
                    trimmed.length(),
                    e.getMessage());
            return error("WEB_SEARCH_ERROR", e.getMessage());
        }
    }

    private static String error(String code, String message) {
        return JsonUtils.toJsonString(ToolCallResult.error(TOOL_NAME, code, message));
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max) + "...";
    }
}
