package com.xuejiai.aaf.module.knowledge.service;

import static com.xuejiai.aaf.common.exception.ExceptionUtil.exception;

import java.time.LocalDateTime;
import java.util.Objects;

import org.springframework.stereotype.Service;

import com.xuejiai.aaf.common.enums.knowledge.DocumentStatusEnum;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.engine.knowledge.pipeline.KnowledgePipelineService;
import com.xuejiai.aaf.framework.org.OrgContext;
import com.xuejiai.aaf.framework.security.PermissionExecutionService;
import com.xuejiai.aaf.framework.storage.StorageService;
import com.xuejiai.aaf.framework.task.queue.AsyncTaskMessage;
import com.xuejiai.aaf.framework.task.queue.TaskHandler;
import com.xuejiai.aaf.framework.task.queue.TaskQueue;
import com.xuejiai.aaf.module.knowledge.domain.KnowledgeDocument;
import com.xuejiai.aaf.module.knowledge.repository.KnowledgeDocumentRepository;

import lombok.RequiredArgsConstructor;

/** 知识库文档异步处理任务。 */
@Service
@RequiredArgsConstructor
public class KnowledgeDocumentQueueService implements TaskHandler {

    public static final String TASK_TYPE = "KNOWLEDGE_DOCUMENT_PROCESS";
    private static final String TASK_ID_PREFIX = "knowledge-document:";

    private final TaskQueue taskQueue;
    private final KnowledgeDocumentRepository documentRepository;
    private final StorageService storageService;
    private final KnowledgePipelineService pipelineService;
    private final PermissionExecutionService permissionExecutionService;
    private final KnowledgeDocumentExecutionLeaseService executionLeaseService;

    public String enqueue(KnowledgeDocument document) {
        var payload =
                new ProcessDocumentPayload(
                        document.getId(),
                        document.getOwnerId(),
                        document.getOrgId(),
                        document.getWorkspaceId());
        // 当前文档只存在一个处理代际；若未来支持原地重建，任务 ID 需加入 generation。
        var task =
                new AsyncTaskMessage(
                        taskId(document.getId()),
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

    static String taskId(Long documentId) {
        return TASK_ID_PREFIX + documentId;
    }

    @Override
    public String taskType() {
        return TASK_TYPE;
    }

    @Override
    public String handle(String taskId, String payloadJson) {
        var payload = JsonUtils.parseObject(payloadJson, ProcessDocumentPayload.class);
        requireValid(payload);
        if (!taskId(payload.documentId()).equals(taskId)) {
            throw exception(GlobalErrorCode.BAD_REQUEST);
        }
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
        return null;
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
        try (var input = storageService.download(document.getFilePath())) {
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
        if (!Objects.equals(document.getOwnerId(), payload.ownerId())
                || !Objects.equals(document.getOrgId(), payload.orgId())
                || !Objects.equals(document.getWorkspaceId(), payload.workspaceId())) {
            throw exception(GlobalErrorCode.FORBIDDEN);
        }
    }

    private void requireValid(ProcessDocumentPayload payload) {
        if (payload == null
                || payload.documentId() == null
                || payload.documentId() <= 0
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

    record ProcessDocumentPayload(Long documentId, Long ownerId, Long orgId, Long workspaceId) {}
}
