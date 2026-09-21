package com.xuejiai.aaf.module.ai.aigc.execution.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.intelligent.agent.port.ContextAwareToolHandler;
import com.xuejiai.aaf.framework.intelligent.agent.port.ToolInvocationPort.ToolInvocation;
import com.xuejiai.aaf.framework.intelligent.agent.port.ToolInvocationPort.ToolInvocationResult;
import com.xuejiai.aaf.module.ai.aigc.execution.api.AigcActionCommand;
import com.xuejiai.aaf.module.ai.aigc.execution.api.AigcExecutionApi;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcApprovedGenerationTarget;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcGenerationProposalBridgeApi;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcGenerationProposalTargetType;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectApi;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import tools.jackson.core.type.TypeReference;

/**
 * 已确认生成目标 → 确定性动作的唯一桥接 Tool。
 *
 * <p>模型不提供任何参数：Proposal 身份来自 {@code InvocationContext.taskId}，项目范围来自执行画像冻结的受信 {@code
 * projectId}，目标对象由服务端按目标 key 在当前图谱中定位。动作提交后由既有执行链创建 root {@code
 * AigcExecutionRun}，并把解析出的已确认目标整体冻结为执行证据；本 Tool 不写 ProjectGraph、candidate、积分或交付真理源。
 */
@Component
public final class SubmitApprovedGenerationActionToolHandler implements ContextAwareToolHandler {

    public static final String TOOL_NAME = "aigc.project.generation.action.submit";

    private final AigcGenerationProposalBridgeApi proposalBridge;
    private final AigcProjectApi projects;
    private final AigcExecutionApi executions;

    public SubmitApprovedGenerationActionToolHandler(
            AigcGenerationProposalBridgeApi proposalBridge,
            AigcProjectApi projects,
            AigcExecutionApi executions) {
        this.proposalBridge = Objects.requireNonNull(proposalBridge, "proposalBridge 不能为空");
        this.projects = Objects.requireNonNull(projects, "projects 不能为空");
        this.executions = Objects.requireNonNull(executions, "executions 不能为空");
    }

    @Override
    public String toolName() {
        return TOOL_NAME;
    }

    @Override
    public String description() {
        return "提交当前可信 Task 某个已确认生成目标；只接受 itemKey，且必须落在服务端已批准范围内，项目版本与图谱 revision 由服务端解析。";
    }

    @Override
    public boolean reversible() {
        return false;
    }

    @Override
    public Map<String, Object> inputSchema() {
        return JsonUtils.parseObject(
                """
                {
                  "type": "object",
                  "additionalProperties": false,
                  "required": ["itemKey"],
                  "properties": {
                    "itemKey": {"type": "string", "minLength": 1, "maxLength": 128}
                  }
                }
                """,
                new TypeReference<Map<String, Object>>() {});
    }

    @Override
    public Mono<ToolInvocationResult> invoke(ToolInvocation invocation) {
        Objects.requireNonNull(invocation, "invocation 不能为空");
        return Mono.fromCallable(() -> submit(invocation)).subscribeOn(Schedulers.boundedElastic());
    }

    private ToolInvocationResult submit(ToolInvocation invocation) {
        var context = invocation.context();
        if (context.taskId() == null || context.executionId() == null) {
            throw new IllegalStateException(TOOL_NAME + " 只能由 canonical Task 调用");
        }
        var arguments = invocation.arguments();
        if (!arguments.keySet().equals(Set.of("itemKey"))) {
            throw new IllegalArgumentException(TOOL_NAME + " 参数必须且只能包含 itemKey");
        }
        if (context.projectId() == null) {
            throw new IllegalStateException(TOOL_NAME + " 缺少受信项目范围");
        }
        var itemKey = String.valueOf(arguments.get("itemKey"));
        var target =
                proposalBridge.resolveApprovedTargetByLinkedTask(
                        context.taskId().value(), context.projectId(), itemKey);
        var run =
                executions.submit(
                        new AigcActionCommand(
                                target.projectId(),
                                resolveProjectObjectId(target),
                                target.actionKey(),
                                target.generationInstruction(),
                                null,
                                Map.of(),
                                java.util.List.of(),
                                java.util.List.of(),
                                target.projectVersion(),
                                target.graphRevision(),
                                true,
                                idempotencyKey(target),
                                target));
        var metadata = new LinkedHashMap<String, Object>();
        metadata.put("proposalId", target.proposalId());
        metadata.put("proposalVersion", target.proposalVersion());
        metadata.put("itemKey", target.itemKey());
        metadata.put("actionKey", target.actionKey());
        metadata.put("executionRunId", run.id());
        metadata.put("reversible", false);
        var output =
                JsonUtils.toJsonString(
                        Map.of(
                                "executionRunId", run.id(),
                                "status", run.status().name(),
                                "actionKey", target.actionKey(),
                                "itemKey", target.itemKey()));
        return new ToolInvocationResult(output, metadata);
    }

    /**
     * 按已确认目标定位项目对象。
     *
     * <p>对象类目标必须在当前图谱中按 stableKey 唯一命中；项目级目标不带对象，由动作链自行归属成本。
     */
    private Long resolveProjectObjectId(AigcApprovedGenerationTarget target) {
        if (target.targetType() != AigcGenerationProposalTargetType.PROJECT_OBJECT) {
            return null;
        }
        return projects.getGraph(target.projectId()).objects().stream()
                .filter(object -> target.targetKey().equals(object.stableKey()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("已确认生成目标对应的项目对象不存在"))
                .id();
    }

    /** 幂等键由已确认目标身份确定性派生，重放收敛同一 submission。 */
    private static String idempotencyKey(AigcApprovedGenerationTarget target) {
        return "proposal-action:%s:%d:%s"
                .formatted(target.proposalId(), target.proposalVersion(), target.itemKey());
    }
}
