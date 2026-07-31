package com.xuejiai.aaf.framework.engine.knowledge.pipeline;

import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import com.xuejiai.aaf.common.enums.knowledge.DocumentStatusEnum;
import com.xuejiai.aaf.framework.engine.knowledge.KnowledgeVectorService;
import com.xuejiai.aaf.framework.engine.knowledge.chunker.AutoChunkStrategySelector;
import com.xuejiai.aaf.framework.engine.knowledge.chunker.ChunkerFactory;
import com.xuejiai.aaf.framework.engine.knowledge.chunker.DocumentChunk;
import com.xuejiai.aaf.framework.engine.knowledge.graph.EntityExtractionService;
import com.xuejiai.aaf.framework.engine.knowledge.graph.GraphService;
import com.xuejiai.aaf.framework.engine.knowledge.importer.ImporterFactory;
import com.xuejiai.aaf.framework.engine.meta.runtime.TaskExecutionInProgressException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 知识库处理管道服务（导入→分块→向量化入库）。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgePipelineService {

    private static final int TOTAL_STEPS = PipelineStep.values().length;
    private static final int MAX_ERROR_LENGTH = 2_000;

    private final ImporterFactory importerFactory;
    private final ChunkerFactory chunkerFactory;
    private final AutoChunkStrategySelector strategySelector;
    private final KnowledgeChunkStore chunkStore;
    private final KnowledgeVectorService vectorService;
    private final EntityExtractionService entityExtractionService;
    private final GraphService graphService;
    private final KnowledgeDocumentStatusPort documentStatusPort;

    private final ConcurrentHashMap<Long, PipelineProgress> progressMap = new ConcurrentHashMap<>();

    /** 执行完整管道。外部调用必须根据返回结果决定是否触发任务重试。 */
    public PipelineResult process(
            Long knowledgeBaseId,
            Long documentId,
            InputStream input,
            String filename,
            Runnable executionGuard) {
        var startTime = Instant.now();
        try {
            executionGuard.run();
            updateDocumentStatus(documentId, DocumentStatusEnum.PROCESSING.getCode(), 0, null);
            executionGuard.run();

            updateProgress(documentId, PipelineStep.IMPORT, 0, startTime);
            var importer =
                    importerFactory
                            .getImporter(filename)
                            .orElseThrow(
                                    () -> new IllegalArgumentException("不支持的文件类型: " + filename));
            var importResult = importer.importDocument(input, filename);
            executionGuard.run();
            var fullText =
                    String.join(
                            "\n", importResult.sections().stream().map(s -> s.content()).toList());

            updateProgress(documentId, PipelineStep.CHUNK, 1, startTime);
            var ext = filename.substring(filename.lastIndexOf('.') + 1);
            var chunkConfig = strategySelector.selectStrategy(ext, knowledgeBaseId);
            var chunker = chunkerFactory.getChunker(chunkConfig.strategy());
            var chunks =
                    chunker.chunk(
                            fullText,
                            chunkConfig,
                            Map.of(
                                    "knowledge_base_id", knowledgeBaseId,
                                    "document_id", documentId,
                                    "filename", filename));
            executionGuard.run();

            updateProgress(documentId, PipelineStep.EMBED, 2, startTime);
            executionGuard.run();
            var chunkIds = chunkStore.replace(knowledgeBaseId, documentId, chunks);
            executionGuard.run();

            updateProgress(documentId, PipelineStep.STORE, 3, startTime);
            executionGuard.run();
            vectorService.store(buildDocuments(chunks, chunkIds, knowledgeBaseId, documentId));
            executionGuard.run();

            updateProgress(documentId, PipelineStep.GRAPH_EXTRACT, 4, startTime);
            executionGuard.run();
            graphService.clearDocumentData(knowledgeBaseId, documentId);
            executionGuard.run();
            for (var chunk : chunks) {
                entityExtractionService.extractAndSave(
                        chunk.content(), knowledgeBaseId, documentId, executionGuard);
                executionGuard.run();
            }

            updateDocumentStatus(
                    documentId, DocumentStatusEnum.COMPLETED.getCode(), chunks.size(), null);
            executionGuard.run();
            progressMap.remove(documentId);

            var durationMs = System.currentTimeMillis() - startTime.toEpochMilli();
            return new PipelineResult(
                    true, documentId, chunks.size(), chunks.size(), durationMs, null);
        } catch (TaskExecutionInProgressException inProgress) {
            throw inProgress;
        } catch (Exception failure) {
            ensureCurrentOrDefer(executionGuard, failure);
            var errorMessage = truncate(failure.getMessage());
            log.error("知识库管道执行失败，documentId={}", documentId, failure);
            try {
                clearDocumentData(knowledgeBaseId, documentId, executionGuard);
            } catch (TaskExecutionInProgressException inProgress) {
                inProgress.addSuppressed(failure);
                throw inProgress;
            } catch (RuntimeException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
                log.error("清理失败知识库文档数据异常，documentId={}", documentId, cleanupFailure);
            }
            try {
                executionGuard.run();
                updateDocumentStatus(
                        documentId, DocumentStatusEnum.FAILED.getCode(), 0, errorMessage);
                executionGuard.run();
            } catch (TaskExecutionInProgressException inProgress) {
                inProgress.addSuppressed(failure);
                throw inProgress;
            } catch (RuntimeException statusFailure) {
                failure.addSuppressed(statusFailure);
                log.error("更新知识库文档失败状态异常，documentId={}", documentId, statusFailure);
            }
            updateProgressError(documentId, errorMessage, startTime);

            var durationMs = System.currentTimeMillis() - startTime.toEpochMilli();
            return new PipelineResult(false, documentId, 0, 0, durationMs, errorMessage);
        }
    }

    private void ensureCurrentOrDefer(Runnable executionGuard, Exception failure) {
        try {
            executionGuard.run();
        } catch (TaskExecutionInProgressException inProgress) {
            inProgress.addSuppressed(failure);
            throw inProgress;
        }
    }

    /** 清理指定文档的关系块、向量和图谱数据。 */
    public void clearDocumentData(Long knowledgeBaseId, Long documentId) {
        clearDocumentRelationalData(documentId);
        clearDocumentGraphData(knowledgeBaseId, documentId);
    }

    /** 清理可参与调用方关系库事务的知识块和向量。 */
    public void clearDocumentRelationalData(Long documentId) {
        chunkStore.clear(documentId);
    }

    /** 清理不可参与关系库事务的文档图谱数据。 */
    public void clearDocumentGraphData(Long knowledgeBaseId, Long documentId) {
        graphService.clearDocumentData(knowledgeBaseId, documentId);
    }

    private void clearDocumentData(Long knowledgeBaseId, Long documentId, Runnable executionGuard) {
        executionGuard.run();
        chunkStore.clear(documentId);
        executionGuard.run();
        graphService.clearDocumentData(knowledgeBaseId, documentId);
        executionGuard.run();
    }

    public Optional<PipelineProgress> getProgress(Long documentId) {
        return Optional.ofNullable(progressMap.get(documentId));
    }

    private void updateProgress(
            Long documentId, PipelineStep step, int completed, Instant startTime) {
        progressMap.put(
                documentId,
                new PipelineProgress(documentId, step, TOTAL_STEPS, completed, startTime, null));
    }

    private void updateProgressError(Long documentId, String errorMessage, Instant startTime) {
        var current = progressMap.get(documentId);
        var step = current != null ? current.currentStep() : PipelineStep.IMPORT;
        var completed = current != null ? current.completedSteps() : 0;
        progressMap.put(
                documentId,
                new PipelineProgress(
                        documentId, step, TOTAL_STEPS, completed, startTime, errorMessage));
    }

    private List<Document> buildDocuments(
            List<DocumentChunk> chunks,
            List<Long> chunkIds,
            Long knowledgeBaseId,
            Long documentId) {
        if (chunks.size() != chunkIds.size()) {
            throw new IllegalStateException("知识块与持久化 ID 数量不一致");
        }
        return java.util.stream.IntStream.range(0, chunks.size())
                .mapToObj(
                        index -> {
                            var chunk = chunks.get(index);
                            var metadata = new java.util.HashMap<>(chunk.metadata());
                            metadata.put("chunk_id", chunkIds.get(index));
                            metadata.put("chunk_index", chunk.index());
                            metadata.put("knowledge_base_id", knowledgeBaseId);
                            metadata.put("document_id", documentId);
                            return new Document(chunk.content(), metadata);
                        })
                .toList();
    }

    private void updateDocumentStatus(
            Long documentId, int status, int chunkCount, String errorMessage) {
        documentStatusPort.updateStatus(documentId, status, chunkCount, errorMessage);
    }

    private String truncate(String message) {
        if (message == null || message.length() <= MAX_ERROR_LENGTH) {
            return message;
        }
        return message.substring(0, MAX_ERROR_LENGTH);
    }
}
