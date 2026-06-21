package com.xuejiai.aaf.framework.messaging.sms;

import java.util.Map;

import com.xuejiai.aaf.framework.messaging.ProviderResponse;

/** 短信发送器接口，多厂商实现。 */
public interface SmsSender {

    /**
     * 发送短信。
     *
     * @return 厂商响应（含 provider/apiRequestId/apiCode/apiMsg），便于上层落库
     */
    ProviderResponse send(String phone, String templateCode, Map<String, String> params);
}
