package com.xuejiai.aaf.framework.engine.settlement;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * 模拟支付渠道适配器——直接返回成功，用于开发和测试。
 *
 * <p>B2 生产硬隔离（双保险）：
 *
 * <ul>
 *   <li>{@link ConditionalOnProperty}：仅 {@code aaf.pay.mock.enabled=true} 时注册，默认关闭
 *   <li>{@link Profile}：{@code prod} Profile 下即使配置误开也不注册 Bean，MOCK 渠道对 {@code SettlementEngine}
 *       不可见，下单会被"不支持的支付渠道"直接拒绝
 * </ul>
 */
@Slf4j
@Component
@Profile("!prod")
@ConditionalOnProperty(
        prefix = "aaf.pay.mock",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = false)
public class MockPayChannelAdapter implements PayChannelAdapter {

    public static final String CHANNEL_CODE = "MOCK";

    /** 记录已支付订单（模拟对账用） */
    private final ConcurrentMap<String, PaidRecord> paidOrders = new ConcurrentHashMap<>();

    private record PaidRecord(String channelOrderNo, long amount) {}

    @Override
    public String channelCode() {
        return CHANNEL_CODE;
    }

    @Override
    public PayResult charge(ChargeRequest request) {
        log.info("模拟支付: outTradeNo={}, amount={}", request.outTradeNo(), request.amount());
        var channelOrderNo =
                "MOCK_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        paidOrders.put(request.outTradeNo(), new PaidRecord(channelOrderNo, request.amount()));
        return new PayResult(true, PayStatus.PAID, request.outTradeNo(), channelOrderNo, "模拟支付成功");
    }

    @Override
    public PayResult withdraw(WithdrawRequest request) {
        log.info("模拟提现: outTradeNo={}, amount={}", request.outTradeNo(), request.amount());
        var channelOrderNo =
                "MOCK_W_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);
        return new PayResult(true, PayStatus.PAID, request.outTradeNo(), channelOrderNo, "模拟提现成功");
    }

    @Override
    public RefundResult refund(RefundRequest request) {
        log.info("模拟退款: refundNo={}, amount={}", request.refundNo(), request.amount());
        return new RefundResult(true, request.refundNo(), "模拟退款成功");
    }

    @Override
    public QueryResult queryStatus(String outTradeNo) {
        var record = paidOrders.get(outTradeNo);
        return record != null ? new QueryResult(PayStatus.PAID, record.channelOrderNo()) : null;
    }

    @Override
    public List<BillItem> downloadBill(LocalDate date) {
        // 返回内存中记录的所有已支付订单作为模拟账单
        return paidOrders.entrySet().stream()
                .map(e -> new BillItem(e.getKey(), e.getValue().amount(), PayStatus.PAID))
                .toList();
    }
}
