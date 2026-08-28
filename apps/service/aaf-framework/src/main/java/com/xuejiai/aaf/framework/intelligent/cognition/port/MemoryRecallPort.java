package com.xuejiai.aaf.framework.intelligent.cognition.port;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import com.xuejiai.aaf.framework.intelligent.cognition.model.MemoryRecord;
import com.xuejiai.aaf.framework.intelligent.cognition.model.MemoryRecord.MemorySubject;

/** Cognition 长期记忆召回边界。 */
public interface MemoryRecallPort {

    List<MemoryRecord> recall(RecallQuery query);

    /**
     * 记忆召回请求。
     *
     * @param sessionId 本会话标识；为空表示不召回短期会话上下文
     */
    record RecallQuery(
            MemorySubject subject,
            String query,
            int maxItems,
            int characterBudget,
            String sessionId,
            Instant at) {
        public RecallQuery {
            Objects.requireNonNull(subject, "subject 不能为空");
            query = Objects.requireNonNullElse(query, "").trim();
            if (maxItems < 0 || characterBudget < 0) {
                throw new IllegalArgumentException("记忆预算不能为负数");
            }
            sessionId = sessionId == null || sessionId.isBlank() ? null : sessionId.trim();
            Objects.requireNonNull(at, "at 不能为空");
        }

        /** 不含会话维度的召回，仅取持久记忆。 */
        public RecallQuery(
                MemorySubject subject,
                String query,
                int maxItems,
                int characterBudget,
                Instant at) {
            this(subject, query, maxItems, characterBudget, null, at);
        }
    }
}
