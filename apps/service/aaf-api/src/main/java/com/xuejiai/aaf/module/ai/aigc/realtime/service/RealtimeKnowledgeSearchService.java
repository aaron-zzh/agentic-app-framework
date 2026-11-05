package com.xuejiai.aaf.module.ai.aigc.realtime.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.xuejiai.aaf.framework.intelligent.ai.realtime.RealtimeConversationConfig.FunctionTool;
import com.xuejiai.aaf.framework.org.OrgContext;
import com.xuejiai.aaf.framework.security.PermissionExecutionService;
import com.xuejiai.aaf.module.ai.assistant.api.AssistantConversationContextApi.ConversationContext;
import com.xuejiai.aaf.module.ai.assistant.api.AssistantConversationContextApi.ConversationIdentity;
import com.xuejiai.aaf.module.knowledge.api.KnowledgeSearchApi;
import com.xuejiai.aaf.module.knowledge.api.KnowledgeSearchApi.SearchQuery;

import lombok.RequiredArgsConstructor;

/** Realtime 会话受控知识检索服务。 */
@Service
@RequiredArgsConstructor
public class RealtimeKnowledgeSearchService {

    public static final String TOOL_NAME = "knowledge_search";

    private static final int DEFAULT_TOP_K = 3;
    private static final int MAX_TOP_K = 5;
    private static final int MAX_QUERY_LENGTH = 500;
    private static final int MAX_RESULT_CONTENT_LENGTH = 1_500;
    private static final int MAX_TOTAL_CONTENT_LENGTH = 6_000;

    private final KnowledgeSearchApi knowledgeSearchApi;
    private final PermissionExecutionService permissionExecutionService;

    /** 返回服务端保留的固定知识检索工具声明。 */
    public FunctionTool toolDefinition() {
        return new FunctionTool(
                TOOL_NAME,
                "从当前助理已绑定的知识库检索与用户问题相关的内容。当问题需要查证你训练知识之外的资料（如专业规范、政策条款、操作教程等，见系统消息中的「可用知识库」）时调用。",
                Map.of(
                        "type",
                        "object",
                        "properties",
                        Map.of(
                                "query",
                                Map.of(
                                        "type",
                                        "string",
                                        "description",
                                        "完整自然语言检索语句，覆盖问题核心实体与术语，不要只写关键词"),
                                "topK",
                                Map.of("type", "integer", "minimum", 1, "maximum", MAX_TOP_K)),
                        "required",
                        List.of("query"),
                        "additionalProperties",
                        false));
    }

    /** 使用会话建立时冻结的身份和知识库范围执行检索。 */
    public SearchResponse search(
            ConversationIdentity identity,
            ConversationContext context,
            String query,
            Integer requestedTopK) {
        Objects.requireNonNull(identity, "identity 不能为空");
        Objects.requireNonNull(context, "context 不能为空");
        var normalizedQuery = normalizeQuery(query);
        var topK = normalizeTopK(requestedTopK);
        requireCompatibleOrgContext(identity);

        var result =
                permissionExecutionService.runAsOwner(
                        identity.ownerUserId(),
                        "realtime-knowledge-search",
                        () ->
                                OrgContext.runIfAbsent(
                                        identity.orgId(),
                                        identity.workspaceId(),
                                        () ->
                                                knowledgeSearchApi.search(
                                                        new SearchQuery(
                                                                normalizedQuery,
                                                                context.knowledgeBaseIds(),
                                                                Set.of(),
                                                                topK,
                                                                0.5))));
        return successResponse(result);
    }

    private SearchResponse successResponse(KnowledgeSearchApi.SearchResult result) {
        var items = new ArrayList<SearchItem>();
        var remainingContentLength = MAX_TOTAL_CONTENT_LENGTH;
        for (var hit : result.hits()) {
            if (remainingContentLength == 0) {
                break;
            }
            var content =
                    truncate(
                            Objects.requireNonNull(hit.content(), "检索结果 content 不能为空"),
                            Math.min(MAX_RESULT_CONTENT_LENGTH, remainingContentLength));
            remainingContentLength -= content.codePointCount(0, content.length());
            var source = Objects.requireNonNull(hit.source(), "检索结果 source 不能为空");
            items.add(
                    new SearchItem(
                            content,
                            hit.score(),
                            new SearchSource(
                                    source.knowledgeBaseId(),
                                    source.documentId(),
                                    source.sourceKey(),
                                    source.sourceUri())));
        }
        return new SearchResponse(
                true,
                "OK",
                "检索完成",
                items,
                result.degradedChannels() == null ? Set.of() : result.degradedChannels());
    }

    private String normalizeQuery(String query) {
        Objects.requireNonNull(query, "query 不能为空");
        var normalized = query.trim();
        var length = normalized.codePointCount(0, normalized.length());
        if (length < 1 || length > MAX_QUERY_LENGTH) {
            throw new IllegalArgumentException("query 长度必须在 1 到 500 字符之间");
        }
        return normalized;
    }

    private int normalizeTopK(Integer requestedTopK) {
        var topK = requestedTopK == null ? DEFAULT_TOP_K : requestedTopK;
        if (topK < 1 || topK > MAX_TOP_K) {
            throw new IllegalArgumentException("topK 必须在 1 到 5 之间");
        }
        return topK;
    }

    private void requireCompatibleOrgContext(ConversationIdentity identity) {
        var currentOrgId = OrgContext.getCurrentOrgId();
        var currentWorkspaceId = OrgContext.getCurrentWorkspaceId();
        var hasContext =
                currentOrgId != null
                        || currentWorkspaceId != null
                        || OrgContext.isAllOrganizations();
        if (hasContext
                && (OrgContext.isAllOrganizations()
                        || !Objects.equals(currentOrgId, identity.orgId())
                        || !Objects.equals(currentWorkspaceId, identity.workspaceId()))) {
            throw new IllegalStateException("当前组织上下文与 Realtime 会话冻结上下文冲突");
        }
    }

    private String truncate(String value, int maxLength) {
        var length = value.codePointCount(0, value.length());
        if (length <= maxLength) {
            return value;
        }
        return value.substring(0, value.offsetByCodePoints(0, maxLength));
    }

    /** 提交给模型的精简检索结果。 */
    public record SearchResponse(
            boolean success,
            String code,
            String message,
            List<SearchItem> results,
            Set<String> degradedChannels) {

        public SearchResponse {
            code = Objects.requireNonNull(code, "code 不能为空");
            message = Objects.requireNonNull(message, "message 不能为空");
            results = List.copyOf(Objects.requireNonNull(results, "results 不能为空"));
            degradedChannels =
                    Collections.unmodifiableSet(
                            new LinkedHashSet<>(
                                    Objects.requireNonNull(
                                            degradedChannels, "degradedChannels 不能为空")));
        }

        public static SearchResponse failure(String code, String message) {
            return new SearchResponse(false, code, message, List.of(), Set.of());
        }
    }

    /** 单条精简检索结果。 */
    public record SearchItem(String content, double score, SearchSource source) {

        public SearchItem {
            content = Objects.requireNonNull(content, "content 不能为空");
            Objects.requireNonNull(source, "source 不能为空");
        }
    }

    /** 检索来源白名单字段。 */
    public record SearchSource(
            UUID knowledgeBaseId, UUID documentId, String sourceKey, String sourceUri) {}
}
