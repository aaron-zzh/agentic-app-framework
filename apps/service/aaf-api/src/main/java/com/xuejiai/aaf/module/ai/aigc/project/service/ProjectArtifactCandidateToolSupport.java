package com.xuejiai.aaf.module.ai.aigc.project.service;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.intelligent.agent.model.InvocationContext;
import com.xuejiai.aaf.framework.intelligent.agent.port.ToolInvocationPort.ToolInvocation;
import com.xuejiai.aaf.framework.intelligent.agent.port.ToolInvocationPort.ToolInvocationResult;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectApi;

/** 项目 candidate Tool 的共同受信上下文、CAS 与输出封装；模型不得提供项目范围或版本参数。 */
abstract class ProjectArtifactCandidateToolSupport {

    private final AigcProjectApi projectApi;

    ProjectArtifactCandidateToolSupport(AigcProjectApi projectApi) {
        this.projectApi = projectApi;
    }

    protected final TrustedProject requireProject(ToolInvocation invocation, String toolName) {
        var context = invocation.context();
        rejectUntrustedParameters(invocation.arguments(), toolName);
        var projectId = context.projectId();
        if (projectId == null) {
            throw new IllegalArgumentException(toolName + " 需要 InvocationContext 绑定 projectId");
        }
        var project = projectApi.requireProject(projectId);
        var graph = projectApi.getGraph(projectId);
        return new TrustedProject(
                projectId,
                project.version(),
                graph.revisionNo(),
                context,
                stableIdempotencyKey(invocation));
    }

    protected static Long requiredPositiveLong(
            Map<String, Object> arguments, String field, String toolName) {
        var value = arguments.get(field);
        if (!(value instanceof Number number) || number.longValue() < 1) {
            throw new IllegalArgumentException(toolName + " 的 " + field + " 必须为正整数");
        }
        return number.longValue();
    }

    protected static String requiredText(
            Map<String, Object> arguments, String field, int maxLength, String toolName) {
        var value = arguments.get(field);
        if (value == null || value.toString().isBlank()) {
            throw new IllegalArgumentException(toolName + " 需要非空 " + field);
        }
        var text = value.toString().trim();
        if (text.length() > maxLength) {
            throw new IllegalArgumentException(toolName + " 的 " + field + " 长度不能超过 " + maxLength);
        }
        return text;
    }

    protected static String optionalText(
            Map<String, Object> arguments, String field, int maxLength, String toolName) {
        var value = arguments.get(field);
        if (value == null || value.toString().isBlank()) {
            return null;
        }
        var text = value.toString().trim();
        if (text.length() > maxLength) {
            throw new IllegalArgumentException(toolName + " 的 " + field + " 长度不能超过 " + maxLength);
        }
        return text;
    }

    protected static ToolInvocationResult candidateResult(
            Long candidateVersionId, Long objectId, Integer versionNo) {
        var output =
                Map.of(
                        "candidateVersionId", candidateVersionId,
                        "objectId", objectId,
                        "versionNo", versionNo,
                        "status", "candidate");
        return new ToolInvocationResult(
                JsonUtils.toJsonString(output),
                Map.of(
                        "candidateVersionId", candidateVersionId,
                        "objectId", objectId,
                        "versionNo", versionNo,
                        "toolUnits", 1));
    }

    private static void rejectUntrustedParameters(Map<String, Object> arguments, String toolName) {
        var prohibited =
                Set.of(
                        "projectid",
                        "workspaceid",
                        "expectedprojectversion",
                        "expectedgraphrevision",
                        "idempotencykey",
                        "binding",
                        "relationlayer");
        for (var field : arguments.keySet()) {
            var normalized = field.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
            if (prohibited.contains(normalized)) {
                throw new IllegalArgumentException(toolName + " 不接受受信参数: " + field);
            }
        }
    }

    private static String stableIdempotencyKey(ToolInvocation invocation) {
        var key = invocation.context().idempotencyKey();
        if (key == null || key.value().isBlank()) {
            throw new IllegalStateException("项目 candidate Tool 缺少 InvocationContext 幂等键");
        }
        return key.value() + ":" + invocation.toolCallId();
    }

    protected record TrustedProject(
            Long projectId,
            Integer projectVersion,
            Long graphRevision,
            InvocationContext invocationContext,
            String idempotencyKey) {}
}
