package com.xuejiai.aaf.module.user.growth.service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.enums.pay.CreditTransactionSourceEnum;
import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.framework.engine.credit.CreditService;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.module.system.notify.service.NotificationService;
import com.xuejiai.aaf.module.user.growth.domain.UserGrowthProgress;
import com.xuejiai.aaf.module.user.growth.domain.UserGrowthTask;
import com.xuejiai.aaf.module.user.growth.repository.UserGrowthProgressRepository;
import com.xuejiai.aaf.module.user.growth.repository.UserGrowthTaskRepository;
import com.xuejiai.aaf.module.user.growth.vo.UserGrowthTaskVO;

import lombok.RequiredArgsConstructor;

/** 用户成长任务服务（v0.2.1 P3）。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserGrowthService {

    private final UserGrowthTaskRepository taskRepository;
    private final UserGrowthProgressRepository progressRepository;
    private final OperatorContext operatorContext;
    private final CreditService creditService;
    private final NotificationService notificationService;

    /** 列出当前用户所有任务（合并任务定义 + 用户进度）。 */
    public List<UserGrowthTaskVO> listMyTasks() {
        Long userId = operatorContext.currentUserId().orElseThrow();
        var tasks = taskRepository.findByEnabledAndDeletedFalseOrderBySortOrderAsc(true);
        var progressMap = buildProgressMap(userId);
        return tasks.stream().map(t -> toVO(t, progressMap.get(t.getId()))).toList();
    }

    private Map<Long, UserGrowthProgress> buildProgressMap(Long userId) {
        var list = progressRepository.findByUserIdAndDeletedFalse(userId);
        var map = new HashMap<Long, UserGrowthProgress>();
        for (var p : list) {
            map.put(p.getTaskId(), p);
        }
        return map;
    }

    private UserGrowthTaskVO toVO(UserGrowthTask t, UserGrowthProgress p) {
        var vo = new UserGrowthTaskVO();
        vo.setId(t.getId());
        vo.setCode(t.getCode());
        vo.setName(t.getName());
        vo.setDescription(t.getDescription());
        vo.setIcon(t.getIcon());
        vo.setCategory(t.getCategory());
        vo.setTriggerEvent(t.getTriggerEvent());
        vo.setTargetCount(t.getTargetCount());
        vo.setRewardCredits(t.getRewardCredits());
        vo.setRewardOutfit(t.getRewardOutfit());
        vo.setSortOrder(t.getSortOrder());
        if (p != null) {
            vo.setUserProgress(p.getProgress());
            vo.setUserStatus(p.getStatus());
            vo.setUserCompletedTime(p.getCompletedTime());
            vo.setUserClaimedTime(p.getClaimedTime());
        } else {
            vo.setUserProgress(0);
            vo.setUserStatus("PENDING");
        }
        return vo;
    }

    /**
     * 内部 API：事件触发时增加进度（由各业务模块调用，例如 AigcImage 完成时触发 'aigc.image.success'）。
     *
     * <p>v0.2.1 暴露但未集成到业务模块——业务模块可在成功 mutation 后主动调用，或后续接 sys_user_event 监听器。
     */
    @Transactional
    public void incrementProgressByEvent(Long userId, String eventCode) {
        var tasks = taskRepository.findByEnabledAndDeletedFalseOrderBySortOrderAsc(true);
        for (var task : tasks) {
            if (eventCode.equals(task.getTriggerEvent())) {
                incrementForTask(userId, task);
            }
        }
    }

    private void incrementForTask(Long userId, UserGrowthTask task) {
        var existing =
                progressRepository
                        .findByUserIdAndTaskIdAndDeletedFalse(userId, task.getId())
                        .orElseGet(
                                () -> {
                                    var p = new UserGrowthProgress();
                                    p.setUserId(userId);
                                    p.setTaskId(task.getId());
                                    p.setProgress(0);
                                    p.setStatus("PENDING");
                                    return p;
                                });
        if ("CLAIMED".equals(existing.getStatus())) return; // 已领取
        existing.setProgress(existing.getProgress() + 1);
        if (existing.getProgress() >= task.getTargetCount()
                && "PENDING".equals(existing.getStatus())) {
            existing.setStatus("COMPLETED");
            existing.setCompletedTime(LocalDateTime.now());
        }
        progressRepository.save(existing);
    }

    /** 用户领取奖励——发放积分（如有）+ 标记 CLAIMED。 */
    @Transactional
    public void claim(Long taskId) {
        Long userId = operatorContext.currentUserId().orElseThrow();
        var task =
                taskRepository
                        .findById(taskId)
                        .orElseThrow(
                                () -> new BusinessException(GlobalErrorCode.NOT_FOUND, "任务不存在"));
        var progress =
                progressRepository
                        .findByUserIdAndTaskIdAndDeletedFalse(userId, taskId)
                        .orElseThrow(
                                () -> new BusinessException(GlobalErrorCode.BAD_REQUEST, "尚未达成"));
        if (!"COMPLETED".equals(progress.getStatus())) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "任务未完成或已领取");
        }
        if (task.getRewardCredits() != null && task.getRewardCredits() > 0) {
            creditService.earn(
                    userId,
                    task.getRewardCredits(),
                    CreditTransactionSourceEnum.GROWTH_TASK.getCode(),
                    task.getCode());
        }
        progress.setStatus("CLAIMED");
        progress.setClaimedTime(LocalDateTime.now());
        progressRepository.save(progress);
        // WS 推送领取通知
        notificationService.sendSystemNotification(
                userId,
                "任务奖励已到账",
                "「" + task.getName() + "」完成奖励 +" + task.getRewardCredits() + " 积分，已存入账户");
    }
}
