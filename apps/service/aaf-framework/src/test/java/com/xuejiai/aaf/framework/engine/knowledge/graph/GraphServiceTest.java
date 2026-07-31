package com.xuejiai.aaf.framework.engine.knowledge.graph;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import com.xuejiai.aaf.test.BaseMockitoUnitTest;

class GraphServiceTest extends BaseMockitoUnitTest {

    @Mock private KnowledgeEntityRepository entityRepository;
    @InjectMocks private GraphService graphService;

    @Test
    @DisplayName("Given 知识库实体关系 When 查询快照 Then 返回业务节点和 source target 边")
    void should_return_nodes_and_edges_when_snapshot_is_requested() {
        // 准备参数
        var source = entity("source-uuid", "AAF", "Concept", 3L);
        var target = entity("target-uuid", "Agent", "Concept", 3L);
        var relation = new KnowledgeRelation();
        relation.setId("relation-uuid");
        relation.setType("CONTAINS");
        relation.setSourceDocumentId(11L);
        relation.setTarget(target);
        source.setRelations(List.of(relation));
        when(entityRepository.findGraphByKnowledgeBaseId(3L)).thenReturn(List.of(source, target));

        // 调用
        var snapshot = graphService.snapshot(3L);

        // 断言
        assertThat(snapshot.nodes())
                .extracting(GraphService.GraphNode::id)
                .containsExactly("source-uuid", "target-uuid");
        assertThat(snapshot.edges())
                .singleElement()
                .satisfies(
                        edge -> {
                            assertThat(edge.id()).isEqualTo("relation-uuid");
                            assertThat(edge.sourceId()).isEqualTo("source-uuid");
                            assertThat(edge.targetId()).isEqualTo("target-uuid");
                            assertThat(edge.type()).isEqualTo("CONTAINS");
                        });
    }

    @Test
    @DisplayName("Given 两文档共享抽取实体 When 依次清理 Then 每次删关系后回收知识库孤立实体")
    void should_delete_all_extracted_orphans_after_each_document_is_cleared() {
        // 调用
        graphService.clearDocumentData(3L, 11L);
        graphService.clearDocumentData(3L, 12L);

        // 断言
        var ordered = inOrder(entityRepository);
        ordered.verify(entityRepository).deleteDocumentRelations(3L, 11L);
        ordered.verify(entityRepository).deleteExtractedOrphanEntities(3L);
        ordered.verify(entityRepository).deleteDocumentRelations(3L, 12L);
        ordered.verify(entityRepository).deleteExtractedOrphanEntities(3L);
    }

    private KnowledgeEntity entity(String id, String name, String type, Long knowledgeBaseId) {
        var entity = new KnowledgeEntity();
        entity.setId(id);
        entity.setName(name);
        entity.setType(type);
        entity.setKnowledgeBaseId(knowledgeBaseId);
        return entity;
    }
}
