package com.xuejiai.aaf.framework.engine.knowledge.pipeline;

import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import com.xuejiai.aaf.framework.engine.knowledge.KnowledgeVectorService;
import com.xuejiai.aaf.framework.engine.knowledge.chunker.AutoChunkStrategySelector;
import com.xuejiai.aaf.framework.engine.knowledge.chunker.ChunkerFactory;
import com.xuejiai.aaf.framework.engine.knowledge.chunker.DocumentChunk;
import com.xuejiai.aaf.framework.engine.knowledge.embedding.EmbeddingService;
import com.xuejiai.aaf.framework.engine.knowledge.importer.ImporterFactory;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 知识库处理管道服务（导入→分块→Embedding→入库）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgePipelineService {

    private static final int TOTAL_STEPS = PipelineStep.values().length;

    private final ImporterFactory importerFactory;
    private final ChunkerFactory chunkerFactory;
    private final AutoChunkStrategySelector strategySelector;
    private final EmbeddingService embeddingService;
    private final KnowledgeVectorService vectorService;
    private final EntityManager entityManager;

    private final ConcurrentHashMap<Long, PipelineProgress> progressMap = new ConcurrentHashMap<>();

    /**
     * 执行完整管道
     */
    public PipelineResult process(Long knowledgeBaseId, Long documentId, InputStream input, String filename) {
        var startTime = Instant.now();

        try {
            // IMPORT: 解析文档
            updateProgress(documentId, PipelineStep.IMPORT, 0, startTime);
            var importer = importerFactory.getImporter(filename)
                    .orElseThrow(() -> new IllegalArgumentException("不支持的文件类型: " + filename));
            var importResult = importer.importDocument(input, filename);
            var fullText = String.join("\n", importResult.sections().stream()
                    .map(s -> s.content()).toList());

            // CHUNK: 分块
            updateProgress(documentId, PipelineStep.CHUNK, 1, startTime);
            var ext = filename.substring(filename.lastIndexOf('.') + 1);
            var chunkConfig = strategySelector.selectStrategy(ext, knowledgeBaseId);
            var chunker = chunkerFactory.getChunker(chunkConfig.strategy());
            var chunks = chunker.chunk(fullText, chunkConfig, Map.of(
                    "knowledgeBaseId", knowledgeBaseId,
                    "documentId", documentId,
                    "filename", filename));

            // EMBED: 生成向量
            updateProgress(documentId, PipelineStep.EMBED, 2, startTime);
            var texts = chunks.stream().map(DocumentChunk::content).toList();
            var embeddings = embeddingService.embedBatch(texts, 20);

            // STORE: 写入向量库
            updateProgress(documentId, PipelineStep.STORE, 3, startTime);
            var documents = buildDocuments(chunks, embeddings, knowledgeBaseId, documentId);
            vectorService.store(documents);

            // 成功：更新文档状态
            updateDocumentStatus(documentId, 2, chunks.size(), null);
            progressMap.remove(documentId);

            var durationMs = System.currentTimeMillis() - startTime.toEpochMilli();
            return new PipelineResult(true, documentId, chunks.size(), embeddings.size(), durationMs, null);

        } catch (Exception e) {
            log.error("管道执行失败，documentId={}", documentId, e);
            var errorMsg = e.getMessage();
            updateDocumentStatus(documentId, 3, 0, errorMsg);
            updateProgressError(documentId, errorMsg, startTime);

            var durationMs = System.currentTimeMillis() - startTime.toEpochMilli();
            return new PipelineResult(false, documentId, 0, 0, durationMs, errorMsg);
        }
    }

    /**
     * 查询管道执行进度
     */
    public Optional<PipelineProgress> getProgress(Long documentId) {
        return Optional.ofNullable(progressMap.get(documentId));
    }

    private void updateProgress(Long documentId, PipelineStep step, int completed, Instant startTime) {
        progressMap.put(documentId, new PipelineProgress(documentId, step, TOTAL_STEPS, completed, startTime, null));
    }

    private void updateProgressError(Long documentId, String errorMessage, Instant startTime) {
        var current = progressMap.get(documentId);
        var step = current != null ? current.currentStep() : PipelineStep.IMPORT;
        var completed = current != null ? current.completedSteps() : 0;
        progressMap.put(documentId, new PipelineProgress(documentId, step, TOTAL_STEPS, completed, startTime, errorMessage));
    }

    private List<Document> buildDocuments(List<DocumentChunk> chunks, List<float[]> embeddings,
                                          Long knowledgeBaseId, Long documentId) {
        return java.util.stream.IntStream.range(0, chunks.size())
                .mapToObj(i -> {
                    var chunk = chunks.get(i);
                    var metadata = new java.util.HashMap<>(chunk.metadata());
                    metadata.put("chunkIndex", chunk.index());
                    metadata.put("knowledgeBaseId", knowledgeBaseId);
                    metadata.put("documentId", documentId);
                    return new Document(chunk.content(), metadata);
                })
                .toList();
    }

    private void updateDocumentStatus(Long documentId, int status, int chunkCount, String errorMessage) {
        entityManager.createNativeQuery(
                        "UPDATE knowledge_document SET status = :status, chunk_count = :chunkCount, error_message = :errorMessage WHERE id = :id")
                .setParameter("status", status)
                .setParameter("chunkCount", chunkCount)
                .setParameter("errorMessage", errorMessage)
                .setParameter("id", documentId)
                .executeUpdate();
    }
}
