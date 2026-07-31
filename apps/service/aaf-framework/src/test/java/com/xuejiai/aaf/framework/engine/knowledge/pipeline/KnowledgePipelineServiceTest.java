package com.xuejiai.aaf.framework.engine.knowledge.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.ai.document.Document;

import com.xuejiai.aaf.common.enums.knowledge.DocumentStatusEnum;
import com.xuejiai.aaf.framework.engine.knowledge.KnowledgeVectorService;
import com.xuejiai.aaf.framework.engine.knowledge.chunker.AutoChunkStrategySelector;
import com.xuejiai.aaf.framework.engine.knowledge.chunker.ChunkConfig;
import com.xuejiai.aaf.framework.engine.knowledge.chunker.ChunkStrategy;
import com.xuejiai.aaf.framework.engine.knowledge.chunker.ChunkerFactory;
import com.xuejiai.aaf.framework.engine.knowledge.chunker.DocumentChunk;
import com.xuejiai.aaf.framework.engine.knowledge.chunker.DocumentChunker;
import com.xuejiai.aaf.framework.engine.knowledge.graph.EntityExtractionService;
import com.xuejiai.aaf.framework.engine.knowledge.graph.GraphService;
import com.xuejiai.aaf.framework.engine.knowledge.importer.DocumentImporter;
import com.xuejiai.aaf.framework.engine.knowledge.importer.DocumentSection;
import com.xuejiai.aaf.framework.engine.knowledge.importer.ImportResult;
import com.xuejiai.aaf.framework.engine.knowledge.importer.ImporterFactory;
import com.xuejiai.aaf.framework.engine.meta.runtime.TaskExecutionInProgressException;
import com.xuejiai.aaf.test.BaseMockitoUnitTest;

class KnowledgePipelineServiceTest extends BaseMockitoUnitTest {

    private static final Runnable VALID_LEASE = () -> {};

    @Mock private ImporterFactory importerFactory;
    @Mock private ChunkerFactory chunkerFactory;
    @Mock private AutoChunkStrategySelector strategySelector;
    @Mock private KnowledgeChunkStore chunkStore;
    @Mock private KnowledgeVectorService vectorService;
    @Mock private EntityExtractionService entityExtractionService;
    @Mock private GraphService graphService;
    @Mock private KnowledgeDocumentStatusPort documentStatusPort;
    @Mock private DocumentImporter importer;
    @Mock private DocumentChunker chunker;
    @InjectMocks private KnowledgePipelineService pipelineService;

    @Test
    @DisplayName("Given 可解析文档 When 执行管道 Then 持久化块并以 snake_case metadata 写入向量库")
    void should_store_chunks_and_vector_metadata_when_pipeline_succeeds() throws Exception {
        // 准备参数
        var config = new ChunkConfig(ChunkStrategy.FIXED_SIZE, 512, 64);
        var chunk = new DocumentChunk("知识内容", 0, Map.of("page", 1), 4);
        when(importerFactory.getImporter("guide.md")).thenReturn(Optional.of(importer));
        when(importer.importDocument(any(), eq("guide.md")))
                .thenReturn(
                        new ImportResult(
                                List.of(new DocumentSection("知识内容", 0, Map.of())), "guide", 4));
        when(strategySelector.selectStrategy("md", 3L)).thenReturn(config);
        when(chunkerFactory.getChunker(ChunkStrategy.FIXED_SIZE)).thenReturn(chunker);
        when(chunker.chunk(anyString(), eq(config), anyMap())).thenReturn(List.of(chunk));
        when(chunkStore.replace(3L, 11L, List.of(chunk))).thenReturn(List.of(101L));

        // 调用
        var result =
                pipelineService.process(
                        3L,
                        11L,
                        new ByteArrayInputStream("content".getBytes()),
                        "guide.md",
                        VALID_LEASE);

        // 断言
        assertThat(result.success()).isTrue();
        assertThat(result.chunkCount()).isEqualTo(1);
        var documents = documentListCaptor();
        verify(vectorService).store(documents.capture());
        var metadata = documents.getValue().getFirst().getMetadata();
        assertThat(metadata)
                .containsEntry("knowledge_base_id", 3L)
                .containsEntry("document_id", 11L)
                .containsEntry("chunk_id", 101L)
                .containsEntry("chunk_index", 0);
        verify(chunkStore).replace(3L, 11L, List.of(chunk));
        verify(entityExtractionService)
                .extractAndSave(eq("知识内容"), eq(3L), eq(11L), any(Runnable.class));
        verify(documentStatusPort, times(2))
                .updateStatus(
                        eq(11L),
                        anyInt(),
                        anyInt(),
                        org.mockito.ArgumentMatchers.nullable(String.class));
    }

