package com.xuejiai.aaf.module.knowledge.service;

import static com.xuejiai.aaf.common.exception.ExceptionUtil.exception;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.engine.knowledge.pipeline.KnowledgePipelineService;
import com.xuejiai.aaf.framework.storage.FileService;
import com.xuejiai.aaf.framework.task.queue.AsyncTaskMessage;
import com.xuejiai.aaf.framework.task.queue.TaskHandler;
import com.xuejiai.aaf.framework.task.queue.TaskQueue;
import com.xuejiai.aaf.module.knowledge.domain.KnowledgeDocument;

import lombok.RequiredArgsConstructor;

/** 知识库文档删除后的外部资源异步清理任务。 */
@Service
@RequiredArgsConstructor
public class KnowledgeDocumentCleanupQueueService implements TaskHandler {

    public static final String TASK_TYPE = "KNOWLEDGE_DOCUMENT_CLEANUP";
    private static final String TASK_ID_PREFIX = "knowledge-document-cleanup:";

    private final TaskQueue taskQueue;
    private final KnowledgePipelineService pipelineService;
    private final FileService fileService;

    public String enqueue(KnowledgeDocument document) {
        var payload =
                new CleanupDocumentPayload(
                        document.getKnowledgeBaseId(), document.getId(), document.getFilePath());
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
    public void handle(String taskId, String payloadJson) {
        var payload = JsonUtils.parseObject(payloadJson, CleanupDocumentPayload.class);
        requireValid(payload);
        if (!taskId(payload.documentId()).equals(taskId)) {
            throw exception(GlobalErrorCode.BAD_REQUEST);
        }
        pipelineService.clearDocumentGraphData(payload.knowledgeBaseId(), payload.documentId());
        if (payload.filePath() != null && !payload.filePath().isBlank()) {
            fileService.delete(payload.filePath());
        }
    }

    private void requireValid(CleanupDocumentPayload payload) {
        if (payload == null
                || payload.knowledgeBaseId() == null
                || payload.knowledgeBaseId() <= 0
                || payload.documentId() == null
                || payload.documentId() <= 0) {
            throw exception(GlobalErrorCode.BAD_REQUEST);
        }
    }

    record CleanupDocumentPayload(Long knowledgeBaseId, Long documentId, String filePath) {}
}
