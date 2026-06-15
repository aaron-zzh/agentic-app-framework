package com.xuejiai.aaf.module.system.notify.controller;

import java.util.List;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.xuejiai.aaf.common.model.PageResult;
import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.messaging.ws.WebSocketSessionManager;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.module.system.notify.service.NotificationService;
import com.xuejiai.aaf.module.system.notify.vo.NotificationPageDTO;
import com.xuejiai.aaf.module.system.notify.vo.NotificationVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 消息通知接口。
 *
 * @author AaronZZH & Kiro
 */
@Tag(name = "消息通知")
@Slf4j
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final OperatorContext operatorContext;
    private final WebSocketSessionManager sessionManager;
    private final ObjectMapper objectMapper;

    @Operation(summary = "分页查询通知")
    @GetMapping
    public Result<PageResult<NotificationVO>> page(
            @Validated @ParameterObject NotificationPageDTO request) {
        Long userId = operatorContext.currentUserId().orElseThrow();
        return Result.success(notificationService.page(userId, request));
    }

    @Operation(summary = "获取未读数量")
    @GetMapping("/unread-count")
    public Result<Long> unreadCount() {
        Long userId = operatorContext.currentUserId().orElseThrow();
        return Result.success(notificationService.unreadCount(userId));
    }

    @Operation(summary = "标记已读")
    @PutMapping("/read")
    public Result<Void> markAsRead(@RequestBody List<Long> ids) {
        Long userId = operatorContext.currentUserId().orElseThrow();
        notificationService.markAsRead(userId, ids);
        return Result.success();
    }

    @Operation(summary = "删除通知")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        Long userId = operatorContext.currentUserId().orElseThrow();
        notificationService.delete(userId, id);
        return Result.success();
    }

    @Operation(summary = "测试：向指定用户推送 WebSocket 通知（不传 userId 则推给自己）")
    @PostMapping("/test-push")
    public Result<Void> testPush(
            @RequestParam(required = false) Long userId,
            @RequestBody(required = false) TestPushDTO dto) {
        Long targetId = userId != null ? userId : operatorContext.currentUserId().orElseThrow();
        String title = dto != null && dto.title() != null ? dto.title() : "测试通知";
        String body = dto != null && dto.body() != null ? dto.body() : "这是一条测试推送消息";
        try {
            var payload =
                    objectMapper.writeValueAsString(
                            java.util.Map.of(
                                    "type", "notification",
                                    "notificationType", "system",
                                    "title", title,
                                    "body", body,
                                    "relatedUrl", ""));
            sessionManager.sendToUser(targetId, payload);
        } catch (Exception e) {
            log.error("test-push 序列化失败", e);
        }
        return Result.success();
    }

    record TestPushDTO(String title, String body) {}
}
