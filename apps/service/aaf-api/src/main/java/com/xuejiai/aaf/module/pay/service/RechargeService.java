package com.xuejiai.aaf.module.pay.service;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.enums.pay.BizOrderTypeEnum;
import com.xuejiai.aaf.common.enums.pay.CreditTransactionSourceEnum;
import com.xuejiai.aaf.framework.engine.credit.CreditService;
import com.xuejiai.aaf.module.pay.handler.PaySuccessHandler;
import com.xuejiai.aaf.module.user.growth.event.UserGrowthEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * RECHARGE 类业务订单的支付成功处理器：标记订单已支付 → 积分入账。
 *
 * <p>B2：原 {@code initiateRecharge(amount, channelCode)} 由客户端提交金额下单，已删除。 充值下单统一由 {@code
 * CreditPackageController#purchase} 承担——金额取 {@code credit_package.price}（服务端货架定价），
 * 本类只负责支付成功后的入账，不再提供任何创建订单入口。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RechargeService implements PaySuccessHandler {

    private final BizOrderService bizOrderService;
    private final CreditService creditService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public String bizOrderType() {
        return BizOrderTypeEnum.RECHARGE.getCode();
    }

    /** 充值成功回调：积分入账（从业务订单获取金额，不信任回调入参） */
    @Override
    @Transactional
    public void onPaySuccess(Long payOrderId) {
        var bizOrder = bizOrderService.findByPayOrderId(payOrderId);
        if (bizOrder == null) {
            log.warn("支付成功但未找到关联业务订单: payOrderId={}", payOrderId);
            return;
        }
        if (!BizOrderTypeEnum.RECHARGE.getCode().equals(bizOrder.getOrderType())) {
            return;
        }
        // 标记业务订单已支付
        bizOrderService.markPaid(bizOrder.getId());
        // 积分入账（金额从业务订单获取）
        // M3：CreditService.earn 以「账户+来源+业务单号」为幂等键，并发/重复回调不会重复加分
        creditService.earn(
                bizOrder.getUserId(),
                bizOrder.getTotalAmount(),
                CreditTransactionSourceEnum.RECHARGE.getCode(),
                bizOrder.getOrderNo());
        log.info(
                "充值成功，积分入账: userId={}, amount={}", bizOrder.getUserId(), bizOrder.getTotalAmount());
        eventPublisher.publishEvent(
                new UserGrowthEvent(bizOrder.getUserId(), "credit.recharge.success"));
    }
}
