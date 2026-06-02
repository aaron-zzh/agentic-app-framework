package com.xuejiai.aaf.module.system.workflow.agui;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.framework.engine.workflow.WorkflowEngine;
import com.xuejiai.aaf.framework.engine.workflow.runtime.WorkflowExecutionLog;
import com.xuejiai.aaf.framework.engine.workflow.runtime.WorkflowExecutionLogger;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.module.ai.chat.agui.AgUiEvent;
import com.xuejiai.aaf.module.ai.flow.repository.AiFlowDefinitionRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 工作流 AG-UI 服务——启动工作流并将执行日志转换为 AG-UI 事件流。
 *
 * @author Kiro
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowAgUiService {

    private static final long SSE_TIMEOUT = 5 * 60 * 1000L;

    private final WorkflowEngine workflowEngine;
    private final WorkflowExecutionLogger executionLogger;
    private final ObjectMapper objectMapper;
    private final AiFlowDefinitionRepository flowRepository;
    private final OperatorContext operatorContext;

    /** runId → SseEmitter，用于恢复流程时继续推送 */
    private final Map<String, SseEmitter> activeEmitters = new ConcurrentHashMap<>();

    /** runId → processInstanceId 映射 */
    private final Map<String, String> runProcessMap = new ConcurrentHashMap<>();

    /** 启动工作流并返回 AG-UI SSE 事件流。 */
    public SseEmitter startAndStream(WorkflowRunRequest request) {
        var emitter = new SseEmitter(SSE_TIMEOUT);
        var runId = UUID.randomUUID().toString();

        emitter.onCompletion(
                () -> {
                    activeEmitters.remove(runId);
                    log.debug("工作流 AG-UI SSE 完成: runId={}", runId);
                });
        emitter.onTimeout(
                () -> {
                    activeEmitters.remove(runId);
                    log.warn("工作流 AG-UI SSE 超时: runId={}", runId);
                });

        activeEmitters.put(runId, emitter);

        Thread.startVirtualThread(() -> executeWorkflow(request, emitter, runId));

        return emitter;
    }

    /** 提交用户输入，恢复等待中的流程。 */
    public void submitInput(String runId, Map<String, Object> variables) {
        var processInstanceId = runProcessMap.get(runId);
        if (processInstanceId == null) {
            log.warn("未找到运行中的流程: runId={}", runId);
            return;
        }

        // 设置流程变量并发送信号恢复流程
        workflowEngine.setProcessVariables(processInstanceId, variables);

        // 完成当前等待任务
        var currentTask = workflowEngine.getCurrentTask(processInstanceId);
        if (currentTask != null) {
            workflowEngine.completeTask(currentTask.taskId(), variables, null);

            // 推送工具调用结果事件
            var emitter = activeEmitters.get(runId);
            if (emitter != null) {
                sendEvent(
                        emitter, AgUiEvent.toolCallResult(runId, "user_input_" + runId, "用户已提交输入"));
                // 继续推送后续节点状态
                pushExecutionState(emitter, runId, processInstanceId);
            }
        }
    }

    /** 获取执行轨迹。 */
    public List<WorkflowExecutionLog> getExecutionTrace(String processInstanceId) {
        return executionLogger.getExecutionLogs(processInstanceId);
    }

    private void executeWorkflow(WorkflowRunRequest request, SseEmitter emitter, String runId) {
        String tempDeploymentId = null;
        try {
            sendEvent(emitter, AgUiEvent.runStarted(runId));

            var variables =
                    request.variables() != null ? request.variables() : Map.<String, Object>of();
            String processKey;

            if (request.debug()) {
                // 调试模式：校验创建者，临时部署 BPMN XML，执行后清理
                if (request.bpmnXml() == null || request.bpmnXml().isBlank()) {
                    throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "调试模式需要传入 bpmnXml");
                }
                if (request.flowId() != null) {
                    var flow =
                            flowRepository
                                    .findById(request.flowId())
                                    .orElseThrow(
                                            () ->
                                                    new BusinessException(
                                                            GlobalErrorCode.NOT_FOUND, "工作流不存在"));
                    var currentUserId = operatorContext.currentOwnerId().orElse(null);
                    if (currentUserId == null || !currentUserId.equals(flow.getCreateBy())) {
                        throw new BusinessException(GlobalErrorCode.FORBIDDEN, "仅创建者可调试");
                    }
                }
                tempDeploymentId = workflowEngine.deploy("debug-" + runId, request.bpmnXml());
                processKey = "debug-" + runId;
            } else {
                // 正式运行：查 ai_flow_definition，校验 PUBLISHED
                if (request.flowId() == null) {
                    throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "正式运行需要传入 flowId");
                }
                var flow =
                        flowRepository
                                .findById(request.flowId())
                                .orElseThrow(
                                        () ->
                                                new BusinessException(
                                                        GlobalErrorCode.NOT_FOUND, "工作流不存在"));
                if (!"PUBLISHED".equals(flow.getStatus())) {
                    throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "工作流未发布，无法运行");
                }
                processKey = flow.getId().toString();
            }

            var processInstanceId =
                    workflowEngine.startProcess(processKey, "agui:" + runId, variables);
            runProcessMap.put(runId, processInstanceId);

            // 调试模式记录临时 deploymentId，完成后清理
            if (tempDeploymentId != null) {
                final var deployId = tempDeploymentId;
                emitter.onCompletion(
                        () -> {
                            try {
                                workflowEngine.deleteDeployment(deployId, true);
                            } catch (Exception ignored) {
                            }
                        });
            }

            pushExecutionState(emitter, runId, processInstanceId);

        } catch (Exception e) {
            log.error("工作流 AG-UI 执行失败: runId={}", runId, e);
            if (tempDeploymentId != null) {
                try {
                    workflowEngine.deleteDeployment(tempDeploymentId, true);
                } catch (Exception ignored) {
                }
            }
            sendEvent(emitter, AgUiEvent.runError(runId, e.getMessage()));
            completeEmitter(emitter, e);
        }
    }

    private void pushExecutionState(SseEmitter emitter, String runId, String processInstanceId) {
        try {
            // 获取执行日志，推送状态
            var logs = executionLogger.getExecutionLogs(processInstanceId);
            var completedNodes =
                    logs.stream()
                            .filter(l -> "completed".equals(l.status()))
                            .map(WorkflowExecutionLog::nodeId)
                            .toList();
            var activeNodes =
                    logs.stream()
                            .filter(l -> "running".equals(l.status()))
                            .map(WorkflowExecutionLog::nodeId)
                            .toList();

            // 发送 STATE_DELTA：当前活跃节点和已完成节点
            var stateMap =
                    Map.of(
                            "activeNodes", activeNodes,
                            "completedNodes", completedNodes,
                            "processInstanceId", processInstanceId);
            var stateDeltaJson =
                    objectMapper.writeValueAsString(
                            Map.of(
                                    "type", "STATE_DELTA",
                                    "runId", runId,
                                    "state", stateMap));
            emitter.send(SseEmitter.event().data(stateDeltaJson));

            // 检查是否有等待用户输入的任务
            var currentTask = workflowEngine.getCurrentTask(processInstanceId);
            if (currentTask != null) {
                // 发送 TOOL_CALL_START 表示等待用户输入
                var toolCallId = "user_input_" + runId;
                sendEvent(emitter, AgUiEvent.toolCallStart(runId, toolCallId, "user_input"));
                sendEvent(
                        emitter,
                        AgUiEvent.toolCallArgs(
                                runId,
                                toolCallId,
                                objectMapper.writeValueAsString(
                                        Map.of(
                                                "taskId", currentTask.taskId(),
                                                "taskName", currentTask.name(),
                                                "assignee",
                                                        currentTask.assignee() != null
                                                                ? currentTask.assignee()
                                                                : ""))));
            } else {
                // 流程已结束
                // 发送最后一条输出作为消息
                var lastOutput =
                        logs.stream()
                                .filter(l -> "completed".equals(l.status()) && l.output() != null)
                                .reduce((a, b) -> b)
                                .map(WorkflowExecutionLog::output)
                                .orElse("流程执行完成");

                var messageId = UUID.randomUUID().toString();
                sendEvent(emitter, AgUiEvent.textMessageStart(runId, messageId));
                sendEvent(emitter, AgUiEvent.textMessageContent(runId, messageId, lastOutput));
                sendEvent(emitter, AgUiEvent.textMessageEnd(runId, messageId));
                sendEvent(emitter, AgUiEvent.runFinished(runId));
                emitter.complete();
                activeEmitters.remove(runId);
                runProcessMap.remove(runId);
            }
        } catch (Exception e) {
            log.error("推送执行状态失败: runId={}", runId, e);
            sendEvent(emitter, AgUiEvent.runError(runId, e.getMessage()));
            completeEmitter(emitter, e);
        }
    }

    private void sendEvent(SseEmitter emitter, AgUiEvent event) {
        try {
            var json = objectMapper.writeValueAsString(event);
            emitter.send(SseEmitter.event().data(json));
        } catch (IOException e) {
            log.debug("工作流 AG-UI SSE 发送失败: {}", e.getMessage());
        }
    }

    private void completeEmitter(SseEmitter emitter, Throwable e) {
        try {
            emitter.completeWithError(e);
        } catch (Exception ignored) {
            // 客户端已断开
        }
    }
}
