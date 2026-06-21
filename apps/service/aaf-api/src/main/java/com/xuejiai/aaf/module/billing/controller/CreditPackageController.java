package com.xuejiai.aaf.module.billing.controller;

import java.util.List;
import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import com.xuejiai.aaf.common.enums.pay.BizOrderTypeEnum;
import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.module.billing.repository.CreditPackageRepository;
import com.xuejiai.aaf.module.billing.vo.CreditPackageVO;
import com.xuejiai.aaf.module.pay.service.BizOrderService;
import com.xuejiai.aaf.module.pay.service.PayNotifyService;
import com.xuejiai.aaf.module.pay.service.PayOrderService;
import com.xuejiai.aaf.module.pay.vo.BizOrderCreateDTO;
import com.xuejiai.aaf.module.pay.vo.BizOrderItemCreateDTO;
import com.xuejiai.aaf.module.pay.vo.PayOrderCreateDTO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 积分充值套餐接口 */
@Slf4j
@Tag(name = "积分充值套餐")
@RestController
@RequestMapping("/api/billing/credit-packages")
@RequiredArgsConstructor
public class CreditPackageController {

    private final CreditPackageRepository creditPackageRepository;
    private final BizOrderService bizOrderService;
    private final PayOrderService payOrderService;
    private final PayNotifyService payNotifyService;
    private final OperatorContext operatorContext;

    @Operation(summary = "获取积分充值套餐列表")
    @GetMapping
    public Result<List<CreditPackageVO>> list() {
        var packages =
                creditPackageRepository.findByStatusOrderBySortAsc("ENABLED").stream()
                        .map(
                                p ->
                                        new CreditPackageVO(
                                                p.getId(),
                                                p.getName(),
                                                p.getCredits(),
                                                p.getBonusCredits(),
                                                p.getPrice(),
                                                p.getGroupLabel(),
                                                Boolean.TRUE.equals(p.getRecommended())))
                        .toList();
        return Result.success(packages);
    }

    @Operation(summary = "购买积分充值套餐")
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/purchase")
    @Transactional
    public Result<com.xuejiai.aaf.module.pay.vo.PayOrderVO> purchase(
            @RequestBody Map<String, String> body) {
        String packageIdStr = body.get("packageId");
        if (packageIdStr == null || packageIdStr.isBlank()) {
            return Result.error(400, "packageId 不能为空");
        }
        Long userId =
                operatorContext
                        .currentOwnerId()
                        .orElseThrow(
                                () -> new BusinessException(GlobalErrorCode.UNAUTHORIZED, "未登录"));
        var pkg =
                creditPackageRepository
                        .findById(Long.parseLong(packageIdStr))
                        .orElseThrow(
                                () -> new BusinessException(GlobalErrorCode.NOT_FOUND, "套餐不存在"));

        String channelCode = body.getOrDefault("channelCode", "MOCK");

        // 创建业务订单，通过 BizOrderItem 携带套餐 ID 供 handler 使用
        var bizOrder =
                bizOrderService.create(
                        userId,
                        new BizOrderCreateDTO(
                                BizOrderTypeEnum.CREDIT_PACKAGE.getCode(),
                                "积分套餐 " + pkg.getName(),
                                pkg.getPrice(),
                                channelCode,
                                List.of(
                                        new BizOrderItemCreateDTO(
                                                "CREDIT_PKG",
                                                String.valueOf(pkg.getId()),
                                                pkg.getName(),
                                                1,
                                                pkg.getPrice()))));

        // 创建支付单
        var payOrder =
                payOrderService.create(
                        new PayOrderCreateDTO(
                                bizOrder.orderNo(),
                                "积分套餐 " + pkg.getName(),
                                null,
                                pkg.getPrice(),
                                channelCode,
                                userId));

        bizOrderService.bindPayOrder(bizOrder.id(), payOrder.id());

        // 同步成功（MOCK/余额支付）：立即触发通知
        if (payOrderService.isSuccess(payOrder.id())) {
            payNotifyService.onPaySuccess(payOrder.id());
        }

        return Result.success(payOrder);
    }
}
