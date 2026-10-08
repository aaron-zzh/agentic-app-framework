package com.xuejiai.aaf.module.ai.aigc.project.service;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.framework.org.OrgContext;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectApi;
import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcProjectSchedule;
import com.xuejiai.aaf.module.ai.aigc.project.domain.AigcProjectSchedule.ProjectScopeSnapshot;
import com.xuejiai.aaf.module.ai.aigc.project.repository.AigcProjectRepository;
import com.xuejiai.aaf.module.ai.aigc.project.repository.AigcProjectScheduleRepository;
import com.xuejiai.aaf.module.ai.assistant.port.ProgrammaticAssistantTaskPort;
import com.xuejiai.aaf.module.ai.assistant.port.ProgrammaticAssistantTaskPort.Objective;
import com.xuejiai.aaf.module.ai.assistant.port.ProgrammaticAssistantTaskPort.Purpose;
import com.xuejiai.aaf.module.ai.assistant.port.ProgrammaticAssistantTaskPort.Request;

import lombok.extern.slf4j.Slf4j;

/**
 * AIGC 项目定时任务服务。
 *
 * <p>负责定时任务的创建、取消、暂停/恢复和触发逻辑。
 *
 * <h3>触发校验</h3>
 *
 * <ul>
 *   <li>项目 lifecycle 必须允许创作变更（{@code AigcProjectLifecycle#allowsCreativeMutation()}）
 *   <li>Assistant 可执行性（定义存在、已发布、权限）
 *   <li>预算充足
 *   <li>安全策略允许
 *   <li>同项目无未终结 Task → DEFERRED
 * </ul>
 *
 * <h3>幂等性</h3>
 *
 * <p>使用 {@code claimFence} 防止分布式重复触发；使用 {@code idempotencyKey} 确保 Task 创建幂等。
 *
 * @author Kiro
 */
@Slf4j
@Service
public class AigcProjectScheduleService {

    private final AigcProjectScheduleRepository schedules;
    private final AigcProjectRepository projects;
    private final ProgrammaticAssistantTaskPort programmaticTask;
    private final AigcProjectApi projectApi;
    private final OperatorContext operatorContext;

    public AigcProjectScheduleService(
            AigcProjectScheduleRepository schedules,
            AigcProjectRepository projects,
            ProgrammaticAssistantTaskPort programmaticTask,
            AigcProjectApi projectApi,
            OperatorContext operatorContext) {
        this.schedules = Objects.requireNonNull(schedules, "schedules");
        this.projects = Objects.requireNonNull(projects, "projects");
        this.programmaticTask = Objects.requireNonNull(programmaticTask, "programmaticTask");
        this.projectApi = Objects.requireNonNull(projectApi, "projectApi");
        this.operatorContext = Objects.requireNonNull(operatorContext, "operatorContext");
    }

    /**
     * 创建定时任务。
     *
     * @param command 创建命令
     * @return 定时任务 ID
     */
    @Transactional
    public Long createSchedule(CreateScheduleCommand command) {
        Objects.requireNonNull(command, "command");

        var project =
                projects.findByIdAndDeletedFalse(command.projectId())
                        .orElseThrow(() -> new IllegalArgumentException("项目不存在"));

        if (!project.getStatus().allowsCreativeMutation()) {
            throw new IllegalStateException("只能为可生成内容状态（CREATING/EXECUTING/ADOPTING）的项目创建定时任务");
        }

        var schedule = new AigcProjectSchedule();
        schedule.setOrgId(command.orgId());
        schedule.setWorkspaceId(command.workspaceId());
        schedule.setProjectId(command.projectId());
        schedule.setOwnerId(command.ownerId());
        schedule.setExecuteAt(command.executeAt());
        schedule.setTimezone(command.timezone());
        schedule.setAssistantId(command.assistantId());
        schedule.setConversationId(command.conversationId());
        schedule.setPrompt(command.prompt());
        schedule.setProjectScope(
                new ProjectScopeSnapshot(
                        project.getId(),
                        project.getWorkspaceId(),
                        project.getGraphRevision(),
                        project.getName()));
        schedule.setStatus(AigcProjectSchedule.Status.PENDING);

        var saved = schedules.save(schedule);
        log.info(
                "[ProjectSchedule] 创建定时任务 scheduleId={}, projectId={}, executeAt={}",
                saved.getId(),
                command.projectId(),
                command.executeAt());
        return saved.getId();
    }

