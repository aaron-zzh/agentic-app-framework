package com.xuejiai.aaf.module.billing.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.crud.BaseCrudController;
import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.module.billing.domain.Subscription;
import com.xuejiai.aaf.module.billing.service.SubscriptionCrudService;
import com.xuejiai.aaf.module.billing.service.SubscriptionService;
import com.xuejiai.aaf.module.billing.vo.DowngradeDTO;
import com.xuejiai.aaf.module.billing.vo.SubscribeDTO;
import com.xuejiai.aaf.module.billing.vo.SubscriptionPageParam;
import com.xuejiai.aaf.module.billing.vo.SubscriptionVO;
import com.xuejiai.aaf.module.pay.vo.PayOrderVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "订阅管理")
@RestController("billingSubscriptionController")
@RequestMapping("/api/billing/subscriptions")
@RequiredArgsConstructor
public class SubscriptionController
        extends BaseCrudController<
                Subscription, SubscriptionVO, Void, Void, SubscriptionPageParam> {

    private final SubscriptionCrudService subscriptionCrudService;
    private final SubscriptionService subscriptionService;
    private final OperatorContext operatorContext;

    @Override
    protected BaseCrudService<Subscription, SubscriptionVO, Void, Void, SubscriptionPageParam>
            getService() {
        return subscriptionCrudService;
    }

    @Operation(summary = "购买或升级订阅")
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/subscribe")
    public Result<PayOrderVO> subscribe(@Valid @RequestBody SubscribeDTO dto) {
        return Result.success(
                subscriptionService.subscribe(
                        currentUserId(), dto.planCode(), dto.channelCode(), dto.isYearly()));
    }

    @Operation(summary = "当前用户订阅")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/me")
    public Result<SubscriptionVO> me() {
        return Result.success(subscriptionCrudService.getActiveForUser(currentUserId()));
    }

    @Operation(summary = "取消当前订阅")
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/me/cancel")
    public Result<SubscriptionVO> cancel() {
        return Result.success(
                subscriptionCrudService.toManagementVO(
                        subscriptionService.cancel(currentUserId())));
    }

    @Operation(summary = "降级当前订阅")
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/me/downgrade")
    public Result<SubscriptionVO> downgrade(@Valid @RequestBody DowngradeDTO dto) {
        return Result.success(
                subscriptionCrudService.toManagementVO(
                        subscriptionService.downgrade(
                                currentUserId(), dto.planCode(), dto.isYearly())));
    }

    @Operation(summary = "撤销当前订阅的降级申请")
    @PreAuthorize("isAuthenticated()")
    @DeleteMapping("/me/pending-downgrade")
    public Result<SubscriptionVO> cancelPending() {
        return Result.success(
                subscriptionCrudService.toManagementVO(
                        subscriptionService.cancelPending(currentUserId())));
    }

    private Long currentUserId() {
        return operatorContext
                .currentUserId()
                .orElseThrow(
                        () ->
                                new com.xuejiai.aaf.common.exception.BusinessException(
                                        com.xuejiai.aaf.common.exception.GlobalErrorCode
                                                .UNAUTHORIZED,
                                        "请先登录"));
    }
}
