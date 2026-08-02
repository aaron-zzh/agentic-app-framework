package com.xuejiai.aaf.framework.engine.knowledge.trusted;

import java.time.Instant;
import java.util.Objects;

/** PostgreSQL 历史知识查询的显式双时态作用域。当前查询不创建该对象，统一使用数据库时钟。 */
public record KnowledgeTemporalScope(Instant validAt, Instant knownAt) {

    public KnowledgeTemporalScope {
        Objects.requireNonNull(validAt, "validAt 不能为空");
        Objects.requireNonNull(knownAt, "knownAt 不能为空");
    }
}
