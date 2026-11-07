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

/** 受控知识检索服务：Realtime 会话与文字对话 knowledge_search 工具共用。 */
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
                "检索当前助理已绑定的知识库（见系统消息中的「可用知识库」），返回与检索语句最相关的资料片段。"
                        + "只接受 query 与 topK 参数，不接受知识库或文档 ID；无结果时返回未找到的提示。",
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
        return search(
                new SearchScope(
                        identity.ownerUserId(),
                        identity.orgId(),
                        identity.workspaceId(),
                        context.knowledgeBaseIds()),
                "realtime-knowledge-search",
                query,
                requestedTopK);
    }

    /**
     * 语音与文字对话共用的受控检索：只检索可信范围内的知识库，在所有者身份与组织上下文下执行， 条数与截断规则统一。
     *
     * @param scope 来自可信上下文的执行身份与知识库范围，不接受模型传入
     * @param reason 以所有者身份执行的审计原因
     */
    public SearchResponse search(
            SearchScope scope, String reason, String query, Integer requestedTopK) {
        Objects.requireNonNull(scope, "scope 不能为空");
        var normalizedQuery = normalizeQuery(query);
        var topK = normalizeTopK(requestedTopK);
        requireCompatibleOrgContext(scope.orgId(), scope.workspaceId());

        var result =
                permissionExecutionService.runAsOwner(
                        scope.ownerUserId(),
                        reason,
                        () ->
                                OrgContext.runIfAbsent(
                                        scope.orgId(),
                                        scope.workspaceId(),
                                        () ->
                                                knowledgeSearchApi.search(
                                                        new SearchQuery(
                                                                normalizedQuery,
                                                                scope.knowledgeBaseIds(),
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

    private void requireCompatibleOrgContext(Long orgId, Long workspaceId) {
        var currentOrgId = OrgContext.getCurrentOrgId();
        var currentWorkspaceId = OrgContext.getCurrentWorkspaceId();
        var hasContext =
                currentOrgId != null
                        || currentWorkspaceId != null
                        || OrgContext.isAllOrganizations();
        if (hasContext
                && (OrgContext.isAllOrganizations()
                        || !Objects.equals(currentOrgId, orgId)
                        || !Objects.equals(currentWorkspaceId, workspaceId))) {
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

    /** 可信检索范围：执行身份与知识库集合均由服务端解析，不接受模型传入。 */
    public record SearchScope(
            Long ownerUserId, Long orgId, Long workspaceId, Set<UUID> knowledgeBaseIds) {

        public SearchScope {
            Objects.requireNonNull(ownerUserId, "ownerUserId 不能为空");
            knowledgeBaseIds =
                    Set.copyOf(Objects.requireNonNull(knowledgeBaseIds, "knowledgeBaseIds 不能为空"));
        }
    }
}
