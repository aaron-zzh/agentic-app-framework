package com.xuejiai.aaf.framework.engine.settlement;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

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
        this.adapterMap =
                adapters.stream()
                        .collect(Collectors.toMap(PayChannelAdapter::channelCode, Function.identity()));
        log.info("结算引擎初始化，已注册渠道: {}", adapterMap.keySet());
    }

    @Override
    public PayResult charge(ChargeRequest request) {
        var adapter = getAdapter(request.channelCode());
        return adapter.charge(request);
    }

    @Override
    public PayResult withdraw(WithdrawRequest request) {
        var adapter = getAdapter(request.channelCode());
        return adapter.withdraw(request);
    }

    @Override
    public RefundResult refund(RefundRequest request) {
        var adapter = getAdapter(request.channelCode());
        return adapter.refund(request);
    }

    @Override
    public PayStatus queryStatus(String outTradeNo) {
        // 简化实现：遍历所有渠道查询
        for (var adapter : adapterMap.values()) {
            var status = adapter.queryStatus(outTradeNo);
            if (status != null) {
                return status;
            }
        }
        return PayStatus.UNPAID;
    }

    private PayChannelAdapter getAdapter(String channelCode) {
        var adapter = adapterMap.get(channelCode);
        if (adapter == null) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "不支持的支付渠道: " + channelCode);
        }
        return adapter;
    }
}
