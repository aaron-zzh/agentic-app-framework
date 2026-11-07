package com.xuejiai.aaf.module.ai.assistant.service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.intelligent.agent.port.ContextAwareToolHandler;
import com.xuejiai.aaf.framework.intelligent.agent.port.ToolInvocationPort.ToolInvocation;
import com.xuejiai.aaf.framework.intelligent.agent.port.ToolInvocationPort.ToolInvocationResult;
import com.xuejiai.aaf.framework.intelligent.assistant.application.BuiltinControlToolProfile;
import com.xuejiai.aaf.module.ai.aigc.realtime.service.RealtimeKnowledgeSearchService;
import com.xuejiai.aaf.module.ai.aigc.realtime.service.RealtimeKnowledgeSearchService.SearchResponse;
import com.xuejiai.aaf.module.ai.aigc.realtime.service.RealtimeKnowledgeSearchService.SearchScope;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/** 文字对话的角色知识库按需检索工具：检索范围只取 InvocationContext 中服务端冻结的知识库，不接受模型指定知识库或文档。 */
@Component
public final class KnowledgeSearchToolHandler implements ContextAwareToolHandler {

    public static final String TOOL_NAME = BuiltinControlToolProfile.KNOWLEDGE_SEARCH_TOOL_NAME;

    private static final Set<String> ALLOWED_ARGUMENTS = Set.of("query", "topK");

    private static final int MAX_TOP_K = 5;

    private final RealtimeKnowledgeSearchService knowledgeSearchService;

    public KnowledgeSearchToolHandler(RealtimeKnowledgeSearchService knowledgeSearchService) {
        this.knowledgeSearchService =
                Objects.requireNonNull(knowledgeSearchService, "knowledgeSearchService 不能为空");
    }

    @Override
    public String toolName() {
        return TOOL_NAME;
    }

    @Override
    public String description() {
        return "检索当前角色已授权的知识库，返回相关片段与来源。用户询问产品、服务、政策、规范、操作说明等事实性问题时，应先调用本工具再回答；"
                + "寒暄或与知识库无关的问题无需调用。检索结果为空时如实告知未找到相关资料，不要编造。"
                + "只接受 query 与 topK 参数，不接受知识库或文档 ID。";
    }

    @Override
    public boolean readOnly() {
        return true;
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type",
                "object",
                "additionalProperties",
                false,
                "required",
                List.of("query"),
                "properties",
                Map.of(
                        "query",
                        Map.of("type", "string", "description", "完整自然语言检索语句，覆盖问题核心实体与术语，不要只写关键词"),
                        "topK",
                        Map.of(
                                "type",
                                "integer",
                                "minimum",
                                1,
                                "maximum",
                                MAX_TOP_K,
                                "description",
                                "可选：返回片段数量，默认 3")));
    }

    @Override
    public Mono<ToolInvocationResult> invoke(ToolInvocation invocation) {
        Objects.requireNonNull(invocation, "invocation 不能为空");
        return Mono.fromCallable(() -> search(invocation)).subscribeOn(Schedulers.boundedElastic());
    }

    private ToolInvocationResult search(ToolInvocation invocation) {
        var arguments = invocation.arguments();
        rejectUntrustedArguments(arguments);
        var context = invocation.context();
        var knowledgeBaseIds = context.knowledgeBaseIds();
        if (knowledgeBaseIds.isEmpty()) {
            return result(SearchResponse.failure("NO_KNOWLEDGE_BASE", "当前角色未配置知识库，无法检索"));
        }
        var scope =
                new SearchScope(
                        parseLong(context.userId().value(), "userId"),
                        parseLong(context.tenantId().value(), "tenantId"),
                        context.workspaceId(),
                        knowledgeBaseIds);
        var response =
                knowledgeSearchService.search(
                        scope,
                        "assistant-knowledge-search",
                        requireQuery(arguments.get("query")),
                        optionalTopK(arguments.get("topK")));
        if (response.results().isEmpty()) {
            return result(
                    new SearchResponse(
                            true,
                            "NO_MATCH",
                            "未检索到相关资料，请如实告知用户",
                            response.results(),
                            response.degradedChannels()));
        }
        return result(response);
    }

    private static ToolInvocationResult result(SearchResponse response) {
        return new ToolInvocationResult(
                JsonUtils.toJsonString(response),
                Map.of("resultCount", response.results().size(), "toolUnits", 1));
    }

    private static void rejectUntrustedArguments(Map<String, Object> arguments) {
        for (var field : arguments.keySet()) {
            if (!ALLOWED_ARGUMENTS.contains(field)) {
                throw new IllegalArgumentException(
                        TOOL_NAME + " 只接受 query 与 topK，不接受知识库、文档或其他范围参数: " + field);
            }
        }
    }

    private static String requireQuery(Object value) {
        if (!(value instanceof String query) || query.isBlank()) {
            throw new IllegalArgumentException(TOOL_NAME + " 的 query 必须为非空字符串");
        }
        return query;
    }

    private static Integer optionalTopK(Object value) {
        if (value == null) {
            return null;
        }
        if (!(value instanceof Number number)
                || number.doubleValue() != Math.rint(number.doubleValue())) {
            throw new IllegalArgumentException(TOOL_NAME + " 的 topK 必须为整数");
        }
        return number.intValue();
    }

    private static Long parseLong(String value, String field) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException failure) {
            throw new IllegalStateException(TOOL_NAME + " 的可信上下文 " + field + " 不是数值: " + value);
        }
    }
}
