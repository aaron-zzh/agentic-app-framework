package com.xuejiai.aaf.framework.engine.knowledge.pipeline;

import java.io.InputStream;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import com.xuejiai.aaf.common.enums.knowledge.DocumentStatusEnum;
import com.xuejiai.aaf.framework.engine.knowledge.KnowledgeVectorService;
import com.xuejiai.aaf.framework.engine.knowledge.chunker.AutoChunkStrategySelector;
import com.xuejiai.aaf.framework.engine.knowledge.chunker.ChunkerFactory;
import com.xuejiai.aaf.framework.engine.knowledge.embedding.EmbeddingProperties;
import com.xuejiai.aaf.framework.engine.knowledge.graph.EntityExtractionService;
import com.xuejiai.aaf.framework.engine.knowledge.importer.ImporterFactory;
import com.xuejiai.aaf.framework.engine.knowledge.trusted.FocusExtractionContextAssembler;
import com.xuejiai.aaf.framework.engine.knowledge.trusted.KnowledgeIngestConfigurationService;
import com.xuejiai.aaf.framework.engine.knowledge.trusted.TrustedKnowledgeStore;
import com.xuejiai.aaf.framework.engine.knowledge.trusted.TrustedKnowledgeStore.RunContext;
import com.xuejiai.aaf.framework.engine.meta.runtime.TaskExecutionInProgressException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** NexusKB 代际入库管道：新代际成功前不改变线上当前代际。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgePipelineService {

    private static final String PARSER_VERSION = "v1";
    private static final String NORMALIZATION_VERSION = "v1";
    private static final int EXTRACTION_CONTEXT_BUDGET = 12_000;
    private static final int MAX_ERROR_LENGTH = 2_000;

    private final ImporterFactory importerFactory;
    private final ChunkerFactory chunkerFactory;
    private final AutoChunkStrategySelector strategySelector;
    private final TrustedKnowledgeStore truthStore;
    private final KnowledgeVectorService vectorService;
    private final FocusExtractionContextAssembler contextAssembler;
    private final KnowledgeIngestConfigurationService configurationService;
    private final EntityExtractionService extractionService;
    private final KnowledgeDocumentStatusPort documentStatusPort;
    private final EmbeddingProperties embeddingProperties;

    private final ConcurrentHashMap<Long, PipelineProgress> progressMap = new ConcurrentHashMap<>();

    public PipelineResult process(
            Long knowledgeBaseId,
            Long documentId,
            InputStream input,
            String filename,
            Runnable executionGuard) {
        var startTime = Instant.now();
        RunContext run = null;
        try {
            executionGuard.run();
            updateDocumentStatus(documentId, DocumentStatusEnum.PROCESSING.getCode(), 0, null);
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
                            "\n",
                            importResult.sections().stream()
                                    .map(section -> section.content())
                                    .toList());
            var contentHash = TrustedKnowledgeStore.sha256(fullText);

            updateProgress(documentId, PipelineStep.CHUNK, 1, startTime);
            var extension = extension(filename);
            var chunkConfig = strategySelector.selectStrategy(extension, knowledgeBaseId);
            var chunkDigest = TrustedKnowledgeStore.sha256(chunkConfig.toString());
            var configuration = configurationService.resolve();
            var fingerprint =
                    TrustedKnowledgeStore.sha256(
                            String.join(
                                    "|",
                                    contentHash,
                                    PARSER_VERSION,
                                    chunkDigest,
                                    configuration.extractionPromptDigest(),
                                    configuration.extractionOutputContractVersion(),
                                    configuration.extractionModelId(),
                                    configuration.entityResolutionPromptDigest(),
                                    configuration.entityResolutionOutputContractVersion(),
                                    configuration.entityResolutionModelId(),
                                    NORMALIZATION_VERSION,
                                    embeddingProperties.model()));
            run =
                    truthStore.beginRun(
                            knowledgeBaseId,
                            documentId,
                            contentHash,
                            fingerprint,
                            PARSER_VERSION,
                            chunkDigest,
                            configuration,
                            embeddingProperties.model());
            if ("READY".equals(run.status()) || "PUBLISHED".equals(run.status())) {
                var receipt = truthStore.publish(run);
                progressMap.remove(documentId);
                return new PipelineResult(
                        true,
                        documentId,
                        receipt.chunkCount(),
                        receipt.factCount(),
                        System.currentTimeMillis() - startTime.toEpochMilli(),
                        null);
            }

            var chunks =
                    chunkerFactory
                            .getChunker(chunkConfig.strategy())
                            .chunk(
                                    fullText,
                                    chunkConfig,
                                    Map.of(
                                            "knowledge_base_id",
                                            knowledgeBaseId,
                                            "document_id",
                                            documentId,
                                            "filename",
                                            filename));
            executionGuard.run();

            updateProgress(documentId, PipelineStep.STORE, 2, startTime);
            var storedChunks = truthStore.storeChunks(run, chunks);
            executionGuard.run();

            updateProgress(documentId, PipelineStep.EMBED, 3, startTime);
            vectorService.store(run, storedChunks);
            executionGuard.run();

            updateProgress(documentId, PipelineStep.EXTRACT_FACTS, 4, startTime);
            for (var index = 0; index < storedChunks.size(); index++) {
                var context =
                        contextAssembler.assemble(storedChunks, index, EXTRACTION_CONTEXT_BUDGET);
                extractionService.extractAndPersist(run, context, executionGuard);
                executionGuard.run();
            }

            updateProgress(documentId, PipelineStep.PUBLISH, 5, startTime);
            truthStore.markReady(run);
            var receipt = truthStore.publish(run);
            progressMap.remove(documentId);
            return new PipelineResult(
                    true,
                    documentId,
                    receipt.chunkCount(),
                    receipt.factCount(),
                    System.currentTimeMillis() - startTime.toEpochMilli(),
                    null);
        } catch (TaskExecutionInProgressException inProgress) {
            throw inProgress;
        } catch (Exception failure) {
            ensureCurrentOrDefer(executionGuard, failure);
            var errorMessage = truncate(failure.getMessage());
            if (run != null) {
                truthStore.failRun(run, errorMessage);
            } else {
                updateDocumentStatus(
                        documentId, DocumentStatusEnum.FAILED.getCode(), 0, errorMessage);
            }
            updateProgressError(documentId, errorMessage, startTime);
            log.error(
                    "知识库代际入库失败，documentId={}, runId={}",
                    documentId,
                    run == null ? null : run.runId(),
                    failure);
            return new PipelineResult(
                    false,
                    documentId,
                    0,
                    0,
                    System.currentTimeMillis() - startTime.toEpochMilli(),
                    errorMessage);
        }
    }

    public void revokeDocument(Long documentId) {
        truthStore.revokeDocument(documentId);
    }

    public Optional<PipelineProgress> getProgress(Long documentId) {
        return Optional.ofNullable(progressMap.get(documentId));
    }

    private void ensureCurrentOrDefer(Runnable executionGuard, Exception failure) {
        try {
            executionGuard.run();
        } catch (TaskExecutionInProgressException inProgress) {
            inProgress.addSuppressed(failure);
            throw inProgress;
        }
    }

    private void updateProgress(
            Long documentId, PipelineStep step, int completed, Instant startTime) {
        progressMap.put(
                documentId,
                new PipelineProgress(
                        documentId,
                        step,
                        PipelineStep.values().length,
                        completed,
                        startTime,
                        null));
    }

    private void updateProgressError(Long documentId, String errorMessage, Instant startTime) {
        var current = progressMap.get(documentId);
        progressMap.put(
                documentId,
                new PipelineProgress(
                        documentId,
                        current == null ? PipelineStep.IMPORT : current.currentStep(),
                        PipelineStep.values().length,
                        current == null ? 0 : current.completedSteps(),
                        startTime,
                        errorMessage));
    }

    private void updateDocumentStatus(
            Long documentId, int status, int chunkCount, String errorMessage) {
        documentStatusPort.updateStatus(documentId, status, chunkCount, errorMessage);
    }

    private String extension(String filename) {
        var dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1);
    }

    private String truncate(String message) {
        if (message == null || message.length() <= MAX_ERROR_LENGTH) {
            return message;
        }
        return message.substring(0, MAX_ERROR_LENGTH);
    }
}
