package com.xuejiai.aaf.module.system.task.action;

import java.util.Map;

import org.springframework.stereotype.Component;

import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.engine.workflow.WorkflowEngine;
import com.xuejiai.aaf.module.system.task.domain.ScheduledTask;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.type.TypeReference;

/**
 * 工作流动作执行器——定时触发 Flowable 工作流实例。
 *
 * <h3>支持的三种调度模式</h3>
 *
 * <p><b>模式 A：工作流内部编排（一次调度，步骤有依赖）</b>
 *
 * <pre>
 * 调度器 09:00 触发一次
 *   → WorkflowActionExecutor.execute()
 *   → Flowable 启动流程实例
 *       ├── ServiceTask：步骤1（收集数据）→ 完成后
 *       ├── ServiceTask：步骤2（分析数据）→ 完成后
 *       └── ServiceTask：步骤3（发送报告）→ 流程结束
 * </pre>
 *
 * 适合步骤间有数据依赖、整体在分钟级内完成的场景。 BPMN 里用顺序流（SequenceFlow）连接各 ServiceTask 即可。
 *
 * <p><b>模式 B：每步骤独立调度（多条 ScheduledTask，步骤无依赖）</b>
 *
 * <pre>
 * sys_scheduled_task 建三条记录：
 *   task1: cron=0 0 9 * * ?, processKey=step1-flow
 *   task2: cron=0 0 10 * * ?, processKey=step2-flow
 *   task3: cron=0 0 11 * * ?, processKey=step3-flow
 * </pre>
 *
 * 适合步骤完全独立、按时间窗口分散执行的场景。
 *
 * <p><b>模式 C：调度触发 + 工作流内部定时等待（混合模式）</b>
 *
 * <pre>
 * 调度器 09:00 触发一次
 *   → Flowable 启动流程
 *       ├── ServiceTask：步骤1 立即执行
 *       ├── TimerBoundaryEvent：等待到 10:00（Flowable 定时边界事件）
 *       ├── ServiceTask：步骤2 在 10:00 自动触发
 *       ├── TimerBoundaryEvent：等待到 11:00
 *       └── ServiceTask：步骤3 在 11:00 自动触发
 * </pre>
 *
 * Flowable 原生支持 Timer Boundary Event，BPMN 配置 timeDate/timeDuration 即可。 适合"每天 09:00
 * 启动，各步骤在固定时间点执行"的场景。
 *
 * <h3>步骤进度查询</h3>
 *
 * <p>三种模式的步骤级执行历史均可通过 Flowable 历史表查询：
 *
 * <pre>
 * SELECT act_name_, start_time_, end_time_, duration_
 * FROM act_hi_actinst
 * WHERE proc_inst_id_ = :instanceId
 * ORDER BY start_time_;
 * </pre>
 *
 * 工作流启动时注入了 {@code scheduledTaskId}，可通过流程变量反查关联任务。
 *
 * <h3>actionConfig JSON 格式</h3>
 *
 * <pre>
 * {
 *   "processKey": "daily-report-flow",  // Flowable 流程定义 Key（必填）
 *   "businessKey": "my-biz-key",        // 业务键，用于关联查询（可选）
 *   "variables": {                       // 流程启动变量（可选）
 *     "userId": 1,
 *     "input": "帮我总结今天的任务"
 *   }
 * }
 * </pre>
 *
 * <p>TODO：Agent 直接调用（不经过 Flowable）待 AAF-021 元引擎就绪后扩展： actionConfig 加 {@code "agentId"} 字段，通过
 * AgentDispatcher 直接派发 Agent 执行。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkflowActionExecutor implements ScheduledActionExecutor {

    private final WorkflowEngine workflowEngine;

    @Override
    public String actionType() {
        return "WORKFLOW";
    }

    @Override
    public void execute(ScheduledTask task) {
        try {
            var config =
                    JsonUtils.parseObject(
                            task.getActionConfig(), new TypeReference<Map<String, Object>>() {});

            var processKey = (String) config.get("processKey");
            if (processKey == null || processKey.isBlank()) {
                throw new IllegalArgumentException("actionConfig 缺少 processKey");
            }

            // businessKey 默认用 task-{id}，便于后续查询流程实例
            var businessKey =
                    config.containsKey("businessKey")
                            ? (String) config.get("businessKey")
                            : "task-" + task.getId();

            // 启动变量：合并 actionConfig.variables + 任务元信息
            @SuppressWarnings("unchecked")
            var userVars =
                    config.containsKey("variables")
                            ? (Map<String, Object>) config.get("variables")
                            : Map.<String, Object>of();
            var variables = new java.util.HashMap<>(userVars);
            variables.put("scheduledTaskId", task.getId());
            variables.put("scheduledTaskName", task.getName());

            var instanceId = workflowEngine.startProcess(processKey, businessKey, variables);
            log.info(
                    "定时任务 [{}] 触发工作流 processKey={} instanceId={}",
                    task.getName(),
                    processKey,
                    instanceId);

        } catch (Exception e) {
            log.error("WORKFLOW 动作执行失败，taskId={}", task.getId(), e);
            throw new RuntimeException("WORKFLOW 动作执行失败: " + e.getMessage(), e);
        }
    }
}
