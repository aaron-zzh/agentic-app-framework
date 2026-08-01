package com.xuejiai.aaf.framework.engine.settlement;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;

import lombok.extern.slf4j.Slf4j;

/** 结算引擎默认实现——路由到对应的支付渠道适配器。 */
@Slf4j
@Service
public class DefaultSettlementEngine implements SettlementEngine {

    private final Map<String, PayChannelAdapter> adapterMap;

    public DefaultSettlementEngine(List<PayChannelAdapter> adapters) {
        this.adapterMap = new HashMap<>();
        for (var adapter : adapters) {
            for (var code : adapter.supportedChannelCodes()) {
                adapterMap.put(code, adapter);
            }
        }
        log.info("结算引擎初始化，已注册渠道: {}", adapterMap.keySet());
    }

    @Override
    public PayResult charge(ChargeRequest request) {
        // m19：引擎边界统一校验金额为正——负数/零金额此前可一路传到渠道 SDK
        requirePositiveAmount(request.amount(), "支付金额");
        return getAdapter(request.channelCode()).charge(request);
    }

    @Override
    public PayResult withdraw(WithdrawRequest request) {
        requirePositiveAmount(request.amount(), "提现金额");
        return getAdapter(request.channelCode()).withdraw(request);
    }

    @Override
    public RefundResult refund(RefundRequest request) {
        requirePositiveAmount(request.amount(), "退款金额");
        // M25：原单总额是渠道计算退款比例的依据，缺失或小于退款额一定是调用方组装错误
        if (request.originalAmount() < request.amount()) {
            throw new BusinessException(
                    GlobalErrorCode.BAD_REQUEST,
                    "原单金额不能小于退款金额: original=%d, refund=%d"
                            .formatted(request.originalAmount(), request.amount()));
        }
        return getAdapter(request.channelCode()).refund(request);
    }

    /**
     * 查询支付状态。
     *
     * <p>m18：按订单自带的 channelCode 精确路由，不遍历全部渠道远程查询；适配器在"查询异常"时返回 null， 与"渠道查无此交易"（{@link
     * PayStatus#NOT_FOUND}）区分，调用方对 null 必须保持原状态不变更。
     */
    @Override
    public QueryResult queryStatus(String channelCode, String outTradeNo) {
        return getAdapter(channelCode).queryStatus(outTradeNo);
    }

    /** M28：回调验签统一入口，按信封 channelCode 路由；未注册渠道直接拒绝而非抛异常，避免向外泄露渠道装配情况。 */
    @Override
    public NotifyResult verifyAndParseNotify(NotifyEnvelope envelope) {
        var adapter = adapterMap.get(envelope.channelCode());
        if (adapter == null) {
            log.warn("收到未注册渠道的回调，直接拒绝: channelCode={}", envelope.channelCode());
            return NotifyResult.rejected("渠道未注册");
        }
        return adapter.verifyAndParseNotify(envelope);
    }

    @Override
    public void close(String channelCode, String outTradeNo) {
        getAdapter(channelCode).close(outTradeNo);
    }

    @Override
    public boolean isChannelSupported(String channelCode) {
        return adapterMap.containsKey(channelCode);
    }

    private PayChannelAdapter getAdapter(String channelCode) {
        var adapter = adapterMap.get(channelCode);
        if (adapter == null) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "不支持的支付渠道: " + channelCode);
        }
        return adapter;
    }

    /** m19：金额必须为正，负/零金额在渠道侧行为不确定（可能被接受形成异常单）。 */
    private void requirePositiveAmount(long amount, String label) {
        if (amount <= 0) {
            throw new BusinessException(
                    GlobalErrorCode.BAD_REQUEST, "%s必须大于 0: %d".formatted(label, amount));
        }
    }
}
