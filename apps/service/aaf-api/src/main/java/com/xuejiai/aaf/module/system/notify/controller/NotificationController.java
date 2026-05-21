package com.xuejiai.aaf.module.system.notify.controller;

import java.util.List;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.model.PageResult;
import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.security.ActorContext;
import com.xuejiai.aaf.module.system.notify.service.NotificationService;
import com.xuejiai.aaf.module.system.notify.vo.NotificationPageDTO;
import com.xuejiai.aaf.module.system.notify.vo.NotificationVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** 消息通知接口。 */
@Tag(name = "消息通知")
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final ActorContext actorContext;

    @Operation(summary = "分页查询通知")
    @GetMapping
    public Result<PageResult<NotificationVO>> page(
            @Validated @ParameterObject NotificationPageDTO request) {
        Long userId = actorContext.currentUserId().orElseThrow();
        return Result.success(notificationService.page(userId, request));
    }

    @Operation(summary = "获取未读数量")
    @GetMapping("/unread-count")
    public Result<Long> unreadCount() {
        Long userId = actorContext.currentUserId().orElseThrow();
        return Result.success(notificationService.unreadCount(userId));
    }

    @Operation(summary = "标记已读")
    @PutMapping("/read")
    public Result<Void> markAsRead(@RequestBody List<Long> ids) {
        Long userId = actorContext.currentUserId().orElseThrow();
        notificationService.markAsRead(userId, ids);
        return Result.success();
    }

    @Operation(summary = "删除通知")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        Long userId = actorContext.currentUserId().orElseThrow();
        notificationService.delete(userId, id);
        return Result.success();
    }
}