    @Test
    @DisplayName("Given 图谱抽取失败 When 执行管道 Then 清理块向量图谱并标记失败")
    void should_cleanup_all_document_data_when_graph_extraction_fails() throws Exception {
        // 准备参数
        var config = new ChunkConfig(ChunkStrategy.FIXED_SIZE, 512, 64);
        var chunk = new DocumentChunk("知识内容", 0, Map.of(), 4);
        when(importerFactory.getImporter("guide.md")).thenReturn(Optional.of(importer));
        when(importer.importDocument(any(), eq("guide.md")))
                .thenReturn(
                        new ImportResult(
                                List.of(new DocumentSection("知识内容", 0, Map.of())), "guide", 4));
        when(strategySelector.selectStrategy("md", 3L)).thenReturn(config);
        when(chunkerFactory.getChunker(ChunkStrategy.FIXED_SIZE)).thenReturn(chunker);
        when(chunker.chunk(anyString(), eq(config), anyMap())).thenReturn(List.of(chunk));
        when(chunkStore.replace(3L, 11L, List.of(chunk))).thenReturn(List.of(101L));
        doThrow(new IllegalStateException("graph unavailable"))
                .when(entityExtractionService)
                .extractAndSave(eq("知识内容"), eq(3L), eq(11L), any(Runnable.class));

        // 调用
        var result =
                pipelineService.process(
                        3L,
                        11L,
                        new ByteArrayInputStream("content".getBytes()),
                        "guide.md",
                        VALID_LEASE);

        // 断言
        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("graph unavailable");
        verify(chunkStore).clear(11L);
        verify(graphService, times(2)).clearDocumentData(3L, 11L);
        verify(documentStatusPort, times(2))
                .updateStatus(
                        eq(11L),
                        anyInt(),
                        anyInt(),
                        org.mockito.ArgumentMatchers.nullable(String.class));
    }

    @Test
    @DisplayName("Given 向量写入失败 When 执行管道 Then 清理该文档块和向量并标记失败")
    void should_cleanup_document_data_when_vector_store_fails() throws Exception {
        // 准备参数
        var config = new ChunkConfig(ChunkStrategy.FIXED_SIZE, 512, 64);
        var chunk = new DocumentChunk("知识内容", 0, Map.of(), 4);
        when(importerFactory.getImporter("guide.md")).thenReturn(Optional.of(importer));
        when(importer.importDocument(any(), eq("guide.md")))
                .thenReturn(
                        new ImportResult(
                                List.of(new DocumentSection("知识内容", 0, Map.of())), "guide", 4));
        when(strategySelector.selectStrategy("md", 3L)).thenReturn(config);
        when(chunkerFactory.getChunker(ChunkStrategy.FIXED_SIZE)).thenReturn(chunker);
        when(chunker.chunk(anyString(), eq(config), anyMap())).thenReturn(List.of(chunk));
        when(chunkStore.replace(3L, 11L, List.of(chunk))).thenReturn(List.of(101L));
        doThrow(new IllegalStateException("vector unavailable")).when(vectorService).store(any());

        // 调用
        var result =
                pipelineService.process(
                        3L,
                        11L,
                        new ByteArrayInputStream("content".getBytes()),
                        "guide.md",
                        VALID_LEASE);

        // 断言
        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("vector unavailable");
        verify(chunkStore).clear(11L);
        verify(graphService).clearDocumentData(3L, 11L);
        verify(documentStatusPort, times(2))
                .updateStatus(
                        eq(11L),
                        anyInt(),
                        anyInt(),
                        org.mockito.ArgumentMatchers.nullable(String.class));
    }

    @Test
    @DisplayName("Given 向量写入后租约丢失 When 执行管道 Then 不清理也不写失败状态")
    void should_not_cleanup_or_mark_failed_after_execution_lease_is_lost() throws Exception {
        // 准备参数
        var config = new ChunkConfig(ChunkStrategy.FIXED_SIZE, 512, 64);
        var chunk = new DocumentChunk("知识内容", 0, Map.of(), 4);
        when(importerFactory.getImporter("guide.md")).thenReturn(Optional.of(importer));
        when(importer.importDocument(any(), eq("guide.md")))
                .thenReturn(
                        new ImportResult(
                                List.of(new DocumentSection("知识内容", 0, Map.of())), "guide", 4));
        when(strategySelector.selectStrategy("md", 3L)).thenReturn(config);
        when(chunkerFactory.getChunker(ChunkStrategy.FIXED_SIZE)).thenReturn(chunker);
        when(chunker.chunk(anyString(), eq(config), anyMap())).thenReturn(List.of(chunk));
        when(chunkStore.replace(3L, 11L, List.of(chunk))).thenReturn(List.of(101L));
        var guardCalls = new AtomicInteger();
        Runnable expiringGuard =
                () -> {
                    if (guardCalls.incrementAndGet() == 8) {
                        throw new TaskExecutionInProgressException("lease lost");
                    }
                };

        // 调用 + 断言
        assertThatThrownBy(
                        () ->
                                pipelineService.process(
                                        3L,
                                        11L,
                                        new ByteArrayInputStream("content".getBytes()),
                                        "guide.md",
                                        expiringGuard))
                .isInstanceOf(TaskExecutionInProgressException.class);
        verify(vectorService).store(any());
        verify(chunkStore, never()).clear(anyLong());
        verify(graphService, never()).clearDocumentData(anyLong(), anyLong());
        verify(documentStatusPort, times(1))
                .updateStatus(
                        eq(11L),
                        anyInt(),
                        anyInt(),
                        org.mockito.ArgumentMatchers.nullable(String.class));
    }

