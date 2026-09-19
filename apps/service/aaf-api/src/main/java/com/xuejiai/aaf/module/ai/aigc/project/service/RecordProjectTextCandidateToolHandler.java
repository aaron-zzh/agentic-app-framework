package com.xuejiai.aaf.module.ai.aigc.project.service;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.intelligent.agent.port.ContextAwareToolHandler;
import com.xuejiai.aaf.framework.intelligent.agent.port.ToolInvocationPort.ToolInvocation;
import com.xuejiai.aaf.framework.intelligent.agent.port.ToolInvocationPort.ToolInvocationResult;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectApi;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcTextCandidateCommand;
import com.xuejiai.aaf.module.ai.aigc.project.api.ProjectArtifactCommandApi;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/** 将模型生成的纯文本登记为当前绑定项目对象的不可变 candidate。 */
@Component
public final class RecordProjectTextCandidateToolHandler extends ProjectArtifactCandidateToolSupport
        implements ContextAwareToolHandler {

    public static final String TOOL_NAME = "record_project_text_candidate";

    private final ProjectArtifactCommandApi artifactCommands;

    public RecordProjectTextCandidateToolHandler(
            AigcProjectApi projectApi, ProjectArtifactCommandApi artifactCommands) {
        super(projectApi);
        this.artifactCommands = Objects.requireNonNull(artifactCommands, "artifactCommands 不能为空");
    }

    @Override
    public String toolName() {
        return TOOL_NAME;
    }

    @Override
    public String description() {
        return "将模型已生成的纯文本登记为当前绑定项目对象的不可变 candidate；不会自动采用。";
    }

    @Override
    public boolean reversible() {
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
                List.of("objectId", "content"),
                "properties",
                Map.of(
                        "objectId",
                        Map.of("type", "integer", "minimum", 1, "description", "当前绑定项目中的目标对象"),
                        "content",
                        Map.of("type", "string", "minLength", 1, "maxLength", 1_000_000),
                        "summary",
                        Map.of("type", "string", "maxLength", 500, "description", "可选候选摘要")));
    }

    @Override
    public Mono<ToolInvocationResult> invoke(ToolInvocation invocation) {
        Objects.requireNonNull(invocation, "invocation 不能为空");
        return Mono.fromCallable(() -> record(invocation)).subscribeOn(Schedulers.boundedElastic());
    }

    private ToolInvocationResult record(ToolInvocation invocation) {
        var trusted = requireProject(invocation, TOOL_NAME);
        var arguments = invocation.arguments();
        var candidate =
                artifactCommands.recordTextCandidate(
                        new AigcTextCandidateCommand(
                                trusted.projectId(),
                                requiredPositiveLong(arguments, "objectId", TOOL_NAME),
                                trusted.projectVersion(),
                                trusted.graphRevision(),
                                requiredText(arguments, "content", 1_000_000, TOOL_NAME),
                                optionalText(arguments, "summary", 500, TOOL_NAME),
                                trusted.idempotencyKey()),
                        trusted.invocationContext());
        return candidateResult(candidate.id(), candidate.objectId(), candidate.versionNo());
    }
}
