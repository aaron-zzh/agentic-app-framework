package com.xuejiai.aaf.framework.intelligent.cognition.port;

import java.time.Instant;
import java.util.List;

import com.xuejiai.aaf.framework.intelligent.cognition.model.MemoryRecord;
import com.xuejiai.aaf.framework.intelligent.cognition.model.MemoryRecord.ExplicitConfirmation;
import com.xuejiai.aaf.framework.intelligent.cognition.model.MemoryRecord.MemorySubject;

/** Cognition 记忆管理查询与按范围遗忘边界。 */
public interface MemoryManagementPort {

    MemoryPage list(
            MemorySubject subject, String scope, int offset, int limit, Instant at);

    List<MemoryRecord> search(
            MemorySubject subject, String keyword, String scope, int limit, Instant at);

    long count(MemorySubject subject, String scope, Instant at);

    int forgetScope(
            MemorySubject subject,
            String scope,
            ExplicitConfirmation confirmation,
            Instant at);

    record MemoryPage(List<MemoryRecord> items, long total) {
        public MemoryPage {
            items = items == null ? List.of() : List.copyOf(items);
        }
    }
}
