package com.xuejiai.aaf.framework.intelligent.cognition.port;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import com.xuejiai.aaf.framework.intelligent.cognition.model.MemoryRecord;
import com.xuejiai.aaf.framework.intelligent.cognition.model.MemoryRecord.ExplicitConfirmation;
import com.xuejiai.aaf.framework.intelligent.cognition.model.MemoryRecord.MemorySubject;

/** Cognition 长期记忆唯一写入边界。 */
public interface MemoryWritePort {

    List<MemoryRecord> append(List<MemoryRecord> memories);

    MemoryRecord correct(
            MemorySubject subject,
            String memoryId,
            MemoryRecord replacement,
            ExplicitConfirmation confirmation);

    void forget(
            MemorySubject subject,
            List<String> memoryIds,
            ExplicitConfirmation confirmation,
            Instant at);

    int mergeVisitor(
            MemorySubject visitor,
            MemorySubject user,
            ExplicitConfirmation confirmation,
            Instant at);

    void expireAnonymous(Instant at);

    static Instant anonymousExpiry(Instant now, Duration ttl) {
        if (ttl == null || ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("匿名记忆 TTL 必须为正数");
        }
        return now.plus(ttl);
    }
}