    /**
     * 取消定时任务。
     *
     * @param scheduleId 定时任务 ID
     */
    @Transactional
    public void cancelSchedule(Long scheduleId) {
        var schedule =
                schedules
                        .findByIdAndDeletedFalse(scheduleId)
                        .orElseThrow(() -> new IllegalArgumentException("定时任务不存在"));

        if (schedule.isTerminal()) {
            log.warn(
                    "[ProjectSchedule] 定时任务已终态 scheduleId={}, status={}",
                    scheduleId,
                    schedule.getStatus());
            return;
        }

        schedule.setStatus(AigcProjectSchedule.Status.CANCELLED);
        schedules.save(schedule);
        log.info("[ProjectSchedule] 取消定时任务 scheduleId={}", scheduleId);
    }

    /**
     * 暂停定时任务（设置 nextAttemptAt 为 null）。
     *
     * @param scheduleId 定时任务 ID
     */
    @Transactional
    public void pauseSchedule(Long scheduleId) {
        var schedule =
                schedules
                        .findByIdAndDeletedFalse(scheduleId)
                        .orElseThrow(() -> new IllegalArgumentException("定时任务不存在"));

        if (schedule.getStatus() != AigcProjectSchedule.Status.PENDING
                && schedule.getStatus() != AigcProjectSchedule.Status.DEFERRED) {
            throw new IllegalStateException("只能暂停 PENDING 或 DEFERRED 状态的任务");
        }

        schedule.setNextAttemptAt(null);
        schedules.save(schedule);
        log.info("[ProjectSchedule] 暂停定时任务 scheduleId={}", scheduleId);
    }

    /**
     * 恢复定时任务（重新设置 nextAttemptAt）。
     *
     * @param scheduleId 定时任务 ID
     */
    @Transactional
    public void resumeSchedule(Long scheduleId) {
        var schedule =
                schedules
                        .findByIdAndDeletedFalse(scheduleId)
                        .orElseThrow(() -> new IllegalArgumentException("定时任务不存在"));

        if (schedule.getStatus() != AigcProjectSchedule.Status.PENDING
                && schedule.getStatus() != AigcProjectSchedule.Status.DEFERRED) {
            throw new IllegalStateException("只能恢复 PENDING 或 DEFERRED 状态的任务");
        }

        schedule.setNextAttemptAt(Instant.now());
        schedules.save(schedule);
        log.info("[ProjectSchedule] 恢复定时任务 scheduleId={}", scheduleId);
    }

    /**
     * 查询项目的所有定时任务。
     *
     * @param projectId 项目 ID
     * @return 定时任务列表
     */
    @Transactional(readOnly = true)
    public List<AigcProjectSchedule> listByProject(Long projectId) {
        return schedules.findByProjectIdAndDeletedFalseOrderByExecuteAtAsc(projectId);
    }

    /**
     * 扫描并触发到期任务（由统一 scheduler 定时调用）。
     *
     * <p>使用 {@code FOR UPDATE SKIP LOCKED} 原子认领，避免分布式重复触发。
     *
     * @param batchSize 批次大小
     */
    @Transactional
    public void scanAndTrigger(int batchSize) {
        var now = Instant.now();
        var dueSchedules = schedules.claimDueSchedules(now, batchSize);

        for (var schedule : dueSchedules) {
            try {
                triggerOne(schedule);
            } catch (Exception e) {
                log.error(
                        "[ProjectSchedule] 触发定时任务失败 scheduleId={}, error={}",
                        schedule.getId(),
                        e.getMessage(),
                        e);
                handleTriggerFailure(schedule, e);
            }
        }
    }

