package com.xuejiai.aaf.module.pay.service;

import static com.xuejiai.aaf.common.exception.ExceptionUtil.exception;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.enums.pay.PayOrderStatusEnum;
import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.framework.engine.settlement.*;
import com.xuejiai.aaf.framework.engine.settlement.channel.BrokerageBalanceChannelAdapter;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.module.brokerage.repository.BrokerageUserRepository;
import com.xuejiai.aaf.module.pay.ErrorCodeConstants;
import com.xuejiai.aaf.module.pay.api.PayOrderQueryApi;
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
public class PayOrderService implements PayOrderQueryApi {

    private final PayOrderRepository payOrderRepository;
    private final SettlementEngine settlementEngine;
    private final java.util.Optional<
                    com.xuejiai.aaf.framework.engine.settlement.channel.AlipayChannelAdapter>
            alipayAdapter;

    @org.springframework.context.annotation.Lazy
    private final BrokerageUserRepository brokerageUserRepository;

    private final UserRepository userRepository;

    private final BizOrderService bizOrderService;
    private final OperatorContext operatorContext;

    /** 创建支付单并发起支付 */
    @Transactional
    public PayOrderVO create(PayOrderCreateDTO dto) {
        var order = new PayOrder();
        order.setMerchantOrderNo(dto.merchantOrderNo());
        order.setSubject(dto.subject());
        order.setBody(dto.body());
        order.setAmount(dto.amount());
        order.setChannelCode(dto.channelCode());
        order.setUserId(requireCurrentOwnerId());
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

        if (result.status() == PayStatus.PAID) {
            order.setStatus(PayOrderStatusEnum.SUCCESS.getCode());
            order.setChannelOrderNo(result.channelOrderNo());
            order.setSuccessTime(LocalDateTime.now());
        }
        if (result.codeUrl() != null) {
            order.setCodeUrl(result.codeUrl());
        } else if (isAlipayRedirectChannel(dto.channelCode())) {
            // 电脑网站支付/手机网站支付：跳转表单 HTML 按需实时生成，此处仅存放跳转接口地址供前端整页跳转
            order.setCodeUrl("/api/pay/orders/" + order.getId() + "/redirect");
        }
        payOrderRepository.save(order);
        return toVO(order);
    }

