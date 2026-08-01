package com.xuejiai.aaf.module.company.workflow;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

/**
 * WorkflowExecutor stub——v1 实现已归档，待对接新 agentscope 路径。
 *
 * <p>m15：AAF 中"workflow"一词有三套互不相同的抽象，此处属第三类，不要与前两者混用：
 *
 * <ul>
 *   <li>{@code framework.engine.workflow}（Flowable）——BPMN 审批流，节点是 UserTask/ServiceTask
 *   <li>{@code module.system.workflow}——审批流的业务封装（请假/报销等）
 *   <li>本包 {@code module.company.workflow}——企业运营编排：按 skill 串联的 AI 步骤，非 BPMN、不进 Flowable
 * </ul>
 *
 * <p>原 v1 实现注释声称"fork 并行"，实际是 for 循环内同步 dispatch；归档后改为显式抛异常，不再保留
 * 误导性描述与静默降级路径。重建时若确需并行，须真正并发执行并在注释中说明调度模型。
 */
@Service
public class WorkflowExecutor {

    public WorkflowResult execute(List<WorkflowStep> steps, Long userId) {
        throw new UnsupportedOperationException("WorkflowExecutor 待重新实现（v1 已归档）");
    }

    public WorkflowResult execute(String sessionId, List<WorkflowStep> steps, String input) {
        throw new UnsupportedOperationException("WorkflowExecutor 待重新实现（v1 已归档）");
    }

    public record WorkflowStep(
            String skill, String name, String input, String output, List<String> dependsOn) {
        public WorkflowStep(String skill, String name, String input, String output) {
            this(skill, name, input, output, List.of());
        }
    }

    public record WorkflowResult(
            boolean success, Map<String, String> stepResults, String finalOutput, String error) {
        public static WorkflowResult success(Map<String, String> stepResults, String finalOutput) {
            return new WorkflowResult(true, stepResults, finalOutput, null);
        }

        public static WorkflowResult error(String error) {
            return new WorkflowResult(false, Map.of(), null, error);
        }
    }
}
