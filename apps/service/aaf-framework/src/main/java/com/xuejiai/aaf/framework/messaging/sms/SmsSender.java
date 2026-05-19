package com.xuejiai.aaf.framework.messaging.sms;

import java.util.Map;

/** 短信发送器接口，多厂商实现。 */
public interface SmsSender {

    /** 发送短信 */
    void send(String phone, String templateCode, Map<String, String> params);
}
