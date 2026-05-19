package com.xuejiai.aaf.framework.messaging;

import java.util.List;

/** 统一消息发送服务接口。 */
public interface MessageService {

    /** 发送单条消息 */
    void send(MessageRequest request);

    /** 批量发送消息 */
    void batchSend(List<MessageRequest> requests);
}
