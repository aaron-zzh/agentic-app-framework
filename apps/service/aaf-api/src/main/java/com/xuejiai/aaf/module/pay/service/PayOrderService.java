package com.xuejiai.aaf.module.pay.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.enums.pay.PayOrderStatusEnum;
import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.framework.engine.settlement.*;
import com.xuejiai.aaf.framework.engine.settlement.channel.BrokerageBalanceChannelAdapter;
import com.xuejiai.aaf.module.brokerage.repository.BrokerageUserRepository;
import com.xuejiai.aaf.module.pay.domain.PayOrder;
import com.xuejiai.aaf.module.pay.repository.PayOrderRepository;
import com.xuejiai.aaf.module.pay.vo.PayNotifyDTO;
import com.xuejiai.aaf.module.pay.vo.PayOrderCreateDTO;
import com.xuejiai.aaf.module.pay.vo.PayOrderVO;
import com.xuejiai.aaf.module.system.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 支付订单服务 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayOrderService {

    private final PayOrderRepository payOrderRepository;
    private final SettlementEngine settlementEngine;

    @org.springframework.context.annotation.Lazy
    private final BrokerageUserRepository brokerageUserRepository;

    private final UserRepository userRepository;

    /** 创建支付单并发起支付 */
    @Transactional
    public PayOrderVO create(PayOrderCreateDTO dto) {
        var order = new PayOrder();
        order.setMerchantOrderNo(dto.merchantOrderNo());
        order.setSubject(dto.subject());
        order.setBody(dto.body());
        order.setAmount(dto.amount());
        order.setChannelCode(dto.channelCode());
        order.setUserId(dto.userId());
        order.setExpireTime(LocalDateTime.now().plusMinutes(30));
        payOrderRepository.save(order);

        // 余额支付：先扣余额，再标记成功
        if (BrokerageBalanceChannelAdapter.CHANNEL_CODE.equals(dto.channelCode())) {
            return handleBalancePayment(order, dto);
        }

        // 调用结算引擎发起支付
        var result =
                settlementEngine.charge(
                        new ChargeRequest(
                                dto.merchantOrderNo(),
                                dto.amount(),
                                dto.subject(),
                                dto.channelCode(),
                                null));

        if (result.success()) {
            order.setStatus(PayOrderStatusEnum.SUCCESS.getCode());
            order.setChannelOrderNo(result.channelOrderNo());
            order.setSuccessTime(LocalDateTime.now());
        }
        if (result.codeUrl() != null) {
            order.setCodeUrl(result.codeUrl());
        }
        payOrderRepository.save(order);
        return toVO(order);
    }

    /** 余额支付：原子扣减 brokerage_user.balance，成功后标记支付单 */
    private PayOrderVO handleBalancePayment(PayOrder order, PayOrderCreateDTO dto) {
        // 通过 user_id 找到 contact_id
        var user = userRepository.findById(dto.userId()).orElse(null);
        if (user == null || user.getContactId() == null) {
            order.setStatus(PayOrderStatusEnum.CLOSED.getCode());
            payOrderRepository.save(order);
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "用户未关联联系人，无法使用余额支付");
        }
        // 校验并原子扣减余额
        var bu = brokerageUserRepository.findByContactId(user.getContactId()).orElse(null);
        if (bu == null || bu.getBalance() < dto.amount()) {
            order.setStatus(PayOrderStatusEnum.CLOSED.getCode());
            payOrderRepository.save(order);
            throw new BusinessException(
                    GlobalErrorCode.BAD_REQUEST,
                    "分销余额不足，当前余额: " + (bu == null ? 0 : bu.getBalance()) + " 分");
        }
        int updated = brokerageUserRepository.reduceBalance(user.getContactId(), dto.amount());
        if (updated == 0) {
            order.setStatus(PayOrderStatusEnum.CLOSED.getCode());
            payOrderRepository.save(order);
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "余额扣减失败，请重试");
        }
        // 标记支付成功
        order.setStatus(PayOrderStatusEnum.SUCCESS.getCode());
        order.setChannelOrderNo("BAL_" + dto.merchantOrderNo());
        order.setSuccessTime(LocalDateTime.now());
        payOrderRepository.save(order);
        return toVO(order);
    }

    /** 微信真实回调处理（已验签），返回 payOrderId */
    @Transactional
    public Long handleWxNotify(String outTradeNo, String channelOrderNo) {
        return markSuccess(outTradeNo, channelOrderNo);
    }

    /** 支付宝真实回调处理（已验签），返回 payOrderId */
    @Transactional
    public Long handleAlipayNotify(String outTradeNo, String tradeNo) {
        return markSuccess(outTradeNo, tradeNo);
    }

    private Long markSuccess(String merchantOrderNo, String channelOrderNo) {
        var order = payOrderRepository.findByMerchantOrderNo(merchantOrderNo).orElse(null);
        if (order == null) {
            log.warn("支付回调：找不到支付单, merchantOrderNo={}", merchantOrderNo);
            return null;
        }
        if (order.getStatus().equals(PayOrderStatusEnum.SUCCESS.getCode())) {
            log.info("支付单已成功，忽略重复回调: merchantOrderNo={}", merchantOrderNo);
            return order.getId();
        }
        order.setStatus(PayOrderStatusEnum.SUCCESS.getCode());
        order.setChannelOrderNo(channelOrderNo);
        order.setSuccessTime(LocalDateTime.now());
        payOrderRepository.save(order);
        return order.getId();
    }

    /** 支付回调处理（Mock 渠道，返回支付单 ID） */
    @Transactional
    public Long handleNotify(PayNotifyDTO dto) {
        var order =
                payOrderRepository
                        .findByMerchantOrderNo(dto.merchantOrderNo())
                        .orElseThrow(
                                () -> new BusinessException(GlobalErrorCode.NOT_FOUND, "支付单不存在"));

        // M28：无签名的结构化回调仅用于 Mock 渠道（开发/测试）；真实渠道须经渠道验签的原始回调确认，
        // 防止伪造未签名通知将任意真实订单标记为已付。真实渠道带签名回调端点为后续支付改造项。
        if (!com.xuejiai.aaf.framework.engine.settlement.MockPayChannelAdapter.CHANNEL_CODE.equals(
                order.getChannelCode())) {
            throw new BusinessException(GlobalErrorCode.FORBIDDEN, "真实渠道回调须经验签，禁止未签名通知");
        }

        if (!order.getStatus().equals(PayOrderStatusEnum.WAITING.getCode())) {
            log.warn("支付单已处理，忽略回调: merchantOrderNo={}", dto.merchantOrderNo());
            return null;
        }

        if (dto.success()) {
            order.setStatus(PayOrderStatusEnum.SUCCESS.getCode());
            order.setChannelOrderNo(dto.channelOrderNo());
            order.setSuccessTime(LocalDateTime.now());
        } else {
            order.setStatus(PayOrderStatusEnum.CLOSED.getCode());
        }
        payOrderRepository.save(order);
        log.info("支付回调处理完成: merchantOrderNo={}, success={}", dto.merchantOrderNo(), dto.success());
        return dto.success() ? order.getId() : null;
    }

    /** 判断支付单是否已成功 */
    public boolean isSuccess(Long payOrderId) {
        return payOrderRepository
                .findById(payOrderId)
                .map(o -> o.getStatus().equals(PayOrderStatusEnum.SUCCESS.getCode()))
                .orElse(false);
    }

    /** 查询支付单 */
    @Transactional(readOnly = true)
    public PayOrderVO getById(Long id) {
        var order =
                payOrderRepository
                        .findById(id)
                        .orElseThrow(
                                () -> new BusinessException(GlobalErrorCode.NOT_FOUND, "支付单不存在"));
        return toVO(order);
    }

    private PayOrderVO toVO(PayOrder o) {
        return new PayOrderVO(
                o.getId(),
                o.getMerchantOrderNo(),
                o.getSubject(),
                o.getAmount(),
                o.getStatus(),
                o.getChannelCode(),
                o.getChannelOrderNo(),
                o.getUserId(),
                o.getExpireTime(),
                o.getSuccessTime(),
                o.getRefundAmount(),
                o.getCreateTime(),
                o.getCodeUrl());
    }
}
