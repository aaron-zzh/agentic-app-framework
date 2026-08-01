package com.xuejiai.aaf.module.knowledge.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.engine.knowledge.graph.EntityResolutionService;
import com.xuejiai.aaf.framework.org.OrgContext;
import com.xuejiai.aaf.framework.security.PermissionExecutionService;
import com.xuejiai.aaf.framework.task.queue.AsyncTaskMessage;
import com.xuejiai.aaf.framework.task.queue.TaskHandler;
import com.xuejiai.aaf.framework.task.queue.TaskQueue;

import lombok.RequiredArgsConstructor;

/**
 * 知识库实体消歧异步任务。文档处理管道完成后触发一次，对该知识库的实体做批量去重合并。
 *
 * <p>用同一知识库内所有实体重新扫描，天然幂等——重复触发不会产生错误结果，最多是重复计算， 因此不做"同知识库并发去重"的互斥控制，与 {@code
 * KnowledgeDocumentQueueService} 按文档级租约控制不同。
 */
@Service
@RequiredArgsConstructor
public class KnowledgeEntityResolutionQueueService implements TaskHandler {

    public static final String TASK_TYPE = "KNOWLEDGE_ENTITY_RESOLUTION";
    private static final String TASK_ID_PREFIX = "knowledge-entity-resolution:";

    private final TaskQueue taskQueue;
    private final EntityResolutionService entityResolutionService;
    private final PermissionExecutionService permissionExecutionService;

    /** 文档处理完成后调用，对其所属知识库触发一次实体消歧扫描。 */
    public void enqueue(Long knowledgeBaseId, Long ownerId, Long orgId, Long workspaceId) {
        var payload = new ResolveEntityPayload(knowledgeBaseId, ownerId, orgId, workspaceId);
        var task =
                new AsyncTaskMessage(
                        taskId(knowledgeBaseId),
                        TASK_TYPE,
                        JsonUtils.toJsonString(payload),
                        7,
                        3,
                        0,
                        LocalDateTime.now(),
                        null);
        taskQueue.enqueue(task);
    }

    static String taskId(Long knowledgeBaseId) {
        return TASK_ID_PREFIX + knowledgeBaseId;
    }

    @Override
    public String taskType() {
        return TASK_TYPE;
    }

    @Override
    public void handle(String taskId, String payloadJson) {
        var payload = JsonUtils.parseObject(payloadJson, ResolveEntityPayload.class);
        permissionExecutionService.runAsOwner(
                payload.ownerId(),
                "knowledge-entity-resolution",
                () ->
                        runInOrgContext(
                                payload,
                                () -> entityResolutionService.resolve(payload.knowledgeBaseId())));
    }

    private void runInOrgContext(ResolveEntityPayload payload, Runnable action) {
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

    record ResolveEntityPayload(Long knowledgeBaseId, Long ownerId, Long orgId, Long workspaceId) {}
}
