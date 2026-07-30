package com.xuejiai.aaf.framework.task.queue;

import java.util.List;

/** 死信队列管理边界。 */
public interface DeadLetterQueue {

    /** 按最新优先分页读取死信。 */
    List<DeadLetterMessage> list(int offset, int limit);

    /** 死信总数。 */
    long count();

    /** 重新入队并在成功后删除原死信。 */
    boolean retry(String recordId);

    record DeadLetterMessage(String recordId, AsyncTaskMessage task) {}
}
