package com.xuejiai.aaf.framework.task.queue;

import java.util.List;
import java.util.Optional;

/** 死信队列管理边界。 */
public interface DeadLetterQueue {

    /** 按最新优先分页读取死信。 */
    List<DeadLetterMessage> list(int offset, int limit);

    /** 按 Redis Stream 记录 ID 查询死信。 */
    Optional<DeadLetterMessage> find(String recordId);

    /** 死信总数。 */
    long count();

    /** 重新入队并在成功后删除原死信。仅用于非受管 raw 消息。 */
    boolean retry(String recordId);

    record DeadLetterMessage(String recordId, AsyncTaskMessage task) {}
}
