package com.xuejiai.aaf.module.system.notify.controller;

import java.time.LocalTime;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.module.system.notify.domain.NotificationPreference;
import com.xuejiai.aaf.module.system.notify.service.NotificationPreferenceService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 通知偏好设置接口。
 *
 * @author AaronZZH & Kiro
 */
@Tag(name = "通知偏好")
@RestController
@RequestMapping("/api/notification-preferences")
@RequiredArgsConstructor
public class NotificationPreferenceController {

    private final NotificationPreferenceService preferenceService;
    private final OperatorContext operatorContext;

    @Operation(summary = "获取当前用户通知偏好")
    @GetMapping
    public Result<NotificationPreference> get() {
        Long userId = operatorContext.currentUserId().orElseThrow();
        return Result.success(preferenceService.getByUserId(userId));
    }

    @Operation(summary = "更新通知偏好")
    @PutMapping
    public Result<NotificationPreference> update(@RequestBody UpdateRequest request) {
        Long userId = operatorContext.currentUserId().orElseThrow();
        return Result.success(
                preferenceService.upsert(
                        userId, request.preferences(), request.quietStart(), request.quietEnd()));
    }

    /** 更新请求体 */
    record UpdateRequest(String preferences, LocalTime quietStart, LocalTime quietEnd) {}
}
