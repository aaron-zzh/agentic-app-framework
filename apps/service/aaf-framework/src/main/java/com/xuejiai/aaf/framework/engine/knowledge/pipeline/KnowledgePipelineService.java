package com.xuejiai.aaf.framework.engine.knowledge.pipeline;

import java.io.InputStream;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.xuejiai.aaf.common.enums.knowledge.DocumentStatusEnum;
import com.xuejiai.aaf.framework.engine.knowledge.KnowledgeVectorService;
import com.xuejiai.aaf.framework.engine.knowledge.chunker.AutoChunkStrategySelector;
import com.xuejiai.aaf.framework.engine.knowledge.chunker.SectionAwareChunker;
import com.xuejiai.aaf.framework.engine.knowledge.chunker.SectionOutlineBuilder;
import com.xuejiai.aaf.framework.engine.knowledge.embedding.EmbeddingProperties;
import com.xuejiai.aaf.framework.engine.knowledge.graph.EntityExtractionPrompt;
import com.xuejiai.aaf.framework.engine.knowledge.graph.EntityExtractionService;
import com.xuejiai.aaf.framework.engine.knowledge.graph.EntityResolutionPrompt;
import com.xuejiai.aaf.framework.engine.knowledge.importer.ImporterFactory;
import com.xuejiai.aaf.framework.engine.knowledge.summary.KnowledgeDocumentSummaryService;
import com.xuejiai.aaf.framework.engine.knowledge.trusted.FocusExtractionContextAssembler;
import com.xuejiai.aaf.framework.engine.knowledge.trusted.KnowledgeIngestConfigurationService;
import com.xuejiai.aaf.framework.engine.knowledge.trusted.TrustedKnowledgeStore;
import com.xuejiai.aaf.framework.engine.knowledge.trusted.TrustedKnowledgeStore.RunContext;
import com.xuejiai.aaf.framework.engine.knowledge.trusted.TrustedKnowledgeStore.StoredChunk;
import com.xuejiai.aaf.framework.engine.meta.runtime.TaskExecutionInProgressException;
import com.xuejiai.aaf.framework.org.OrgContext;
import com.xuejiai.aaf.framework.security.PermissionExecutionContextHolder;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** NexusKB 代际入库管道：新代际成功前不改变线上当前代际。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgePipelineService {

    /** v3：分块前规范化空白并合并 PDF 折行；v2：先按章节大纲分块，块携带章节路径与页码，向量化输入拼接章节路径。 */
    private static final String PARSER_VERSION = "v3";

    private static final String NORMALIZATION_VERSION = "v1";
    private static final int EXTRACTION_CONTEXT_BUDGET = 12_000;
    private static final int MAX_ERROR_LENGTH = 2_000;

    private final ImporterFactory importerFactory;
    private final SectionOutlineBuilder outlineBuilder;
    private final SectionAwareChunker sectionChunker;
    private final AutoChunkStrategySelector strategySelector;
    private final TrustedKnowledgeStore truthStore;
    private final KnowledgeVectorService vectorService;
    private final FocusExtractionContextAssembler contextAssembler;
    private final KnowledgeIngestConfigurationService configurationService;
    private final EntityExtractionService extractionService;
    private final KnowledgeDocumentStatusPort documentStatusPort;
    private final KnowledgeSourceVersionPort sourceVersionPort;
    private final EmbeddingProperties embeddingProperties;
    private final KnowledgeDocumentSummaryService documentSummaryService;

    private final ConcurrentHashMap<Long, PipelineProgress> progressMap = new ConcurrentHashMap<>();

    /** 单个文档同时抽取事实的块数；受模型供应商限频约束，限频时由计量器自动延时重试。 */
    @Value("${aaf.knowledge.extraction-concurrency:4}")
    private int extractionConcurrency;

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
            var sourceVersion =
                    sourceVersionPort.freeze(documentId, filename, fullText, contentHash);

            updateProgress(documentId, PipelineStep.CHUNK, 1, startTime);
            var chunkConfig = strategySelector.selectStrategy(knowledgeBaseId);
            var chunkDigest = TrustedKnowledgeStore.sha256(chunkConfig.toString());
            var configuration = configurationService.resolve();
            var fingerprint =
                    TrustedKnowledgeStore.sha256(
                            String.join(
                                    "|",
                                    contentHash,
                                    sourceVersion.checksum(),
                                    PARSER_VERSION,
                                    chunkDigest,
                                    configuration.extractionPromptPairDigest(),
                                    configuration.extractionOutputContractVersion(),
                                    TrustedKnowledgeStore.sha256(
                                            EntityExtractionPrompt.OUTPUT_INSTRUCTIONS),
                                    configuration.extractionModelId(),
                                    configuration.entityResolutionPromptPairDigest(),
                                    configuration.entityResolutionOutputContractVersion(),
                                    EntityResolutionPrompt.STRUCTURED_OUTPUT_FORMAT_VERSION,
                                    TrustedKnowledgeStore.sha256(
                                            EntityResolutionPrompt.OUTPUT_INSTRUCTIONS),
                                    configuration.entityResolutionModelId(),
                                    NORMALIZATION_VERSION,
                                    embeddingProperties.model()));
            run =
                    truthStore.beginRun(
                            knowledgeBaseId,
                            documentId,
                            sourceVersion.documentVersionId(),
                            contentHash,
                            fingerprint,
                            PARSER_VERSION,
                            chunkDigest,
                            configuration,
                            embeddingProperties.model());
            if ("READY".equals(run.status()) || "PUBLISHED".equals(run.status())) {
                var receipt = truthStore.publish(run);
                documentSummaryService.summarize(run, true);
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
                    sectionChunker.chunk(
                            outlineBuilder.build(importResult.sections()),
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
            var chunkFacts = extractFacts(run, storedChunks, documentId, executionGuard);
            executionGuard.run();
            try {
                extractionService.persist(run, chunkFacts, executionGuard);
            } catch (TaskExecutionInProgressException inProgress) {
                throw inProgress;
            } catch (Exception persistFailure) {
                log.warn(
                        "知识库实体消歧或事实写入失败，降级跳过图谱事实，documentId={}, runId={}, 原因={}",
                        documentId,
                        run.runId(),
                        persistFailure.getMessage());
            }
            executionGuard.run();

            updateProgress(documentId, PipelineStep.PUBLISH, 5, startTime);
            truthStore.markReady(run);
            var receipt = truthStore.publish(run);
            documentSummaryService.summarize(run, false);
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
            if (failure
                    instanceof
                    com.xuejiai.aaf.framework.engine.meta.runtime.NonRetryableTaskException
                            nonRetryable) {
                throw nonRetryable;
            }
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

    /**
     * 按块并行抽取事实，同时进行的块数不超过 {@code extractionConcurrency}；结果按块顺序返回，单块失败降级跳过。
     *
     * <p>每块在独立虚拟线程执行并沿用当前组织与权限执行上下文。执行租约失效时不再启动新块，等进行中的块结束后抛出，不中断进行中的模型调用，避免其被记为结果未知。
     */
    private List<EntityExtractionService.ChunkFacts> extractFacts(
            RunContext run,
            List<StoredChunk> storedChunks,
            Long documentId,
            Runnable executionGuard) {
        var context = InheritedContext.capture();
        var slots = new EntityExtractionService.ChunkFacts[storedChunks.size()];
        var leaseLost = new AtomicReference<TaskExecutionInProgressException>();
        var permits = new Semaphore(Math.max(1, extractionConcurrency));
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (var index = 0; index < storedChunks.size() && leaseLost.get() == null; index++) {
                executionGuard.run();
                acquire(permits);
                if (leaseLost.get() != null) {
                    permits.release();
                    break;
                }
                var chunkIndex = index;
                executor.execute(
                        () -> {
                            try {
                                context.run(
                                        () ->
                                                slots[chunkIndex] =
                                                        extractChunk(
                                                                run,
                                                                storedChunks,
                                                                chunkIndex,
                                                                documentId,
                                                                executionGuard));
                            } catch (TaskExecutionInProgressException inProgress) {
                                leaseLost.compareAndSet(null, inProgress);
                            } finally {
                                permits.release();
                            }
                        });
            }
        }
        if (leaseLost.get() != null) {
            throw leaseLost.get();
        }
        return Arrays.stream(slots).filter(Objects::nonNull).toList();
    }

    /** 抽取单块；租约失效向上抛出，其余失败降级跳过该块并返回 null。 */
    private EntityExtractionService.ChunkFacts extractChunk(
            RunContext run,
            List<StoredChunk> storedChunks,
            int index,
            Long documentId,
            Runnable executionGuard) {
        try {
            var context = contextAssembler.assemble(storedChunks, index, EXTRACTION_CONTEXT_BUDGET);
            return extractionService.extract(run, context, executionGuard);
        } catch (TaskExecutionInProgressException inProgress) {
            throw inProgress;
        } catch (Exception extractionFailure) {
            log.warn(
                    "知识库实体抽取失败，降级跳过该块（图投影可待 Neo4j 恢复后由管理员重建），documentId={}, runId={}, chunkIndex={}, 原因={}",
                    documentId,
                    run.runId(),
                    index,
                    extractionFailure.getMessage());
            return null;
        }
    }

    private void acquire(Semaphore permits) {
        try {
            permits.acquire();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("知识库事实抽取被中断", interrupted);
        }
    }

    /** 抽取子线程需要沿用的组织与权限执行上下文。 */
    private record InheritedContext(
            Long orgId,
            Long workspaceId,
            boolean ignoreOrg,
            PermissionExecutionContextHolder.PermissionExecutionContext permission) {

        static InheritedContext capture() {
            return new InheritedContext(
                    OrgContext.getCurrentOrgId(),
                    OrgContext.getCurrentWorkspaceId(),
                    OrgContext.isIgnore(),
                    PermissionExecutionContextHolder.get());
        }

        void run(Runnable action) {
            OrgContext.setCurrentOrgId(orgId);
            OrgContext.setCurrentWorkspaceId(workspaceId);
            OrgContext.setIgnore(ignoreOrg);
            try (var ignored =
                    permission == null
                            ? null
                            : PermissionExecutionContextHolder.useOwner(
                                    permission.ownerId(), permission.reason())) {
                action.run();
            } finally {
                OrgContext.clear();
            }
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

    private String truncate(String message) {
        if (message == null || message.length() <= MAX_ERROR_LENGTH) {
            return message;
        }
        return message.substring(0, MAX_ERROR_LENGTH);
    }
}
