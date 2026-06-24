package com.xuejiai.aaf.module.billing.service;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.enums.pay.BizOrderTypeEnum;
import com.xuejiai.aaf.framework.engine.credit.CreditService;
import com.xuejiai.aaf.module.billing.repository.CreditPackageRepository;
import com.xuejiai.aaf.module.pay.handler.PaySuccessHandler;
import com.xuejiai.aaf.module.pay.service.BizOrderService;
import com.xuejiai.aaf.module.user.growth.event.UserGrowthEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 积分套餐购买支付成功处理器。
 *
 * <p>通过 BizOrderItem.productId 关联套餐，按套餐定义的积分数（含赠送）发放积分。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CreditRechargePayHandler implements PaySuccessHandler {

    private final BizOrderService bizOrderService;
    private final CreditPackageRepository creditPackageRepository;
    private final CreditService creditService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public String bizOrderType() {
        return BizOrderTypeEnum.CREDIT_PACKAGE.getCode();
    }

    @Override
    @Transactional
    public void onPaySuccess(Long payOrderId) {
        var bizOrder = bizOrderService.findByPayOrderId(payOrderId);
        if (bizOrder == null) return;

        bizOrderService.markPaid(bizOrder.getId());

        // 从 BizOrderItem 取套餐 ID
        var items = bizOrderService.getItems(bizOrder.getId());
        if (items.isEmpty()) {
            log.warn("[CreditRechargePayHandler] 订单无明细行: orderId={}", bizOrder.getId());
            return;
        }

        Long packageId = Long.parseLong(items.get(0).productId());
        creditPackageRepository
                .findById(packageId)
                .ifPresentOrElse(
                        pkg -> {
                            long total = pkg.getCredits() + pkg.getBonusCredits();
                            // 充值积分有效期 2 年，与 RechargeService 走同一条路径（CreditService.earn）
                            creditService.earn(
                                    bizOrder.getUserId(),
                                    total,
                                    "CREDIT_PACKAGE",
                                    bizOrder.getOrderNo());
                            log.info(
                                    "[CreditRechargePayHandler] 积分发放: userId={}, credits={}, pkg={}",
                                    bizOrder.getUserId(),
                                    total,
                                    pkg.getName());
                            eventPublisher.publishEvent(
                                    new UserGrowthEvent(bizOrder.getUserId(), "credit.recharge.success"));
                        },
                        () ->
                                log.warn(
                                        "[CreditRechargePayHandler] 套餐不存在: packageId={}",
                                        packageId));
    }
}
