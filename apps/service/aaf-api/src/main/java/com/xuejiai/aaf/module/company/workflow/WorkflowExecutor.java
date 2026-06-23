package com.xuejiai.aaf.module.company.workflow;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

/** WorkflowExecutor stub——v1 实现已归档，待对接新 agentscope 路径。 */
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