    @Test
    @DisplayName("Given 首次图谱写入后失租 When 继任执行 Then 先清旧图再重新抽取")
    void should_replace_stale_graph_when_successor_takes_over() throws Exception {
        // 准备参数
        var config = new ChunkConfig(ChunkStrategy.FIXED_SIZE, 512, 64);
        var chunk = new DocumentChunk("知识内容", 0, Map.of(), 4);
        when(importerFactory.getImporter("guide.md")).thenReturn(Optional.of(importer));
        when(importer.importDocument(any(), eq("guide.md")))
                .thenReturn(
                        new ImportResult(
                                List.of(new DocumentSection("知识内容", 0, Map.of())), "guide", 4));
        when(strategySelector.selectStrategy("md", 3L)).thenReturn(config);
        when(chunkerFactory.getChunker(ChunkStrategy.FIXED_SIZE)).thenReturn(chunker);
        when(chunker.chunk(anyString(), eq(config), anyMap())).thenReturn(List.of(chunk));
        when(chunkStore.replace(3L, 11L, List.of(chunk))).thenReturn(List.of(101L));
        var extractionCalls = new AtomicInteger();
        doAnswer(
                        invocation -> {
                            if (extractionCalls.getAndIncrement() == 0) {
                                Runnable guard = invocation.getArgument(3);
                                guard.run();
                            }
                            return null;
                        })
                .when(entityExtractionService)
                .extractAndSave(eq("知识内容"), eq(3L), eq(11L), any(Runnable.class));
        var guardCalls = new AtomicInteger();
        Runnable expiringGuard =
                () -> {
                    if (guardCalls.incrementAndGet() == 11) {
                        throw new TaskExecutionInProgressException("lease lost");
                    }
                };

        // 首次执行在图谱写入后失租
        assertThatThrownBy(
                        () ->
                                pipelineService.process(
                                        3L,
                                        11L,
                                        new ByteArrayInputStream("content".getBytes()),
                                        "guide.md",
                                        expiringGuard))
                .isInstanceOf(TaskExecutionInProgressException.class);
        verify(graphService).clearDocumentData(3L, 11L);
        verify(documentStatusPort, never())
                .updateStatus(
                        eq(11L),
                        eq(DocumentStatusEnum.FAILED.getCode()),
                        anyInt(),
                        org.mockito.ArgumentMatchers.nullable(String.class));

        // 继任执行
        var result =
                pipelineService.process(
                        3L,
                        11L,
                        new ByteArrayInputStream("content".getBytes()),
                        "guide.md",
                        VALID_LEASE);

        // 断言
        assertThat(result.success()).isTrue();
        var ordered = inOrder(graphService, entityExtractionService);
        ordered.verify(graphService).clearDocumentData(3L, 11L);
        ordered.verify(entityExtractionService).extractAndSave("知识内容", 3L, 11L, expiringGuard);
        ordered.verify(graphService).clearDocumentData(3L, 11L);
        ordered.verify(entityExtractionService).extractAndSave("知识内容", 3L, 11L, VALID_LEASE);
    }

    @Test
    @DisplayName("Given 文档格式不支持 When 执行管道 Then 标记失败并返回失败结果")
    void should_mark_failed_when_importer_is_missing() {
        // 准备参数
        when(importerFactory.getImporter("guide.bin")).thenReturn(Optional.empty());

        // 调用
        var result =
                pipelineService.process(
                        3L,
                        11L,
                        new ByteArrayInputStream("content".getBytes()),
                        "guide.bin",
                        VALID_LEASE);

        // 断言
        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("不支持的文件类型");
        verify(documentStatusPort, times(2))
                .updateStatus(
                        eq(11L),
                        anyInt(),
                        anyInt(),
                        org.mockito.ArgumentMatchers.nullable(String.class));
        verify(vectorService, times(0)).store(any());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private ArgumentCaptor<List<Document>> documentListCaptor() {
        return ArgumentCaptor.forClass((Class) List.class);
    }
}
