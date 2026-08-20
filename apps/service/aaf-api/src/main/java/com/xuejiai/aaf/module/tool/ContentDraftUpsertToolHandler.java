package com.xuejiai.aaf.module.tool;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Component;

import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.intelligent.agent.port.ContextAwareToolHandler;
import com.xuejiai.aaf.framework.intelligent.agent.port.ToolInvocationPort.ToolInvocation;
import com.xuejiai.aaf.framework.intelligent.agent.port.ToolInvocationPort.ToolInvocationResult;
import com.xuejiai.aaf.module.document.api.DocumentDraftApi;
import com.xuejiai.aaf.module.document.api.DocumentDraftApi.DraftUpsertCommand;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/** 将 Assistant 规范 Markdown 产物保存为数据库草稿；永不发布。 */
@Component
public final class ContentDraftUpsertToolHandler implements ContextAwareToolHandler {

    public static final String TOOL_NAME = "content.draft.upsert";

    private final DocumentDraftApi documents;

    public ContentDraftUpsertToolHandler(DocumentDraftApi documents) {
        this.documents = Objects.requireNonNull(documents, "documents 不能为空");
    }

    @Override
    public String toolName() {
        return TOOL_NAME;
    }

    @Override
    public String description() {
        return "将 Markdown 内容保存为当前用户、组织和工作区内的数据库草稿；不会发布。";
    }

    @Override
    public Mono<ToolInvocationResult> invoke(ToolInvocation invocation) {
        Objects.requireNonNull(invocation, "invocation 不能为空");
        return Mono.fromCallable(() -> save(invocation)).subscribeOn(Schedulers.boundedElastic());
    }

    private ToolInvocationResult save(ToolInvocation invocation) {
        var context = invocation.context();
        var ownerId = positiveLong(context.userId().value(), "可信 ownerId");
        var orgId = positiveLong(context.tenantId().value(), "可信 orgId");
        var arguments = invocation.arguments();
        rejectTrustedScopeArguments(arguments);
        var saved =
                documents.upsertDraft(
                        new DraftUpsertCommand(
                                text(arguments, "title", 200),
                                text(arguments, "content", 1_000_000),
                                optionalText(arguments, "documentType", 50, "markdown"),
                                ownerId,
                                orgId,
                                context.workspaceId()));
        if (saved == null || saved.id() == null || saved.id() <= 0) {
            throw new IllegalStateException("草稿保存未返回有效 artifactId");
        }
        var metadata = new LinkedHashMap<String, Object>();
        metadata.put("artifactState", "DRAFT");
        metadata.put("artifactType", "DOCUMENT");
        metadata.put("artifactId", saved.id());
        metadata.put("reversible", true);
        metadata.put("completionEvidence", "DRAFT_COMMITTED");
        var output =
                JsonUtils.toJsonString(
                        Map.of(
                                "artifactId",
                                saved.id(),
                                "artifactState",
                                "DRAFT",
                                "published",
                                false));
        return new ToolInvocationResult(output, metadata);
    }

    private static void rejectTrustedScopeArguments(Map<String, Object> arguments) {
        for (var field : java.util.List.of("ownerId", "orgId", "workspaceId", "publish")) {
            if (arguments.containsKey(field)) {
                throw new IllegalArgumentException(TOOL_NAME + " 不接受受信范围或发布参数: " + field);
            }
        }
    }

    private static String text(Map<String, Object> arguments, String field, int maxLength) {
        var value = arguments.get(field);
        if (value == null || value.toString().isBlank()) {
            throw new IllegalArgumentException(TOOL_NAME + " 需要非空 " + field);
        }
        var text = value.toString().trim();
        if (text.length() > maxLength) {
            throw new IllegalArgumentException(field + " 长度不能超过 " + maxLength);
        }
        return text;
    }

    private static String optionalText(
            Map<String, Object> arguments, String field, int maxLength, String defaultValue) {
        var value = arguments.get(field);
        return value == null || value.toString().isBlank()
                ? defaultValue
                : text(arguments, field, maxLength);
    }

    private static long positiveLong(String value, String field) {
        try {
            var parsed = Long.parseLong(value);
            if (parsed > 0) return parsed;
        } catch (NumberFormatException ignored) {
            // 统一在下方拒绝非数字稳定身份。
        }
        throw new IllegalStateException(field + " 不是正整数");
    }
}
