package com.xuejiai.aaf.module.ai.aigc.project.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Component;

import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.intelligent.agent.port.ContextAwareToolHandler;
import com.xuejiai.aaf.framework.intelligent.agent.port.ToolInvocationPort.ToolInvocation;
import com.xuejiai.aaf.framework.intelligent.agent.port.ToolInvocationPort.ToolInvocationResult;
import com.xuejiai.aaf.module.ai.aigc.project.api.ContentStudioProjectContextProvider;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/** 读取当前 InvocationContext 绑定项目的紧凑语义目录，不接受模型指定项目或工作区。 */
@Component
public final class InspectProjectSemanticsToolHandler implements ContextAwareToolHandler {

    public static final String TOOL_NAME = "inspect_project_semantics";

    private final ContentStudioProjectContextProvider projectContextProvider;

    public InspectProjectSemanticsToolHandler(
            ContentStudioProjectContextProvider projectContextProvider) {
        this.projectContextProvider =
                Objects.requireNonNull(projectContextProvider, "projectContextProvider 不能为空");
    }

    @Override
    public String toolName() {
        return TOOL_NAME;
    }

    @Override
    public String description() {
        return "读取当前执行上下文已绑定项目的语义、图修订、对象 readiness 与允许动作；" + "不接受 projectId、workspaceId 或其他项目范围参数。";
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
                "properties",
                Map.of(
                        "focusedObjectId",
                        Map.of(
                                "type",
                                "integer",
                                "minimum",
                                1,
                                "description",
                                "可选：仅聚焦当前已绑定项目中的一个对象")));
    }

    @Override
    public Mono<ToolInvocationResult> invoke(ToolInvocation invocation) {
        Objects.requireNonNull(invocation, "invocation 不能为空");
        return Mono.fromCallable(() -> inspect(invocation))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private ToolInvocationResult inspect(ToolInvocation invocation) {
        var context = invocation.context();
        rejectUntrustedScope(invocation.arguments());
        var projectId = context.projectId();
        if (projectId == null) {
            throw new IllegalArgumentException(
                    "inspect_project_semantics 需要 InvocationContext 绑定 projectId");
        }
        var workspaceId = context.workspaceId();
        if (workspaceId == null) {
            throw new IllegalArgumentException(
                    "inspect_project_semantics 需要 InvocationContext 绑定 workspaceId");
        }
        var snapshot =
                projectContextProvider.load(
                        new ContentStudioProjectContextProvider.ProjectScope(
                                projectId, workspaceId),
                        optionalPositiveLong(invocation.arguments(), "focusedObjectId"));
        var result = new LinkedHashMap<String, Object>();
        result.put("projectId", snapshot.projectId());
        result.put("projectVersion", snapshot.projectVersion());
        result.put("graphRevision", snapshot.graphRevision());
        result.put("lifecycle", snapshot.lifecycle());
        result.put("generationMode", snapshot.generationMode());
        result.put("semanticCatalogVersion", snapshot.semanticCatalogVersion());
        result.put("focusedObject", snapshot.focusedObject());
        result.put("semanticSubset", snapshot.semanticSubset());
        result.put("readiness", snapshot.readiness());
        result.put("allowedActions", snapshot.allowedActions());
        return new ToolInvocationResult(
                JsonUtils.toJsonString(result),
                Map.of(
                        "projectId", snapshot.projectId(),
                        "projectVersion", snapshot.projectVersion(),
                        "graphRevision", snapshot.graphRevision(),
                        "toolUnits", 1));
    }

    private static void rejectUntrustedScope(Map<String, Object> arguments) {
        var prohibited =
                java.util.Set.of("projectid", "workspaceid", "domain", "binding", "relationlayer");
        for (var field : arguments.keySet()) {
            var normalized =
                    field.replaceAll("[^A-Za-z0-9]", "").toLowerCase(java.util.Locale.ROOT);
            if (prohibited.contains(normalized)) {
                throw new IllegalArgumentException(TOOL_NAME + " 不接受受信项目范围参数: " + field);
            }
        }
    }

    private static Long optionalPositiveLong(Map<String, Object> arguments, String field) {
        var value = arguments.get(field);
        if (value == null) {
            return null;
        }
        if (!(value instanceof Number number) || number.longValue() < 1) {
            throw new IllegalArgumentException(TOOL_NAME + " 的 " + field + " 必须为正整数");
        }
        return number.longValue();
    }
}
