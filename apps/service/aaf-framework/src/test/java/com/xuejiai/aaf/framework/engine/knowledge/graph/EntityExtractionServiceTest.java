package com.xuejiai.aaf.framework.engine.knowledge.graph;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.springframework.ai.chat.client.ChatClient;

import com.xuejiai.aaf.test.BaseMockitoUnitTest;

/**
 * {@link EntityExtractionService} 单元测试。
 *
 * <p>{@code extract()} 直接调用 {@code ChatClient.Builder} 链式 API，Mockito 逐层 mock {@code
 * build().prompt().system().user().call().content()} 的返回值来驱动整条链路，不涉及真实 LLM 调用。
 */
class EntityExtractionServiceTest extends BaseMockitoUnitTest {

    @Mock private GraphService graphService;
    @Mock private KnowledgeEntityRepository entityRepository;

    @Test
    @DisplayName("Given LLM 抽取出带描述的新实体 When 保存 Then description 写入 KnowledgeEntity")
    void should_persist_entity_description_when_entity_is_new() {
        // 准备参数：mock ChatClient 链路返回一个带主宾语描述的三元组
        var chatClientBuilder =
                mockChatClientReturning(
                        """
                [{"subject":"张三","subjectDesc":"阿里巴巴的首席架构师","predicate":"就职于","object":"阿里巴巴","objectDesc":"一家科技公司","confidence":0.95}]
                """);
        var service =
                new EntityExtractionService(chatClientBuilder, graphService, entityRepository);
        when(entityRepository.findByNameAndKnowledgeBaseId("张三", 3L)).thenReturn(Optional.empty());
        when(entityRepository.findByNameAndKnowledgeBaseId("阿里巴巴", 3L))
                .thenReturn(Optional.empty());
        when(graphService.saveEntity(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // 调用
        service.extractAndSave("张三在阿里巴巴担任首席架构师。", 3L, 11L, () -> {});

        // 断言：两个新实体分别带上了 LLM 给的描述
        var captor = ArgumentCaptor.forClass(KnowledgeEntity.class);
        verify(graphService, org.mockito.Mockito.times(2)).saveEntity(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(KnowledgeEntity::getName, KnowledgeEntity::getDescription)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("张三", "阿里巴巴的首席架构师"),
                        org.assertj.core.groups.Tuple.tuple("阿里巴巴", "一家科技公司"));
    }

    @Test
    @DisplayName("Given 实体已存在 When 再次抽取到同名实体 Then 不覆盖已有描述")
    void should_not_overwrite_existing_entity_when_name_already_exists() {
        // 准备参数：实体已存在，本次抽取不应该再新建或修改它
        var chatClientBuilder =
                mockChatClientReturning(
                        """
                [{"subject":"张三","subjectDesc":"新的描述","predicate":"就职于","object":"阿里巴巴","objectDesc":"","confidence":0.9}]
                """);
        var service =
                new EntityExtractionService(chatClientBuilder, graphService, entityRepository);
        var existing = new KnowledgeEntity();
        existing.setId("existing-uuid");
        existing.setName("张三");
        existing.setDescription("原有描述");
        when(entityRepository.findByNameAndKnowledgeBaseId("张三", 3L))
                .thenReturn(Optional.of(existing));
        when(entityRepository.findByNameAndKnowledgeBaseId("阿里巴巴", 3L))
                .thenReturn(Optional.empty());
        when(graphService.saveEntity(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // 调用
        service.extractAndSave("张三在阿里巴巴担任首席架构师。", 3L, 11L, () -> {});

        // 断言：已存在实体不重新保存，描述保持原值
        assertThat(existing.getDescription()).isEqualTo("原有描述");
        verify(graphService, org.mockito.Mockito.times(1)).saveEntity(any());
    }

    @Test
    @DisplayName("Given LLM 返回非 JSON 内容 When 抽取 Then 抛出非法状态异常")
    void should_throw_when_llm_response_is_not_valid_json() {
        // 准备参数
        var chatClientBuilder = mockChatClientReturning("不是 JSON");
        var service =
                new EntityExtractionService(chatClientBuilder, graphService, entityRepository);

        // 调用 + 断言
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.extract("任意文本", 3L, 11L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("不是有效 JSON");
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
