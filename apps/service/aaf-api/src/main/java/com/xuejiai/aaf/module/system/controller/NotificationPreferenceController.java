package com.xuejiai.aaf.module.system.controller;

import java.time.LocalTime;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.security.ActorContext;
import com.xuejiai.aaf.module.system.domain.NotificationPreference;
import com.xuejiai.aaf.module.system.service.NotificationPreferenceService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** 通知偏好设置接口。 */
@Tag(name = "通知偏好")
@RestController
@RequestMapping("/api/notification-preferences")
@RequiredArgsConstructor
public class NotificationPreferenceController {

    private final NotificationPreferenceService preferenceService;
    private final ActorContext actorContext;

    @Operation(summary = "获取当前用户通知偏好")
    @GetMapping
    public Result<NotificationPreference> get() {
        Long userId = actorContext.currentUserId().orElseThrow();
        return Result.success(preferenceService.getByUserId(userId));
    }

    @Operation(summary = "更新通知偏好")
    @PutMapping
    public Result<NotificationPreference> update(@RequestBody UpdateRequest request) {
        Long userId = actorContext.currentUserId().orElseThrow();
        return Result.success(
                preferenceService.upsert(userId, request.preferences(), request.quietStart(), request.quietEnd()));
    }

    /** 更新请求体 */
    record UpdateRequest(String preferences, LocalTime quietStart, LocalTime quietEnd) {}
}
