package com.xuejiai.aaf.module.ai.output.service;

import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.enums.RiskLevel;
import com.xuejiai.aaf.module.ai.output.domain.AiOutput;
import com.xuejiai.aaf.module.ai.output.domain.enums.AiOutputStatus;
import com.xuejiai.aaf.module.ai.output.repository.AiOutputRepository;
import com.xuejiai.aaf.module.ai.output.vo.AiOutputVO;
import com.xuejiai.aaf.module.system.notify.service.NotificationService;

import lombok.RequiredArgsConstructor;

/** AI 产出服务——记录、查询、调整、回退。高风险产出自动推送通知。 */
@Service
@RequiredArgsConstructor
public class AiOutputService {

    private final AiOutputRepository repository;
    private final NotificationService notificationService;

    /** 记录产出（高风险自动推送通知） */
    @Transactional
    public AiOutput record(AiOutput output) {
        repository.save(output);
        if (RiskLevel.HIGH == output.getRiskLevel()) {
            notificationService.sendSystemNotification(
                    output.getCreatorId(), "🔴 高风险 AI 产出", output.getTitle());
        }
        return output;
    }

    /** 分页查询（支持筛选） */
    @Transactional(readOnly = true)
    public Page<AiOutputVO> list(
            Long creatorId,
            String category,
            String riskLevel,
            String sourceType,
            int page,
            int size) {
        return repository
                .findFiltered(
                        creatorId, category, riskLevel, sourceType, PageRequest.of(page, size))
                .map(AiOutputVO::from);
    }

    /** 获取详情——必须限定归属主体。 */
    @Transactional(readOnly = true)
    public AiOutputVO getById(Long id, Long ownerId) {
        return AiOutputVO.from(requireOwned(id, ownerId));
    }

    /** 调整产出——必须限定归属主体。 */
    @Transactional
    public AiOutputVO adjust(Long id, Long ownerId, String note) {
        var output = requireOwned(id, ownerId);
        output.setStatus(AiOutputStatus.ADJUSTED);
        output.setAdjustNote(note);
        return AiOutputVO.from(repository.save(output));
    }

    /**
     * 回退产出——必须限定归属主体。
     *
     * <p>当前只落状态与原因，不执行补偿动作；无完整 revertInfo、幂等补偿与资源版本校验时不得声称已回退。
     */
    @Transactional
    public AiOutputVO revert(Long id, Long ownerId, String reason) {
        var output = requireOwned(id, ownerId);
        output.setStatus(AiOutputStatus.REVERTED);
        output.setAdjustNote(reason);
        return AiOutputVO.from(repository.save(output));
    }

    /** 按归属主体读取，越权访问与不存在一律视为不存在，不泄露资源存在性。 */
    private AiOutput requireOwned(Long id, Long ownerId) {
        if (id == null || ownerId == null) {
            throw new IllegalArgumentException("产出 ID 与归属主体不能为空");
        }
        return repository
                .findByIdAndCreatorIdAndDeletedFalse(id, ownerId)
                .orElseThrow(() -> new AccessDeniedException("无权访问该 AI 产出"));
    }

    /** 统计 */
    @Transactional(readOnly = true)
    public Map<String, Long> stats(Long creatorId) {
        return Map.of(
                "high",
                        repository.countByCreatorIdAndRiskLevelAndStatusAndDeletedFalse(
                                creatorId, RiskLevel.HIGH, AiOutputStatus.EFFECTIVE),
                "medium",
                        repository.countByCreatorIdAndRiskLevelAndStatusAndDeletedFalse(
                                creatorId, RiskLevel.MEDIUM, AiOutputStatus.EFFECTIVE),
                "low",
                        repository.countByCreatorIdAndRiskLevelAndStatusAndDeletedFalse(
                                creatorId, RiskLevel.LOW, AiOutputStatus.EFFECTIVE));
    }
}
