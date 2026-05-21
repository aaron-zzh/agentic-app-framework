package com.xuejiai.aaf.module.system.notify.controller;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.security.ActorContext;
import com.xuejiai.aaf.module.system.notify.domain.Subscription;
import com.xuejiai.aaf.module.system.notify.service.SubscriptionService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** 字段变更订阅接口。 */
@Tag(name = "字段变更订阅")
@RestController
@RequestMapping("/api/subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;
    private final ActorContext actorContext;

    @Operation(summary = "创建订阅")
    @PostMapping
    public Result<Subscription> create(@RequestBody CreateRequest request) {
        Long userId = actorContext.currentUserId().orElseThrow();
        var sub =
                subscriptionService.create(
                        userId,
                        request.entityType(),
                        request.entityId(),
                        request.fields(),
                        request.channels());
        return Result.success(sub);
    }

    @Operation(summary = "取消订阅")
    @DeleteMapping("/{id}")
    public Result<Void> cancel(@PathVariable Long id) {
        Long userId = actorContext.currentUserId().orElseThrow();
        subscriptionService.cancel(userId, id);
        return Result.success(null);
    }

    @Operation(summary = "查询当前用户订阅")
    @GetMapping
    public Result<List<Subscription>> list(
            @RequestParam String entityType, @RequestParam Long entityId) {
        Long userId = actorContext.currentUserId().orElseThrow();
        return Result.success(
                subscriptionService.listByUserAndEntity(userId, entityType, entityId));
    }

    /** 创建订阅请求体 */
    record CreateRequest(String entityType, Long entityId, String fields, String channels) {}
}