    /** 余额支付：原子扣减 brokerage_user.balance，成功后标记支付单 */
    private PayOrderVO handleBalancePayment(PayOrder order, PayOrderCreateDTO dto) {
        // 通过 user_id 找到 contact_id
        var user = userRepository.findById(order.getUserId()).orElse(null);
        if (user == null || user.getContactId() == null) {
            order.setStatus(PayOrderStatusEnum.CLOSED.getCode());
            payOrderRepository.save(order);
            throw exception(ErrorCodeConstants.PAY_ORDER_USER_NOT_LINKED_CONTACT);
        }
        // 校验并原子扣减余额
        var bu = brokerageUserRepository.findByContactId(user.getContactId()).orElse(null);
        if (bu == null || bu.getBalance() < dto.amount()) {
            order.setStatus(PayOrderStatusEnum.CLOSED.getCode());
            payOrderRepository.save(order);
            throw exception(
                    ErrorCodeConstants.PAY_ORDER_BALANCE_INSUFFICIENT,
                    bu == null ? 0 : bu.getBalance());
        }
        int updated = brokerageUserRepository.reduceBalance(user.getContactId(), dto.amount());
        if (updated == 0) {
            order.setStatus(PayOrderStatusEnum.CLOSED.getCode());
            payOrderRepository.save(order);
            throw exception(ErrorCodeConstants.PAY_ORDER_BALANCE_DEDUCT_FAILED);
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

    /**
     * M3：以原子状态迁移标记支付成功，返回 payOrderId（未抢到迁移则返回 null）。
     *
     * <p>原实现"先查 → 判断非 SUCCESS → 改字段 → save"在并发回调（渠道重推 + 定时同步同时命中）下 两个事务可同时通过状态检查，各自触发一次积分入账；且
     * CLOSED/过期订单也会被改写为 SUCCESS。 改为 {@code UPDATE ... WHERE status = WAITING} 后，只有一个事务拿到 updated=1，
     * 且只有真正待支付的订单可迁移。
     */
    private Long markSuccess(String merchantOrderNo, String channelOrderNo) {
        int updated =
                payOrderRepository.transitionStatus(
                        merchantOrderNo,
                        PayOrderStatusEnum.WAITING.getCode(),
                        PayOrderStatusEnum.SUCCESS.getCode(),
                        channelOrderNo,
                        LocalDateTime.now());
        if (updated == 0) {
            log.info("支付回调未触发状态迁移（订单不存在或已非待支付）: merchantOrderNo={}", merchantOrderNo);
            return null;
        }
        return payOrderRepository
                .findByMerchantOrderNo(merchantOrderNo)
                .map(PayOrder::getId)
                .orElse(null);
    }

    /** 支付回调处理（Mock 渠道，返回支付单 ID） */
    @Transactional
    public Long handleNotify(PayNotifyDTO dto) {
        var order =
                payOrderRepository
                        .findByMerchantOrderNo(dto.merchantOrderNo())
                        .orElseThrow(() -> exception(ErrorCodeConstants.PAY_ORDER_NOT_FOUND));

        // M28：无签名的结构化回调仅用于 Mock 渠道（开发/测试）；真实渠道须经渠道验签的原始回调确认，
        // 防止伪造未签名通知将任意真实订单标记为已付。真实渠道带签名回调端点为后续支付改造项。
        if (!com.xuejiai.aaf.framework.engine.settlement.MockPayChannelAdapter.CHANNEL_CODE.equals(
                order.getChannelCode())) {
            throw exception(ErrorCodeConstants.PAY_ORDER_NOTIFY_UNSIGNED_FORBIDDEN);
        }

        // M3：与真实渠道回调走同一套原子状态迁移，避免并发回调重复入账
        if (!dto.success()) {
            payOrderRepository.transitionStatusOnly(
                    dto.merchantOrderNo(),
                    PayOrderStatusEnum.WAITING.getCode(),
                    PayOrderStatusEnum.CLOSED.getCode(),
                    LocalDateTime.now());
            log.info("支付回调处理完成（支付失败关单）: merchantOrderNo={}", dto.merchantOrderNo());
            return null;
        }
        Long payOrderId = markSuccess(dto.merchantOrderNo(), dto.channelOrderNo());
        if (payOrderId != null) {
            log.info("支付回调处理完成: merchantOrderNo={}", dto.merchantOrderNo());
        }
        return payOrderId;
    }

    /** 跨模块只读快照，不执行当前用户归属校验。 */
    @Override
    @Transactional(readOnly = true)
    public java.util.Optional<PayOrderSnapshot> findSnapshot(Long payOrderId) {
        return payOrderRepository
                .findById(payOrderId)
                .map(
                        order ->
                                new PayOrderSnapshot(
                                        order.getId(),
                                        order.getAmount(),
                                        order.getStatus(),
                                        order.getExpireTime(),
                                        order.getSuccessTime()));
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
                        .orElseThrow(() -> exception(ErrorCodeConstants.PAY_ORDER_NOT_FOUND));
        requireOwned(order);
        return toVO(order);
    }

    /** 按商户订单号查询支付单——支付宝 returnUrl 跳转回来时携带的是 out_trade_no（即 merchantOrderNo），非数据库自增 ID */
    @Transactional(readOnly = true)
    public PayOrderVO getByMerchantOrderNo(String merchantOrderNo) {
        var order =
                payOrderRepository
                        .findByMerchantOrderNo(merchantOrderNo)
                        .orElseThrow(() -> exception(ErrorCodeConstants.PAY_ORDER_NOT_FOUND));
        requireOwned(order);
        return toVO(order);
    }

    /**
     * 支付宝页面跳转表单——按需实时生成，不落库。
     *
     * <p>支付宝允许对同一笔未支付订单重复下单，故每次跳转都重新调用支付宝接口生成表单， 避免在数据库中持久化大段 HTML 内容。电脑网站支付、手机网站支付均走此逻辑。
     */
    @Transactional(readOnly = true)
    public String buildAlipayRedirectHtml(Long id) {
        var order =
                payOrderRepository
                        .findById(id)
                        .orElseThrow(() -> exception(ErrorCodeConstants.PAY_ORDER_NOT_FOUND));
        requireOwned(order);
        if (!isAlipayRedirectChannel(order.getChannelCode())) {
            throw exception(ErrorCodeConstants.PAY_ORDER_CHANNEL_MISMATCH);
        }
        if (!order.getStatus().equals(PayOrderStatusEnum.WAITING.getCode())) {
            throw exception(ErrorCodeConstants.PAY_ORDER_ALREADY_FINISHED);
        }
        var adapter =
                alipayAdapter.orElseThrow(
                        () -> exception(ErrorCodeConstants.PAY_ORDER_CHANNEL_NOT_CONFIGURED));
        var chargeRequest =
                new ChargeRequest(
                        order.getMerchantOrderNo(),
                        order.getAmount(),
                        order.getSubject(),
                        order.getChannelCode(),
                        null);
        try {
            return com.xuejiai.aaf.framework.engine.settlement.channel.AlipayChannelAdapter
                            .CHANNEL_CODE_WAP
                            .equals(order.getChannelCode())
                    ? adapter.buildWapPayForm(chargeRequest)
                    : adapter.buildPagePayForm(chargeRequest);
        } catch (com.alipay.api.AlipayApiException e) {
            log.error("支付宝跳转表单生成失败: orderId={}", id, e);
            throw exception(ErrorCodeConstants.PAY_ORDER_CHANNEL_ERROR);
        }
    }

    /** 判断渠道是否为支付宝页面跳转类（电脑网站支付/手机网站支付），此类渠道不返回 codeUrl，需按 orderId 实时生成跳转表单 */
    private boolean isAlipayRedirectChannel(String channelCode) {
        return com.xuejiai.aaf.framework.engine.settlement.channel.AlipayChannelAdapter
                        .CHANNEL_CODE_WAP
                        .equals(channelCode)
                || "alipay_pc".equals(channelCode);
    }

    private Long requireCurrentOwnerId() {
        return operatorContext
                .currentOwnerId()
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.UNAUTHORIZED));
    }

    private void requireOwned(PayOrder order) {
        if (!java.util.Objects.equals(order.getUserId(), requireCurrentOwnerId())) {
            throw new BusinessException(GlobalErrorCode.FORBIDDEN, "无权访问此支付单");
        }
    }

    private PayOrderVO toVO(PayOrder o) {
        var bizOrder = bizOrderService.findByPayOrderId(o.getId());
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
                o.getCodeUrl(),
                bizOrder != null ? bizOrder.getOrderType() : null);
    }
}
