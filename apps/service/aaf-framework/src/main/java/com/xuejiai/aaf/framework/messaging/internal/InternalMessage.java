package com.xuejiai.aaf.framework.messaging.internal;

import lombok.Builder;
import lombok.Getter;

/**
 * 站内信发送请求。
 *
 * <p>推荐使用链式构建：
 *
 * <pre>
 * InternalMessage.builder().userId(userId).type("task").title("完成").body("详情").build()
 * InternalMessage.builder().userId(userId).type("progress").title("50%").strategy(SSE_ONLY).build()
 * </pre>
 *
 * @author Kiro
 */
@Getter
@Builder
public class InternalMessage {

    /** 推送策略 */
    public enum PushStrategy {
        PERSIST_ONLY, // 只存库
        WS_ONLY, // 存库 + WS
        SSE_ONLY, // 不存库 + SSE
        ALL // 存库 + WS + SSE（默认）
    }

    private final Long userId;

    /** 消息类型（approval/mention/task/system/progress/batch 等） */
    @Builder.Default private final String type = "system";

    private final String title;
    private final String body;
    private final String relatedUrl;
    private final String entityType;
    private final Long entityId;

    /** 推送策略，null = 按 type 默认策略 */
    private final PushStrategy strategy;
}
