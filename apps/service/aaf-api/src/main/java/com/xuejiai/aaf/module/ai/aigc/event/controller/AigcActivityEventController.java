package com.xuejiai.aaf.module.ai.aigc.event.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.framework.org.OrgContext;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.module.ai.aigc.event.api.SubscriptionScope;
import com.xuejiai.aaf.module.ai.aigc.event.service.AigcActivityEventService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/aigc/events")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class AigcActivityEventController {

    private final AigcActivityEventService service;
    private final OperatorContext operatorContext;

    @GetMapping("/stream")
    public SseEmitter stream(
            @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId,
            @RequestParam(value = "after", required = false) Long after,
            @RequestParam(value = "orgId", required = false) Long requestedOrgId,
            @RequestParam(value = "workspaceId", required = false) Long requestedWorkspaceId) {
        var userId =
                operatorContext
                        .currentOwnerId()
                        .orElseThrow(
                                () -> new BusinessException(GlobalErrorCode.UNAUTHORIZED, "未登录"));
        var scope = authorizedScope(userId, requestedOrgId, requestedWorkspaceId);
        var cursor = Math.max(after == null ? 0L : after, parseCursor(lastEventId));
        return service.subscribe(scope, cursor);
    }

    SubscriptionScope authorizedScope(Long userId, Long requestedOrgId, Long requestedWorkspaceId) {
        var authorizedOrgId = OrgContext.getCurrentOrgId();
        var authorizedWorkspaceId = OrgContext.getCurrentWorkspaceId();
        if (requestedOrgId != null && !requestedOrgId.equals(authorizedOrgId)) {
            throw new BusinessException(GlobalErrorCode.FORBIDDEN, "Activity 组织 scope 未经授权");
        }
        if (requestedWorkspaceId != null && !requestedWorkspaceId.equals(authorizedWorkspaceId)) {
            throw new BusinessException(GlobalErrorCode.FORBIDDEN, "Activity 工作区 scope 未经授权");
        }
        if (requestedWorkspaceId != null && authorizedOrgId == null) {
            throw new BusinessException(GlobalErrorCode.FORBIDDEN, "Activity 工作区缺少组织 scope");
        }
        return new SubscriptionScope(userId, authorizedOrgId, authorizedWorkspaceId);
    }

    private long parseCursor(String value) {
        if (value == null || value.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException error) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "Last-Event-ID 非法");
        }
    }
}
