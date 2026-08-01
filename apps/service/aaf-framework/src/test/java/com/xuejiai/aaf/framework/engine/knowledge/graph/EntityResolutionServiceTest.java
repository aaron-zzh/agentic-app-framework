package com.xuejiai.aaf.framework.engine.knowledge.graph;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.ai.chat.client.ChatClient;

import com.xuejiai.aaf.framework.engine.knowledge.embedding.EmbeddingService;
import com.xuejiai.aaf.test.BaseMockitoUnitTest;

/**
 * {@link EntityResolutionService} 单元测试。
 *
 * <p>粗筛分组用完全平行/完全垂直的向量构造确定性的相似度关系（平行向量余弦相似度=1.0，垂直向量=0.0）， 避免真实 embedding 的浮点误差影响断言。
 */
class EntityResolutionServiceTest extends BaseMockitoUnitTest {

    @Mock private KnowledgeEntityRepository entityRepository;
    @Mock private GraphService graphService;
    @Mock private EmbeddingService embeddingService;
    @Mock private KnowledgeBaseOwnerPort ownerPort;

    @Test
    @DisplayName("Given 知识库实体数不足 2 个 When 消歧 Then 直接跳过不查归属者")
    void should_skip_when_fewer_than_two_entities() {
        // 准备参数
        when(entityRepository.findByKnowledgeBaseId(3L))
                .thenReturn(List.of(entity("1", "张三", null)));
        var service =
                new EntityResolutionService(
                        entityRepository,
                        graphService,
                        embeddingService,
                        ownerPort,
                        mock(ChatClient.Builder.class));

        // 调用
        service.resolve(3L);

        // 断言
        verify(ownerPort, never()).findOwnerId(any());
        verify(graphService, never()).mergeEntities(any(), any());
    }

    @Test
    @DisplayName("Given 知识库已被删除 When 消歧 Then 跳过不做 embedding 调用")
    void should_skip_when_knowledge_base_owner_not_found() {
        // 准备参数
        when(entityRepository.findByKnowledgeBaseId(3L))
                .thenReturn(List.of(entity("1", "张三", null), entity("2", "李四", null)));
        when(ownerPort.findOwnerId(3L)).thenReturn(null);
        var service =
                new EntityResolutionService(
                        entityRepository,
                        graphService,
                        embeddingService,
                        ownerPort,
                        mock(ChatClient.Builder.class));

        // 调用
        service.resolve(3L);

        // 断言
        verify(embeddingService, never()).embedBatch(any(), any(int.class), any());
        verify(graphService, never()).mergeEntities(any(), any());
    }

    @Test
    @DisplayName("Given 两实体向量高度相似 When 消歧 Then 圈入候选组并交给 LLM 终审")
    void should_group_similar_entities_and_call_llm_for_review() {
        // 准备参数：张三/张经理 向量平行（相似度 1.0），李四向量垂直（相似度 0.0，不入组）
        var zhangSan = entity("1", "张三", "阿里巴巴的架构师");
        var zhangManager = entity("2", "张经理", "阿里巴巴的技术负责人");
        var liSi = entity("3", "李四", "另一家公司的员工");
        when(entityRepository.findByKnowledgeBaseId(3L))
                .thenReturn(List.of(zhangSan, zhangManager, liSi));
        when(ownerPort.findOwnerId(3L)).thenReturn(7L);
        when(embeddingService.embedBatch(any(), eq(20), eq(7L)))
                .thenReturn(
                        List.of(new float[] {1f, 0f}, new float[] {1f, 0f}, new float[] {0f, 1f}));

        var chatClientBuilder =
                mockChatClientReturning(
                        """
                [{"groupIndex":0,"mergeIds":["1","2"]}]
                """);
        var service =
                new EntityResolutionService(
                        entityRepository,
                        graphService,
                        embeddingService,
                        ownerPort,
                        chatClientBuilder);

        // 调用
        service.resolve(3L);

        // 断言：只有"张三"+"张经理"这组被合并，李四不受影响
        verify(graphService).mergeEntities("1", List.of("2"));
        verify(graphService, org.mockito.Mockito.times(1)).mergeEntities(any(), any());
    }

    @Test
    @DisplayName("Given LLM 终审认为候选组不该合并 When 消歧 Then 不调用合并")
    void should_not_merge_when_llm_rejects_the_candidate_group() {
        // 准备参数：两个实体向量相似但 LLM 判断不该合并
        when(entityRepository.findByKnowledgeBaseId(3L))
                .thenReturn(List.of(entity("1", "苹果公司", "科技公司"), entity("2", "梨公司", "水果公司")));
        when(ownerPort.findOwnerId(3L)).thenReturn(7L);
        when(embeddingService.embedBatch(any(), eq(20), eq(7L)))
                .thenReturn(List.of(new float[] {1f, 0f}, new float[] {1f, 0f}));

        var chatClientBuilder = mockChatClientReturning("[]");
        var service =
                new EntityResolutionService(
                        entityRepository,
                        graphService,
                        embeddingService,
                        ownerPort,
                        chatClientBuilder);

        // 调用
        service.resolve(3L);

        // 断言
        verify(graphService, never()).mergeEntities(any(), any());
    }

    @Test
    @DisplayName("Given LLM 终审返回不存在的实体 id When 消歧 Then 跳过该组不抛异常")
    void should_skip_decision_with_unknown_entity_id() {
        // 准备参数
        when(entityRepository.findByKnowledgeBaseId(3L))
                .thenReturn(List.of(entity("1", "张三", null), entity("2", "张经理", null)));
        when(ownerPort.findOwnerId(3L)).thenReturn(7L);
        when(embeddingService.embedBatch(any(), eq(20), eq(7L)))
                .thenReturn(List.of(new float[] {1f, 0f}, new float[] {1f, 0f}));

        var chatClientBuilder =
                mockChatClientReturning(
                        """
                [{"groupIndex":0,"mergeIds":["1","不存在的id"]}]
                """);
        var service =
                new EntityResolutionService(
                        entityRepository,
                        graphService,
                        embeddingService,
                        ownerPort,
                        chatClientBuilder);

        // 调用 + 断言：不抛异常，也不调用合并
        service.resolve(3L);
        verify(graphService, never()).mergeEntities(any(), any());
    }

    private KnowledgeEntity entity(String id, String name, String description) {
        var entity = new KnowledgeEntity();
        entity.setId(id);
        entity.setName(name);
        entity.setDescription(description);
        return entity;
    }

    /** 构造一条 mock 好的 ChatClient.Builder 链路，call().content() 固定返回 content。 */
    private ChatClient.Builder mockChatClientReturning(String content) {
        var chatClient = mock(ChatClient.class);
        var requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        var callResponseSpec = mock(ChatClient.CallResponseSpec.class);
        var builder = mock(ChatClient.Builder.class);

        when(builder.build()).thenReturn(chatClient);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(org.mockito.ArgumentMatchers.anyString())).thenReturn(requestSpec);
        when(requestSpec.user(org.mockito.ArgumentMatchers.anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn(content);
        return builder;
    }
}
