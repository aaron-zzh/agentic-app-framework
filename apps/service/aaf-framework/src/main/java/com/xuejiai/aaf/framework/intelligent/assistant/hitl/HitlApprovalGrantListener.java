package com.xuejiai.aaf.framework.intelligent.assistant.hitl;

import java.util.List;
import java.util.Map;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.engine.tool.ToolPermissionChecker;

import lombok.RequiredArgsConstructor;

/** 将 HITL 审批结果同步为可恢复执行的会话授权。 */
@Component
@RequiredArgsConstructor
public class HitlApprovalGrantListener {

    private final ToolPermissionChecker toolPermissionChecker;
    private final AssistantSessionTrustService sessionTrustService;

    @EventListener
    public void onApprovalResolved(HumanApprovalService.ApprovalResolvedEvent event) {
        if (event.result().decision() != HumanApprovalService.Decision.APPROVED) {
            return;
        }
        var request = event.request();
        if (!shouldGrant(request.type()) || request.grantScope() == HumanApprovalService.GrantScope.NONE) {
            return;
        }
        var sessionId = request.sessionId();
        var subjectKey = subjectKey(request);
        if (sessionId == null || sessionId.isBlank() || subjectKey == null || subjectKey.isBlank()) {
            return;
        }
        grantToolPermission(sessionId, subjectKey, request.grantScope(), request.context());
        if (request.type() == HumanApprovalService.ApprovalType.TOOL_PERMISSION
                && "TOOL".equalsIgnoreCase(request.subjectType())) {
            sessionTrustService.trustTools(sessionId, request.userId(), List.of(subjectKey));
        }
    }

    private boolean shouldGrant(HumanApprovalService.ApprovalType type) {
        return switch (type) {
            case TOOL_PERMISSION, LOW_CONFIDENCE, ACTION_CONFIRM -> true;
            case CONTENT_REVIEW, CREDIT_RECOVERY, VALUE_REVIEW, DATA_MUTATION, CUSTOM -> false;
        };
    }

    private void grantToolPermission(
            String sessionId,
            String subjectKey,
            HumanApprovalService.GrantScope grantScope,
            Map<String, Object> context) {
        switch (grantScope) {
            case ONCE ->
                    toolPermissionChecker.grantWithScope(
                            sessionId, subjectKey, ToolPermissionChecker.GrantScope.ONCE, null);
            case SESSION ->
                    toolPermissionChecker.grantWithScope(
                            sessionId, subjectKey, ToolPermissionChecker.GrantScope.SESSION, null);
            case PATTERN ->
                    toolPermissionChecker.grantWithScope(
                            sessionId,
                            subjectKey,
                            ToolPermissionChecker.GrantScope.PATTERN,
                            stringContext(context, "pattern"));
            case NONE -> {
                // 前置分支已过滤；保留分支确保后续新增枚举时编译器能提示。
            }
        }
    }

    private String subjectKey(HumanApprovalService.ApprovalRequest request) {
        if (request.subjectKey() != null && !request.subjectKey().isBlank()) {
            return request.subjectKey();
        }
        return stringContext(request.context(), "toolName");
    }

    private String stringContext(Map<String, Object> context, String key) {
        if (context == null) {
            return null;
        }
        var value = context.get(key);
        return value == null ? null : value.toString();
    }
}
