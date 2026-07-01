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
        return getAdapter(request.channelCode()).charge(request);
    }

    @Override
    public PayResult withdraw(WithdrawRequest request) {
        return getAdapter(request.channelCode()).withdraw(request);
    }

    @Override
    public RefundResult refund(RefundRequest request) {
        return getAdapter(request.channelCode()).refund(request);
    }

    @Override
    public QueryResult queryStatus(String channelCode, String outTradeNo) {
        return getAdapter(channelCode).queryStatus(outTradeNo);
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
}
