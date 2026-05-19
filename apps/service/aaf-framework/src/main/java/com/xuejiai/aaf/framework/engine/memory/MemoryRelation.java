/**
 * 记忆原子间关系。
 *
 * @author AaronZZH & Kiro
 */
package com.xuejiai.aaf.framework.engine.memory;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.*;

import lombok.Getter;
import lombok.Setter;

/** 原子间关系：因果、时序、关联等。 */
@Getter
@Setter
@Entity
@Table(name = "memory_relation", indexes = {
    @Index(columnList = "sourceId"),
    @Index(columnList = "targetId"),
    @Index(columnList = "relationType")
})
public class MemoryRelation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** 源原子 ID */
    @Column(nullable = false)
    private UUID sourceId;

    /** 目标原子 ID */
    @Column(nullable = false)
    private UUID targetId;

    /** 关系类型：causal / temporal / associative / similar */
    @Column(nullable = false, length = 50)
    private String relationType;

    /** 关系权重 */
    @Column(nullable = false)
    private Double weight = 1.0;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (this.createdAt == null) this.createdAt = Instant.now();
    }
}
