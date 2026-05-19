package com.xuejiai.aaf.framework.messaging;

import java.util.List;
import java.util.Map;

/** 渠道发送器接口，各渠道实现此接口。 */
public interface ChannelSender {

    /** 支持的渠道 */
    MessageChannel channel();

    /** 发送消息到指定接收人 */
    void send(List<String> recipients, String subject, String content, Map<String, Object> variables);
}