    private void triggerOne(AigcProjectSchedule schedule) {
        log.info(
                "[ProjectSchedule] 触发定时任务 scheduleId={}, projectId={}",
                schedule.getId(),
                schedule.getProjectId());

        // 1. 设置认领围栏
        var fence = UUID.randomUUID().toString();
        schedule.setClaimFence(fence);
        schedule.setStatus(AigcProjectSchedule.Status.DISPATCHING);
        schedules.save(schedule);

        // 2. 重新校验项目状态
        var project =
                projects.findByIdAndDeletedFalse(schedule.getProjectId())
                        .orElseThrow(() -> new IllegalStateException("项目不存在"));

        if (!project.getStatus().allowsCreativeMutation()) {
            throw new IllegalStateException("项目当前状态不允许生成内容");
        }

        // 3. 检查同项目是否有未终结 Task
        var activeTaskCount = schedules.countActiveTasksByProject(schedule.getProjectId());
        if (activeTaskCount > 0) {
            log.info(
                    "[ProjectSchedule] 项目存在活跃 Task，转 DEFERRED scheduleId={}, projectId={}",
                    schedule.getId(),
                    schedule.getProjectId());
            schedule.setStatus(AigcProjectSchedule.Status.DEFERRED);
            schedule.setNextAttemptAt(Instant.now().plusSeconds(300)); // 5 分钟后重试
            schedules.save(schedule);
            return;
        }

        // 4. 创建 canonical Task
        var idempotencyKey = "schedule:" + schedule.getId() + ":" + fence;
        var objective =
                new Objective(
                        "定时任务：" + schedule.getPrompt(),
                        schedule.getPrompt(),
                        "schedule:" + schedule.getId(),
                        Purpose.PROJECT_GENERATION.roleKey(),
                        Purpose.PROJECT_GENERATION.skillKey(),
                        List.of());

        var request =
                new Request(
                        schedule.getAssistantId(),
                        schedule.getConversationId(),
                        idempotencyKey,
                        Purpose.PROJECT_GENERATION,
                        schedule.getProjectId(),
                        objective);

        // 临时设置组织上下文（触发时没有用户请求）
        OrgContext.setCurrentOrgId(schedule.getOrgId());
        OrgContext.setCurrentWorkspaceId(schedule.getWorkspaceId());
        try {
            var receipt = programmaticTask.submitTask(request);
            schedule.setLinkedTaskId(receipt.taskId());
            schedule.setStatus(AigcProjectSchedule.Status.TRIGGERED);
            schedules.save(schedule);
            log.info(
                    "[ProjectSchedule] 定时任务触发成功 scheduleId={}, taskId={}",
                    schedule.getId(),
                    receipt.taskId());
        } finally {
            OrgContext.clear();
        }
    }

    private void handleTriggerFailure(AigcProjectSchedule schedule, Exception error) {
        schedule.setLastError(error.getMessage());
        schedule.setFailCount(schedule.getFailCount() + 1);

        if (schedule.getFailCount() >= 3) {
            schedule.setStatus(AigcProjectSchedule.Status.FAILED);
            log.error("[ProjectSchedule] 定时任务失败次数过多，标记为 FAILED scheduleId={}", schedule.getId());
        } else {
            schedule.setStatus(AigcProjectSchedule.Status.DEFERRED);
            schedule.setNextAttemptAt(Instant.now().plusSeconds(300)); // 5 分钟后重试
            log.info("[ProjectSchedule] 定时任务触发失败，转 DEFERRED scheduleId={}", schedule.getId());
        }

        schedules.save(schedule);
    }

    /** 创建定时任务命令 */
    public record CreateScheduleCommand(
            Long orgId,
            Long workspaceId,
            Long projectId,
            Long ownerId,
            Instant executeAt,
            String timezone,
            String assistantId,
            String conversationId,
            String prompt) {

        public CreateScheduleCommand {
            Objects.requireNonNull(orgId, "orgId");
            Objects.requireNonNull(workspaceId, "workspaceId");
            Objects.requireNonNull(projectId, "projectId");
            Objects.requireNonNull(ownerId, "ownerId");
            Objects.requireNonNull(executeAt, "executeAt");
            Objects.requireNonNull(timezone, "timezone");
            Objects.requireNonNull(assistantId, "assistantId");
            Objects.requireNonNull(conversationId, "conversationId");
            Objects.requireNonNull(prompt, "prompt");

            // 校验时区有效性
            try {
                ZoneId.of(timezone);
            } catch (Exception e) {
                throw new IllegalArgumentException("无效的时区：" + timezone);
            }
        }
    }
}
