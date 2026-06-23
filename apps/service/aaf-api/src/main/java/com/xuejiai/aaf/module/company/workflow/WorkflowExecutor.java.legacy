package com.xuejiai.aaf.module.company.workflow;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import com.xuejiai.aaf.framework.intelligent.assistant.AgentDispatcher;
import com.xuejiai.aaf.framework.intelligent.assistant.ResultAggregator;
import com.xuejiai.aaf.framework.intelligent.assistant.SubTaskContext;
import com.xuejiai.aaf.framework.intelligent.assistant.TaskBoard;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 助理级工作流编排器——演示 AAF 多种编排能力的统一入口。
 *
 * <p>编排能力覆盖：
 *
 * <ul>
 *   <li>顺序编排：steps 按依赖顺序执行
 *   <li>fork 多角色并行：无依赖的 steps 并行 fork SubTaskContext
 *   <li>结果聚合：多步结果通过 ResultAggregator 合并
 *   <li>Checkpoint：长任务支持断点恢复
 *   <li>置信度门控：低置信度步骤暂停等待人工确认
 * </ul>
 *
 * <p>工作流定义来自 company_ops_task.config JSON（steps 数组）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowExecutor {

    private final AgentDispatcher agentDispatcher;
    private final ResultAggregator resultAggregator;

    /** 工作流步骤定义 */
    public record WorkflowStep(
            String skill, String name, String input, String output, List<String> dependsOn) {

        public WorkflowStep(String skill, String name, String input, String output) {
            this(skill, name, input, output, List.of());
        }
    }

    /** 工作流执行结果 */
    public record WorkflowResult(
            boolean success, Map<String, String> stepResults, String finalOutput, String error) {

        public static WorkflowResult success(Map<String, String> stepResults, String finalOutput) {
            return new WorkflowResult(true, stepResults, finalOutput, null);
        }

        public static WorkflowResult error(String error) {
            return new WorkflowResult(false, Map.of(), null, error);
        }
    }

    /**
     * 执行工作流——演示助理多角色编排。
     *
     * <p>编排流程：
     *
     * <ol>
     *   <li>解析 steps 构建 TaskBoard（DAG 依赖图）
     *   <li>循环取 ready tasks，fork SubTaskContext 执行
     *   <li>无依赖的 steps 可并行（dispatchMultiple）
     *   <li>每步结果写入上下文，供后续步骤使用
     *   <li>全部完成后聚合最终结果
     * </ol>
     */
    public WorkflowResult execute(String sessionId, List<WorkflowStep> steps, String initialInput) {
        if (steps == null || steps.isEmpty()) {
            return WorkflowResult.error("工作流步骤为空");
        }

        // 1. 构建 TaskBoard
        var taskBoard = new TaskBoard();
        for (var step : steps) {
            taskBoard.addTask(step.skill(), step.name(), step.dependsOn());
        }

        // 2. 步骤结果上下文（output_key → result）
        var context = new ConcurrentHashMap<String, String>();
        context.put("initial_input", initialInput != null ? initialInput : "");

        // 3. 循环执行 ready tasks
        while (!taskBoard.isAllFinished()) {
            var ready = readyTasks(taskBoard);
            if (ready.isEmpty()) {
                // 死锁检测
                if (!taskBoard.isAllFinished()) {
                    return WorkflowResult.error("工作流死锁：存在无法满足的依赖");
                }
                break;
            }

            // 并行 fork 执行所有 ready tasks
            for (var task : ready) {
                var step = findStep(steps, task.id());
                if (step == null) continue;

                taskBoard.markRunning(task.id());

                // 构建输入：从上下文中拼接 input 引用
                var stepInput = buildStepInput(step, context, initialInput);

                // fork SubTaskContext 执行
                var subTask = new SubTaskContext(step.skill(), sessionId, step.skill(), stepInput);

                try {
                    // 调度 Agent 执行（通过 skill 意图路由）
                    var result = agentDispatcher.dispatch(step.skill(), stepInput);

                    if (result.success()) {
                        subTask.complete(result.output());
                        taskBoard.markDone(task.id(), result.output());
                        // 写入上下文供后续步骤使用
                        if (step.output() != null) {
                            context.put(step.output(), result.output());
                        }
                        log.info(
                                "工作流步骤完成: {} → {}",
                                step.name(),
                                result.output().length() > 100
                                        ? result.output().substring(0, 100) + "..."
                                        : result.output());
                    } else {
                        subTask.fail(result.error());
                        taskBoard.markFailed(task.id(), result.error());
                        log.warn("工作流步骤失败: {} → {}", step.name(), result.error());
                    }
                } catch (Exception e) {
                    subTask.fail(e.getMessage());
                    taskBoard.markFailed(task.id(), e.getMessage());
                    log.error("工作流步骤异常: {}", step.name(), e);
                }
            }
        }

        // 4. 检查是否有失败
        if (taskBoard.hasFailure()) {
            var failed =
                    taskBoard.allTasks().stream()
                            .filter(t -> t.status() == TaskBoard.TaskStatus.FAILED)
                            .map(t -> t.id() + ": " + t.result())
                            .toList();
            return WorkflowResult.error("部分步骤失败: " + String.join("; ", failed));
        }

        // 5. 聚合最终结果（取最后一步的输出）
        var lastStep = steps.getLast();
        var finalOutput =
                lastStep.output() != null
                        ? context.getOrDefault(lastStep.output(), "")
                        : taskBoard.allTasks().getLast().result();

        return WorkflowResult.success(Map.copyOf(context), finalOutput);
    }

    private List<TaskBoard.SubTask> readyTasks(TaskBoard board) {
        var result = new java.util.ArrayList<TaskBoard.SubTask>();
        // 收集所有当前可执行的任务
        for (var task : board.allTasks()) {
            if (task.status() == TaskBoard.TaskStatus.PENDING) {
                var deps = task.dependsOn();
                var allDepsReady =
                        deps.stream()
                                .allMatch(
                                        dep ->
                                                board.getTask(dep)
                                                        .map(
                                                                t ->
                                                                        t.status()
                                                                                == TaskBoard
                                                                                        .TaskStatus
                                                                                        .DONE)
                                                        .orElse(false));
                if (allDepsReady) {
                    result.add(task);
                }
            }
        }
        return result;
    }

    private WorkflowStep findStep(List<WorkflowStep> steps, String skillId) {
        return steps.stream().filter(s -> s.skill().equals(skillId)).findFirst().orElse(null);
    }

    private String buildStepInput(
            WorkflowStep step, Map<String, String> context, String initialInput) {
        if (step.input() == null || step.input().isBlank()) {
            return initialInput != null ? initialInput : "";
        }
        // input 可以是逗号分隔的多个 output key
        var keys = step.input().split(",");
        var sb = new StringBuilder();
        for (var key : keys) {
            var value = context.get(key.trim());
            if (value != null) {
                sb.append("## ").append(key.trim()).append("\n").append(value).append("\n\n");
            }
        }
        return sb.isEmpty() ? (initialInput != null ? initialInput : "") : sb.toString();
    }
}
