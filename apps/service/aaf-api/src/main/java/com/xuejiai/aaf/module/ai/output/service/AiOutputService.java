package com.xuejiai.aaf.module.ai.output.service;

import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.module.ai.output.domain.AiOutput;
import com.xuejiai.aaf.module.ai.output.repository.AiOutputRepository;
import com.xuejiai.aaf.module.system.notify.service.NotificationService;

import lombok.RequiredArgsConstructor;

/**
 * AI 产出服务——记录、查询、调整、回退。高风险产出自动推送通知。
 */
@Service
@RequiredArgsConstructor
public class AiOutputService {

    private final AiOutputRepository repository;
    private final NotificationService notificationService;

    /** 记录产出（高风险自动推送通知） */
    @Transactional
    public AiOutput record(AiOutput output) {
        repository.save(output);
        if ("high".equals(output.getRiskLevel())) {
            notificationService.sendSystemNotification(
                    output.getCreatorId(),
                    "🔴 高风险 AI 产出",
                    output.getTitle());
        }
        return output;
    }

    /** 分页查询（支持筛选） */
    @Transactional(readOnly = true)
    public Page<AiOutput> list(Long creatorId, String category, String riskLevel, String sourceType, int page, int size) {
        return repository.findFiltered(creatorId, category, riskLevel, sourceType, PageRequest.of(page, size));
    }

    /** 获取详情 */
    @Transactional(readOnly = true)
    public AiOutput getById(Long id) {
        return repository.findById(id).orElseThrow();
    }

    /** 调整产出 */
    @Transactional
    public AiOutput adjust(Long id, String note) {
        var output = repository.findById(id).orElseThrow();
        output.setStatus("adjusted");
        output.setAdjustNote(note);
        return repository.save(output);
    }

    /** 回退产出 */
    @Transactional
    public AiOutput revert(Long id, String reason) {
        var output = repository.findById(id).orElseThrow();
        output.setStatus("reverted");
        output.setAdjustNote(reason);
        // 实际回退操作由调用方根据 revertInfo 执行
        return repository.save(output);
    }

    /** 统计 */
    @Transactional(readOnly = true)
    public Map<String, Long> stats(Long creatorId) {
        return Map.of(
                "high", repository.countByCreatorIdAndRiskLevelAndStatusAndDeletedFalse(creatorId, "high", "effective"),
                "medium", repository.countByCreatorIdAndRiskLevelAndStatusAndDeletedFalse(creatorId, "medium", "effective"),
                "low", repository.countByCreatorIdAndRiskLevelAndStatusAndDeletedFalse(creatorId, "low", "effective"));
    }
}
