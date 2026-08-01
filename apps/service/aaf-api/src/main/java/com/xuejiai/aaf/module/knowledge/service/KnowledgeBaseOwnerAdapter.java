package com.xuejiai.aaf.module.knowledge.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.engine.knowledge.graph.KnowledgeBaseOwnerPort;

import lombok.RequiredArgsConstructor;

/** 知识库归属者查询适配器。 */
@Component
@RequiredArgsConstructor
public class KnowledgeBaseOwnerAdapter implements KnowledgeBaseOwnerPort {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public Long findOwnerId(Long knowledgeBaseId) {
        var ownerIds =
                jdbcTemplate.queryForList(
                        "SELECT owner_id FROM ai_knowledge_base WHERE id = ? AND deleted = false",
                        Long.class,
                        knowledgeBaseId);
        return ownerIds.isEmpty() ? null : ownerIds.get(0);
    }
}
