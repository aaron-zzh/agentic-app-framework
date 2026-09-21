package com.xuejiai.aaf.module.ai.aigc.execution.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.xuejiai.aaf.framework.crud.CrudEntityRepository;
import com.xuejiai.aaf.module.ai.aigc.execution.api.AigcExecutionRunStatus;
import com.xuejiai.aaf.module.ai.aigc.execution.domain.AigcExecutionRun;

import jakarta.persistence.LockModeType;

public interface AigcExecutionRunRepository extends CrudEntityRepository<AigcExecutionRun> {

    List<AigcExecutionRun> findByProjectIdAndActionKeyAndDeletedFalseOrderByIdDesc(
            Long projectId, String actionKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select run from AigcExecutionRun run where run.id = :id")
    Optional<AigcExecutionRun> findLockedById(@Param("id") Long id);

    long countByProjectIdAndStatus(Long projectId, AigcExecutionRunStatus status);

    List<AigcExecutionRun> findByProjectIdAndStatusIn(
            Long projectId, List<AigcExecutionRunStatus> statuses);

    List<AigcExecutionRun> findByProjectIdOrderByIdAsc(Long projectId);

    Optional<AigcExecutionRun> findByExecutionSubmissionId(Long executionSubmissionId);

    Optional<AigcExecutionRun> findByExecutionSubmissionIdAndParentExecutionRunIdIsNull(
            Long executionSubmissionId);

    List<AigcExecutionRun> findByRootExecutionRunIdOrderByIdAsc(Long rootExecutionRunId);

    Optional<AigcExecutionRun>
            findFirstByParentExecutionRunIdAndWorkflowNodeKeyAndObjectIdAndRetryOfExecutionRunIdIsNull(
                    Long parentExecutionRunId, String workflowNodeKey, Long objectId);

    @Modifying
    @Query(
            value =
                    """
                    UPDATE generation_history
                    SET deleted = true, delete_time = CURRENT_TIMESTAMP
                    WHERE deleted = false
                      AND execution_run_id IN (
                          SELECT id FROM aigc_execution_run WHERE project_id = :projectId
                      )
                    """,
            nativeQuery = true)
    int softDeleteGenerationHistoryByProjectId(@Param("projectId") Long projectId);

    Optional<AigcExecutionRun> findFirstByRetryOfExecutionRunIdOrderByRetryCountDesc(
            Long retryOfExecutionRunId);

    /**
     * 按 root Run 冻结的已确认目标身份反查每个目标的最新 root Run。
     *
     * <p>只看 root Run（{@code id = root_execution_run_id}）：child 与 retry 都从 root 复制同一份冻结证据，若一并统计会重复。
     * 同一 itemKey 存在多次重试时取 id 最大的那次，即当前权威 Run。
     */
    @Query(
            value =
                    """
                    SELECT DISTINCT ON (run.effective_input -> 'approvedGenerationTarget' ->> 'itemKey')
                           run.effective_input -> 'approvedGenerationTarget' ->> 'itemKey' AS item_key,
                           run.id AS execution_run_id,
                           run.status AS status
                    FROM aigc_execution_run run
                    WHERE run.deleted = false
                      AND run.project_id = :projectId
                      AND run.id = run.root_execution_run_id
                      AND run.effective_input -> 'approvedGenerationTarget' ->> 'proposalId' = :proposalId
                      AND run.effective_input -> 'approvedGenerationTarget' ->> 'proposalVersion' = :proposalVersion
                    ORDER BY run.effective_input -> 'approvedGenerationTarget' ->> 'itemKey', run.id DESC
                    """,
            nativeQuery = true)
    List<ApprovedTargetRunRow> findApprovedTargetRuns(
            @Param("projectId") Long projectId,
            @Param("proposalId") String proposalId,
            @Param("proposalVersion") String proposalVersion);

    /**
     * 按项目反查每个生成目标的最新 root Run，跨 Proposal 版本取最新。
     *
     * <p>与 {@link #findApprovedTargetRuns} 的区别是不限定 Proposal：文档、初始化字段等实体要在自己的界面上显示生成状态，
     * 该状态必须在任务终结后依然存在，因此按 itemKey 取全项目最新的一次执行，而不是当前任务范围内的执行。
     */
    @Query(
            value =
                    """
                    SELECT DISTINCT ON (run.effective_input -> 'approvedGenerationTarget' ->> 'itemKey')
                           run.effective_input -> 'approvedGenerationTarget' ->> 'itemKey' AS item_key,
                           run.id AS execution_run_id,
                           run.status AS status
                    FROM aigc_execution_run run
                    WHERE run.deleted = false
                      AND run.project_id = :projectId
                      AND run.id = run.root_execution_run_id
                      AND run.effective_input -> 'approvedGenerationTarget' ->> 'itemKey' IS NOT NULL
                    ORDER BY run.effective_input -> 'approvedGenerationTarget' ->> 'itemKey', run.id DESC
                    """,
            nativeQuery = true)
    List<ApprovedTargetRunRow> findLatestTargetRuns(@Param("projectId") Long projectId);

    /** 原生投影行；{@code proposalVersion} 以文本比较，避免 jsonb 到整型的隐式转换。 */
    interface ApprovedTargetRunRow {
        String getItemKey();

        Long getExecutionRunId();

        String getStatus();
    }
}
