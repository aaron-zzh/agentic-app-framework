package com.xuejiai.aaf.module.knowledge.service;

import static com.xuejiai.aaf.common.exception.ExceptionUtil.exception;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.xuejiai.aaf.common.enums.knowledge.DocumentStatusEnum;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.engine.knowledge.pipeline.KnowledgePipelineService;
import com.xuejiai.aaf.framework.engine.meta.runtime.TaskExecutionInProgressException;
import com.xuejiai.aaf.framework.messaging.event.SysEventPublisher;
import com.xuejiai.aaf.framework.org.OrgContext;
import com.xuejiai.aaf.framework.security.PermissionExecutionService;
import com.xuejiai.aaf.framework.task.queue.AsyncTaskMessage;
import com.xuejiai.aaf.framework.task.queue.TaskHandler;
import com.xuejiai.aaf.framework.task.queue.TaskQueue;
import com.xuejiai.aaf.module.knowledge.domain.KnowledgeDocument;
import com.xuejiai.aaf.module.knowledge.repository.KnowledgeDocumentRepository;
import com.xuejiai.aaf.module.system.file.api.FileStoragePort;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 知识库文档异步处理任务；处理开始、完成与失败时向上传人推送全局事件。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeDocumentQueueService implements TaskHandler {

    public static final String TASK_TYPE = "KNOWLEDGE_DOCUMENT_PROCESS";
    private static final String TASK_ID_PREFIX = "knowledge-document:";
    private static final String EVENT_TYPE_PREFIX = "knowledge.document.";

    private final TaskQueue taskQueue;
    private final KnowledgeDocumentRepository documentRepository;
    private final FileStoragePort fileStoragePort;
    private final KnowledgePipelineService pipelineService;
    private final PermissionExecutionService permissionExecutionService;
    private final KnowledgeDocumentExecutionLeaseService executionLeaseService;
    private final SysEventPublisher eventPublisher;

    public String enqueue(KnowledgeDocument document) {
        var payload =
                new ProcessDocumentPayload(
                        document.getId(),
                        document.getStableId(),
                        document.getOwnerId(),
                        document.getOrgId(),
                        document.getWorkspaceId());
        // 当前文档只存在一个处理代际；若未来支持原地重建，任务 ID 需加入 generation。
        var task =
                new AsyncTaskMessage(
                        taskId(document.getId(), document.getStableId()),
                        TASK_TYPE,
                        JsonUtils.toJsonString(payload),
                        5,
                        3,
                        0,
                        LocalDateTime.now(),
                        null);
        taskQueue.enqueue(task);
        return task.id();
    }

    /** 任务 ID 含文档 stableId，避免数据库重置后主键复用命中旧任务的去重标记。 */
    static String taskId(Long documentId, UUID stableId) {
        return TASK_ID_PREFIX + documentId + ":" + stableId;
    }

    @Override
    public String taskType() {
        return TASK_TYPE;
    }

    @Override
    public String handle(String taskId, String payloadJson) {
        var payload = JsonUtils.parseObject(payloadJson, ProcessDocumentPayload.class);
        requireValid(payload);
        if (!taskId(payload.documentId(), payload.stableId()).equals(taskId)) {
            throw exception(GlobalErrorCode.BAD_REQUEST);
        }
        var document =
                OrgContext.runIgnoring(() -> documentRepository.findById(payload.documentId()))
                        .orElse(null);
        notifyUploader(document, "processing");
        try {
            permissionExecutionService.runAsOwner(
                    payload.ownerId(),
                    "knowledge-document-process",
                    () ->
                            runInOrgContext(
                                    payload,
                                    () ->
                                            executionLeaseService.execute(
                                                    payload.documentId(),
                                                    guard -> process(payload, guard))));
        } catch (TaskExecutionInProgressException inProgress) {
            throw inProgress;
        } catch (RuntimeException failure) {
            notifyUploader(document, "failed");
            throw failure;
        }
        notifyUploader(document, "completed");
        return null;
    }

    /**
     * 向上传人推送用户级文档处理事件 {@code knowledge.document.{action}}，前端据此刷新列表；仅作变化通知，不含业务数据。
     *
     * <p>在组织上下文之外发布，事件不绑定组织与工作区，上传人在任意范围的连接都能收到。
     */
    private void notifyUploader(KnowledgeDocument document, String action) {
        if (document == null || document.getUploadedBy() == null) {
            return;
        }
        var refs = new LinkedHashMap<String, Long>();
        refs.put("knowledgeBaseId", document.getKnowledgeBaseId());
        refs.put("documentId", document.getId());
        try {
            eventPublisher.publish(
                    document.getUploadedBy(),
                    EVENT_TYPE_PREFIX + action,
                    refs,
                    Map.of("action", action));
        } catch (RuntimeException failure) {
            log.warn("知识文档处理事件发布失败，documentId={}, action={}", document.getId(), action, failure);
        }
    }

    private void process(ProcessDocumentPayload payload, Runnable executionGuard) {
        var document = documentRepository.findById(payload.documentId()).orElse(null);
        if (document == null) {
            return;
        }
        requireScope(document, payload);
        executionGuard.run();
        if (DocumentStatusEnum.COMPLETED.getCode().equals(document.getStatus())) {
            return;
        }
        if (document.getFilePath() == null || document.getFilePath().isBlank()) {
            throw new IllegalStateException("知识库文档缺少存储文件");
        }
        try (var input = fileStoragePort.openByKey(document.getFilePath())) {
            var result =
                    pipelineService.process(
                            document.getKnowledgeBaseId(),
                            document.getId(),
                            input,
                            document.getTitle(),
                            executionGuard);
            if (!result.success()) {
                throw new IllegalStateException("知识库文档处理失败: " + result.errorMessage());
            }
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("关闭知识库文档输入流失败", failure);
        }
    }

    private void requireScope(KnowledgeDocument document, ProcessDocumentPayload payload) {
        if (!Objects.equals(document.getStableId(), payload.stableId())
                || !Objects.equals(document.getOwnerId(), payload.ownerId())
                || !Objects.equals(document.getOrgId(), payload.orgId())
                || !Objects.equals(document.getWorkspaceId(), payload.workspaceId())) {
            throw exception(GlobalErrorCode.FORBIDDEN);
        }
    }

    private void requireValid(ProcessDocumentPayload payload) {
        if (payload == null
                || payload.documentId() == null
                || payload.documentId() <= 0
                || payload.stableId() == null
                || payload.ownerId() == null
                || payload.ownerId() <= 0) {
            throw exception(GlobalErrorCode.BAD_REQUEST);
        }
    }

    private void runInOrgContext(ProcessDocumentPayload payload, Runnable action) {
        var previousOrgId = OrgContext.getCurrentOrgId();
        var previousWorkspaceId = OrgContext.getCurrentWorkspaceId();
        OrgContext.setCurrentOrgId(payload.orgId());
        OrgContext.setCurrentWorkspaceId(payload.workspaceId());
        try {
            action.run();
        } finally {
            OrgContext.setCurrentOrgId(previousOrgId);
            OrgContext.setCurrentWorkspaceId(previousWorkspaceId);
        }
    }

    record ProcessDocumentPayload(
            Long documentId, UUID stableId, Long ownerId, Long orgId, Long workspaceId) {}
}
