package com.xuejiai.aaf.module.billing.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
import com.xuejiai.aaf.module.billing.vo.AdminSubscriptionDTO;
import com.xuejiai.aaf.module.billing.vo.DowngradeDTO;
import com.xuejiai.aaf.module.billing.vo.SubscribeDTO;
import com.xuejiai.aaf.module.billing.vo.SubscriptionCheckoutStatusVO;
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

    @Operation(summary = "新购、升级或同 SKU 手动续费")
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/subscribe")
    public Result<PayOrderVO> subscribe(@Valid @RequestBody SubscribeDTO dto) {
        return Result.success(
                subscriptionService.subscribe(currentUserId(), dto.skuCode(), dto.channelCode()));
    }

    @Operation(summary = "查询指定用户当前订阅")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @GetMapping("/admin/users/{userId}")
    public Result<SubscriptionVO> getUserSubscription(@PathVariable Long userId) {
        return Result.success(subscriptionCrudService.getActiveForUser(userId));
    }

    @Operation(summary = "为指定用户开通或升级会员")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @PostMapping("/admin/users/{userId}")
    public Result<SubscriptionVO> activateByAdmin(
            @PathVariable Long userId, @Valid @RequestBody AdminSubscriptionDTO dto) {
        subscriptionService.activateByAdmin(userId, dto.skuCode());
        return Result.success(subscriptionCrudService.getActiveForUser(userId));
    }

    @Operation(summary = "当前用户订阅")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/me")
    public Result<SubscriptionVO> me() {
        return Result.success(subscriptionCrudService.getActiveForUser(currentUserId()));
    }

    @Operation(summary = "查询会员支付单履约状态")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/checkouts/{payOrderId}")
    public Result<SubscriptionCheckoutStatusVO> checkoutStatus(@PathVariable Long payOrderId) {
        return Result.success(subscriptionService.getCheckoutStatus(currentUserId(), payOrderId));
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
                        subscriptionService.downgrade(currentUserId(), dto.skuCode())));
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
                .currentOwnerId()
                .orElseThrow(
                        () ->
                                new com.xuejiai.aaf.common.exception.BusinessException(
                                        com.xuejiai.aaf.common.exception.GlobalErrorCode
                                                .UNAUTHORIZED,
                                        "请先登录"));
    }
}
