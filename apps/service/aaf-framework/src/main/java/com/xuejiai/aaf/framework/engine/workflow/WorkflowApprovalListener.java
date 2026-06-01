package com.xuejiai.aaf.framework.engine.workflow;

import java.util.HashMap;
import java.util.Map;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.intelligent.assistant.HumanApprovalService;
import com.xuejiai.aaf.framework.intelligent.assistant.HumanApprovalService.ApprovalResolvedEvent;
import com.xuejiai.aaf.framework.intelligent.assistant.HumanApprovalService.ApprovalType;
import com.xuejiai.aaf.framework.intelligent.assistant.HumanApprovalService.Decision;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 工作流审批监听器——用户确认工作流启动请求后自动执行。
 *
 * <p>监听 {@link ApprovalResolvedEvent}，当类型为 {@code ACTION_CONFIRM}
 * 且 context 中包含 {@code processKey} 时，自动启动对应工作流。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkflowApprovalListener {

    private final WorkflowEngine workflowEngine;

    @EventListener
    public void onApprovalResolved(ApprovalResolvedEvent event) {
        var request = event.request();
        if (request.type() != ApprovalType.ACTION_CONFIRM) {
            return;
        }
        var processKey = stringContext(request.context(), "processKey");
        if (processKey == null) {
            return;
        }
        if (event.result().decision() != Decision.APPROVED) {
            log.info("工作流启动被拒绝: processKey={}, requestId={}", processKey, request.requestId());
            return;
        }

        // 构建流程变量
        var vars = new HashMap<String, Object>();
        if (request.userId() != null) vars.put("userId", request.userId());
        var extraVars = request.context().get("variables");
        if (extraVars instanceof Map<?, ?> m) {
            m.forEach((k, v) -> vars.put(k.toString(), v));
        }

        try {
            var processInstanceId = workflowEngine.startProcess(
                    processKey, request.sessionId(), vars);
            log.info("工作流已启动（用户确认）: processKey={}, instanceId={}",
                    processKey, processInstanceId);
        } catch (Exception e) {
            log.error("工作流启动失败: processKey={}", processKey, e);
        }
    }

    private static String stringContext(Map<String, Object> context, String key) {
        if (context == null) return null;
        var v = context.get(key);
        return v == null ? null : v.toString();
    }
}
