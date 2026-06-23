package com.xuejiai.aaf.module.ai.aigc.trending;

import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Service;

import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.intelligent.ai.chat.ResilientChatService;
import com.xuejiai.aaf.framework.intelligent.core.model.CapabilityRoutingContext;
import com.xuejiai.aaf.framework.security.OperatorContext;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.type.TypeReference;

/**
 * 热点搜索服务——通过 DashScope enable_search 实时抓取热榜并格式化为结构化列表。
 *
 * <p>固定模型：qwen:qwen3.7-plus，enable_search=true 通过 extraBody 透传。 复用 ResilientChatService 保证积分预检与扣减。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrendingService {

    /** 固定模型 ID（对应 ai_model 表 model_id 字段）。 */
    private static final String MODEL_ID = "qwen:qwen3.7-plus";

    private static String buildSysPrompt() {
        String today = java.time.LocalDate.now().toString(); // e.g. 2026-06-24
        return """
            你是热点分析助手。请通过联网搜索，获取**今天**（%s）中国互联网最新热点（微博/抖音/知乎等主流平台），
            返回严格的 JSON 数组，包含 20 条热点，每条格式如下：
            {
              "rank": 序号(1-20),
              "title": "热点标题",
              "summary": "1-2句简短摘要",
              "tag": "爆款/上升/新闻/娱乐/科技/社会（选一）",
              "suggestion": "一句话内容创作借势建议"
            }
            只输出 JSON 数组，不要有任何前缀、解释或 markdown 代码块。
            """
                .formatted(today);
    }

    private final ResilientChatService chatService;
    private final OperatorContext operatorContext;

    /**
     * 同步获取热点列表。
     *
     * @return 20 条热点，解析失败时返回空列表
     */
    public List<TrendingItem> fetchTrending() {
        Long userId = operatorContext.currentUserId().orElse(null);
        var ctx = CapabilityRoutingContext.of(userId, CapabilityRoutingContext.CAP_CHAT, MODEL_ID);
        // enable_search 为 DashScope 非标准参数，通过 extraBody 透传
        var options = OpenAiChatOptions.builder().extraBody(Map.of("enable_search", true)).build();
        log.info("[热点搜索] 发起请求，enable_search=true, model={}", MODEL_ID);
        var messages =
                List.<org.springframework.ai.chat.messages.Message>of(
                        new SystemMessage(buildSysPrompt()),
                        new UserMessage("请获取当前最新热点，返回 20 条 JSON 列表。"));
        try {
            var response = chatService.call(messages, ctx, options);
            var content = response.getResult().getOutput().getText();
            log.info("[热点搜索] 原始响应长度={}", content != null ? content.length() : 0);
            return parseItems(content);
        } catch (Exception e) {
            log.error("[热点搜索] 调用失败: {}", e.getMessage(), e);
            return List.of();
        }
    }

    private List<TrendingItem> parseItems(String content) {
        if (content == null || content.isBlank()) return List.of();
        String json = content.strip();
        if (json.startsWith("```")) {
            json = json.replaceAll("^```[a-z]*\\n?", "").replaceAll("```$", "").strip();
        }
        // 提取第一个 JSON 数组（防止 AI 在数组前后附加说明文字）
        int start = json.indexOf('[');
        int end = json.lastIndexOf(']');
        if (start < 0 || end < 0 || end <= start) {
            log.warn(
                    "[热点搜索] 未找到 JSON 数组，响应内容: {}",
                    json.length() > 200 ? json.substring(0, 200) : json);
            return List.of();
        }
        json = json.substring(start, end + 1);
        try {
            List<TrendingItem> items =
                    JsonUtils.parseObject(json, new TypeReference<List<TrendingItem>>() {});
            // 过滤字段不完整的条目
            var valid =
                    items.stream()
                            .filter(
                                    it ->
                                            it != null
                                                    && it.title() != null
                                                    && !it.title().isBlank()
                                                    && it.summary() != null
                                                    && !it.summary().isBlank())
                            .limit(20)
                            .toList();
            log.info("[热点搜索] 解析完成，有效条数={}/{}", valid.size(), items.size());
            return valid;
        } catch (Exception e) {
            log.warn("[热点搜索] JSON 解析失败: {}", e.getMessage());
            return List.of();
        }
    }
}
