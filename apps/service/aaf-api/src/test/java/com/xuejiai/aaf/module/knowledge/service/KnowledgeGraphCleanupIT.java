package com.xuejiai.aaf.module.knowledge.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.xuejiai.aaf.framework.engine.knowledge.graph.GraphService;
import com.xuejiai.aaf.framework.engine.knowledge.graph.KnowledgeEntity;
import com.xuejiai.aaf.framework.engine.knowledge.graph.KnowledgeEntityRepository;
import com.xuejiai.aaf.framework.engine.knowledge.graph.KnowledgeRelation;

@SpringBootTest(properties = "aaf.task.queue.enabled=false")
@ActiveProfiles("test")
class KnowledgeGraphCleanupIT {

    @Autowired private GraphService graphService;
    @Autowired private KnowledgeEntityRepository entityRepository;

    @BeforeEach
    void setUp() {
        entityRepository.deleteAll();
    }

    @Test
    @DisplayName("Given 两文档共享实体 When 按 A 到 B 清理 Then 不遗留抽取型孤立节点")
    void should_remove_shared_orphan_after_last_document_is_cleared() {
        // 准备参数
        var shared = graphService.saveEntity(entity("共享实体", 11L));
        var sourceA = graphService.saveEntity(entity("文档 A", 11L));
        var sourceB = graphService.saveEntity(entity("文档 B", 12L));
        graphService.saveRelation(sourceA.getId(), shared.getId(), relation(11L));
        graphService.saveRelation(sourceB.getId(), shared.getId(), relation(12L));

        // 调用
        graphService.clearDocumentData(3L, 11L);
        graphService.clearDocumentData(3L, 12L);

        // 断言
        assertThat(entityRepository.findByKnowledgeBaseId(3L)).isEmpty();
    }

    private KnowledgeEntity entity(String name, Long sourceDocumentId) {
        var entity = new KnowledgeEntity();
        entity.setName(name);
        entity.setType("Concept");
        entity.setKnowledgeBaseId(3L);
        entity.setSourceDocumentId(sourceDocumentId);
        return entity;
    }

    private KnowledgeRelation relation(Long sourceDocumentId) {
        var relation = new KnowledgeRelation();
        relation.setType("RELATES_TO");
        relation.setSourceDocumentId(sourceDocumentId);
        return relation;
    }
}
