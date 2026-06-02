package com.xuejiai.aaf.module.pay.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.enums.pay.BizOrderTypeEnum;
import com.xuejiai.aaf.framework.engine.credit.CreditService;
import com.xuejiai.aaf.module.pay.vo.BizOrderCreateDTO;
import com.xuejiai.aaf.module.pay.vo.PayOrderCreateDTO;
import com.xuejiai.aaf.module.pay.vo.PayOrderVO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 充值业务编排：创建业务订单 → 创建支付单 → 支付成功回调 → 积分入账 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RechargeService {

    private final BizOrderService bizOrderService;
    private final PayOrderService payOrderService;
    private final CreditService creditService;

    /** 发起充值：创建业务订单 + 支付单，MOCK 渠道同步入账 */
    @Transactional
    public PayOrderVO initiateRecharge(Long userId, long amount, String channelCode) {
        // 创建业务订单
        var bizOrder =
                bizOrderService.create(
                        userId,
                        new BizOrderCreateDTO(
                                BizOrderTypeEnum.RECHARGE.getCode(), "积分充值", amount, channelCode));

        // 创建支付单
        var payOrder =
                payOrderService.create(
                        new PayOrderCreateDTO(
                                bizOrder.orderNo(), "积分充值", null, amount, channelCode, userId));

        // 关联支付单
        bizOrderService.bindPayOrder(bizOrder.id(), payOrder.id());

        // 如果支付已同步成功（MOCK 渠道），直接触发积分入账
        if (payOrderService.isSuccess(payOrder.id())) {
            onPaySuccess(payOrder.id());
        }
        return payOrder;
    }

    /** 充值成功回调：积分入账（从 PayOrder 获取金额） */
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
        creditService.earn(
                bizOrder.getUserId(), bizOrder.getTotalAmount(), "RECHARGE", bizOrder.getOrderNo());
        log.info(
                "充值成功，积分入账: userId={}, amount={}", bizOrder.getUserId(), bizOrder.getTotalAmount());
    }
}
