package com.xuejiai.aaf.framework.messaging;

import java.util.List;

/** 消息日志写入接口，由业务层实现（依赖倒置，framework 不依赖 api）。 */
public interface MessageLogWriter {

    /** 创建待发送日志，返回日志 ID */
    Long createPending(
            String channel,
            String templateCode,
            List<String> recipients,
            String subject,
            String content);

    /** 更新日志状态为成功 */
    void markSuccess(Long logId);

    /** 更新日志状态为失败 */
    void markFailed(Long logId, String errorMsg);
}
