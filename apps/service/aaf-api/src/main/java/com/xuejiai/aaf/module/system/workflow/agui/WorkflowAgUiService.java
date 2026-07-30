package com.xuejiai.aaf.module.system.workflow.agui;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.engine.workflow.WorkflowEngine;
import com.xuejiai.aaf.framework.engine.workflow.runtime.WorkflowExecutionLog;
import com.xuejiai.aaf.framework.engine.workflow.runtime.WorkflowExecutionLogger;
import com.xuejiai.aaf.framework.org.OrgContext;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.module.ai.chat.agui.AgUiEvent;
import com.xuejiai.aaf.module.ai.flow.domain.AiFlowDefinition;
import com.xuejiai.aaf.module.ai.flow.repository.AiFlowDefinitionRepository;
import com.xuejiai.aaf.module.ai.flow.service.AiFlowBpmnCompiler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 工作流 AG-UI 服务——启动工作流并将执行日志转换为 AG-UI 事件流。
 *
 * @author AaronZZH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowAgUiService {

    private static final long SSE_TIMEOUT = 30 * 60 * 1000L;
    private static final long POLL_INTERVAL_MS = 500L;
    private static final long HEARTBEAT_INTERVAL_MS = 15_000L;
    private static final Set<String> RESERVED_VARIABLES =
            Set.of("_aafOrgId", "_aafWorkspaceId", "_aafUserId", "_aafDebugDeploymentId");

    private final WorkflowEngine workflowEngine;
    private final WorkflowExecutionLogger executionLogger;
    private final AiFlowDefinitionRepository flowRepository;
    private final AiFlowBpmnCompiler bpmnCompiler;
    private final OperatorContext operatorContext;

    /** runId → SseEmitter，用于恢复流程时继续推送 */
    private final Map<String, SseEmitter> activeEmitters = new ConcurrentHashMap<>();

    /** 启动工作流并返回 AG-UI SSE 事件流。 */
    public SseEmitter startAndStream(WorkflowRunRequest request) {
        var identity = currentIdentity();
        var flow = requireRunnableFlow(request, identity);
        var runId = UUID.randomUUID().toString();
        var emitter = createEmitter(runId);
        Thread.startVirtualThread(
                () -> withOrgContext(identity, () -> executeWorkflow(request, flow, identity, emitter, runId)));
        return emitter;
    }

    /** 断线后按 runId 恢复 AG-UI 事件流。 */
    public SseEmitter resumeStream(String runId) {
        var identity = currentIdentity();
        var processInstanceId = requireRun(runId, identity, false);
        var emitter = createEmitter(runId);
        Thread.startVirtualThread(
                () ->
                        withOrgContext(
                                identity,
                                () -> streamUntilTerminal(emitter, runId, processInstanceId)));
        return emitter;
    }

    /** 提交用户输入，恢复等待中的流程。 */
    public void submitInput(String runId, Map<String, Object> variables) {
        var identity = currentIdentity();
        var processInstanceId = requireRun(runId, identity, true);
        var safeVariables = variables != null ? new HashMap<>(variables) : new HashMap<String, Object>();
        if (!java.util.Collections.disjoint(safeVariables.keySet(), RESERVED_VARIABLES)) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "禁止修改工作流安全变量");
        }
        var currentTask = workflowEngine.getCurrentTask(processInstanceId);
        if (currentTask == null) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "流程当前未等待用户输入");
        }
        workflowEngine.completeTask(currentTask.taskId(), safeVariables, identity.userId().toString());
        var emitter = activeEmitters.get(runId);
        if (emitter != null) {
            sendEvent(
                    emitter, AgUiEvent.toolCallResult(runId, "user_input_" + runId, "用户已提交输入"));
        }
    }

    /** 获取执行轨迹。 */
    public List<WorkflowExecutionLog> getExecutionTrace(String processInstanceId) {
        requireInstanceAccess(processInstanceId, currentIdentity());
        return executionLogger.getExecutionLogs(processInstanceId);
    }

    private void executeWorkflow(
            WorkflowRunRequest request,
            AiFlowDefinition flow,
            RunIdentity identity,
            SseEmitter emitter,
            String runId) {
        String tempDeploymentId = null;
        try {
            sendEvent(emitter, AgUiEvent.runStarted(runId));
            var variables =
                    request.variables() != null
                            ? new HashMap<String, Object>(request.variables())
                            : new HashMap<String, Object>();
            variables.put("_aafOrgId", identity.orgId());
            variables.put("_aafWorkspaceId", identity.workspaceId());
            variables.put("_aafUserId", identity.userId());
            if (request.messages() != null && !request.messages().isEmpty()) {
                variables.put("messages", request.messages());
            }

            String processKey;
            if (request.debug()) {
                var compilation = bpmnCompiler.compileDebug(flow.getId(), runId, flow.getDefinition());
                tempDeploymentId =
                        workflowEngine.deploy("debug-" + flow.getId() + "-" + runId, compilation.bpmnXml());
                processKey = compilation.processKey();
                variables.put("_aafDebugDeploymentId", tempDeploymentId);
            } else {
                processKey = bpmnCompiler.processKey(flow.getId());
            }

            var processInstanceId =
                    workflowEngine.startProcess(processKey, businessKey(runId), variables);
            streamUntilTerminal(emitter, runId, processInstanceId);
        } catch (Exception e) {
            log.error("工作流 AG-UI 执行失败: runId={}", runId, e);
            if (tempDeploymentId != null
                    && workflowEngine.findInstanceByBusinessKey(businessKey(runId)) == null) {
                deleteDebugDeployment(tempDeploymentId);
            }
            sendEvent(emitter, AgUiEvent.runError(runId, safeMessage(e)));
            completeEmitter(emitter, e);
        }
    }

    private void streamUntilTerminal(
            SseEmitter emitter, String runId, String processInstanceId) {
        var announcedTasks = new HashSet<String>();
        var lastHeartbeat = System.currentTimeMillis();
        try {
            while (activeEmitters.get(runId) == emitter) {
                pushExecutionState(emitter, runId, processInstanceId, announcedTasks);
                if (!workflowEngine.isProcessRunning(processInstanceId)) {
                    finishRun(emitter, runId, processInstanceId);
                    return;
                }
                if (System.currentTimeMillis() - lastHeartbeat >= HEARTBEAT_INTERVAL_MS) {
                    emitter.send(SseEmitter.event().comment("heartbeat"));
                    lastHeartbeat = System.currentTimeMillis();
                }
                Thread.sleep(POLL_INTERVAL_MS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            completeEmitter(emitter, e);
        } catch (IOException e) {
            activeEmitters.remove(runId, emitter);
            completeEmitter(emitter, e);
        } catch (RuntimeException e) {
            log.error("推送执行状态失败: runId={}", runId, e);
            sendEvent(emitter, AgUiEvent.runError(runId, safeMessage(e)));
            completeEmitter(emitter, e);
        }
    }

    private void pushExecutionState(
            SseEmitter emitter,
            String runId,
            String processInstanceId,
            Set<String> announcedTasks)
            throws IOException {
        var logs = executionLogger.getExecutionLogs(processInstanceId);
        var completedNodes =
                logs.stream()
                        .filter(l -> "completed".equals(l.status()))
                        .map(WorkflowExecutionLog::nodeId)
                        .distinct()
                        .toList();
        var activeNodes =
                logs.stream()
                        .filter(l -> "running".equals(l.status()))
                        .map(WorkflowExecutionLog::nodeId)
                        .distinct()
                        .toList();
        var failedNodes =
                logs.stream()
                        .filter(l -> "failed".equals(l.status()))
                        .map(WorkflowExecutionLog::nodeId)
                        .distinct()
                        .toList();
        var stateMap =
                Map.of(
                        "activeNodes", activeNodes,
                        "completedNodes", completedNodes,
                        "failedNodes", failedNodes,
                        "processInstanceId", processInstanceId);
        emitter.send(
                SseEmitter.event()
                        .data(
                                JsonUtils.toJsonString(
                                        Map.of(
                                                "type", "STATE_DELTA",
                                                "runId", runId,
                                                "state", stateMap))));

        var currentTask = workflowEngine.getCurrentTask(processInstanceId);
        if (currentTask != null && announcedTasks.add(currentTask.taskId())) {
            var toolCallId = "user_input_" + runId;
            sendEvent(emitter, AgUiEvent.toolCallStart(runId, toolCallId, "user_input"));
            sendEvent(
                    emitter,
                    AgUiEvent.toolCallArgs(
                            runId,
                            toolCallId,
                            JsonUtils.toJsonString(
                                    Map.of(
                                            "taskId", currentTask.taskId(),
                                            "taskName", currentTask.name(),
                                            "assignee",
                                                    currentTask.assignee() != null
                                                            ? currentTask.assignee()
                                                            : ""))));
        }
    }

    private void finishRun(SseEmitter emitter, String runId, String processInstanceId) {
        var logs = executionLogger.getExecutionLogs(processInstanceId);
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
        var deploymentId = workflowEngine.getProcessVariables(processInstanceId).get("_aafDebugDeploymentId");
        if (deploymentId != null) {
            deleteDebugDeployment(String.valueOf(deploymentId));
        }
        activeEmitters.remove(runId, emitter);
        emitter.complete();
    }


    private SseEmitter createEmitter(String runId) {
        var emitter = new SseEmitter(SSE_TIMEOUT);
        var previous = activeEmitters.put(runId, emitter);
        if (previous != null) {
            previous.complete();
        }
        emitter.onCompletion(
                () -> {
                    activeEmitters.remove(runId, emitter);
                    log.debug("工作流 AG-UI SSE 完成: runId={}", runId);
                });
        emitter.onTimeout(
                () -> {
                    activeEmitters.remove(runId, emitter);
                    log.warn("工作流 AG-UI SSE 超时: runId={}", runId);
                });
        emitter.onError(error -> activeEmitters.remove(runId, emitter));
        return emitter;
    }

    private AiFlowDefinition requireRunnableFlow(
            WorkflowRunRequest request, RunIdentity identity) {
        var flow =
                flowRepository
                        .findById(request.flowId())
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                GlobalErrorCode.NOT_FOUND, "工作流不存在"));
        var flowWorkspaceId = flow.getWorkspaceId() != null ? flow.getWorkspaceId() : 0L;
        if (!identity.orgId().equals(flow.getOrgId())
                || !identity.workspaceId().equals(flowWorkspaceId)) {
            throw new BusinessException(GlobalErrorCode.NOT_FOUND, "工作流不存在");
        }
        if (request.debug()) {
            var ownerId = flow.getOwnerId() != null ? flow.getOwnerId() : flow.getCreateBy();
            if (!identity.userId().equals(ownerId)) {
                throw new BusinessException(GlobalErrorCode.FORBIDDEN, "仅创建者可调试");
            }
        } else if (!"PUBLISHED".equals(flow.getStatus())) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "工作流未发布，无法运行");
        }
        return flow;
    }

    private String requireRun(String runId, RunIdentity identity, boolean requireRunning) {
        try {
            UUID.fromString(runId);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "运行标识不合法");
        }
        var processInstanceId = workflowEngine.findInstanceByBusinessKey(businessKey(runId));
        if (processInstanceId == null) {
            throw new BusinessException(GlobalErrorCode.NOT_FOUND, "工作流运行不存在");
        }
        requireInstanceAccess(processInstanceId, identity);
        if (requireRunning && !workflowEngine.isProcessRunning(processInstanceId)) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "工作流运行已结束");
        }
        return processInstanceId;
    }

    private void requireInstanceAccess(String processInstanceId, RunIdentity identity) {
        var variables = workflowEngine.getProcessVariables(processInstanceId);
        if (!identity.orgId().equals(longValue(variables.get("_aafOrgId")))
                || !identity.workspaceId().equals(longValue(variables.get("_aafWorkspaceId")))
                || !identity.userId().equals(longValue(variables.get("_aafUserId")))) {
            throw new BusinessException(GlobalErrorCode.NOT_FOUND, "工作流运行不存在");
        }
    }

    private RunIdentity currentIdentity() {
        var userId =
                operatorContext
                        .currentOwnerId()
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                GlobalErrorCode.UNAUTHORIZED, "用户未登录"));
        var orgId = OrgContext.getCurrentOrgId();
        if (orgId == null) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "缺少组织上下文");
        }
        var workspaceId = OrgContext.getCurrentWorkspaceId();
        return new RunIdentity(userId, orgId, workspaceId != null ? workspaceId : 0L);
    }

    private void withOrgContext(RunIdentity identity, Runnable action) {
        var previousOrgId = OrgContext.getCurrentOrgId();
        var previousWorkspaceId = OrgContext.getCurrentWorkspaceId();
        try {
            OrgContext.setCurrentOrgId(identity.orgId());
            OrgContext.setCurrentWorkspaceId(identity.workspaceId());
            action.run();
        } finally {
            OrgContext.setCurrentOrgId(previousOrgId);
            OrgContext.setCurrentWorkspaceId(previousWorkspaceId);
        }
    }

    private Long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String businessKey(String runId) {
        return "agui:" + runId;
    }

    private void deleteDebugDeployment(String deploymentId) {
        try {
            workflowEngine.deleteDeployment(deploymentId, true);
        } catch (RuntimeException ex) {
            log.warn("清理调试工作流部署失败: deploymentId={}", deploymentId, ex);
        }
    }

    private String safeMessage(Throwable error) {
        return error.getMessage() != null ? error.getMessage() : "工作流执行失败";
    }

    private record RunIdentity(Long userId, Long orgId, Long workspaceId) {}

    private void sendEvent(SseEmitter emitter, AgUiEvent event) {
        try {
            var json = JsonUtils.toJsonString(event);
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
