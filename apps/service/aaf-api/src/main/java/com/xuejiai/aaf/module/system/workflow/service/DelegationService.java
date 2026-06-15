package com.xuejiai.aaf.module.system.workflow.service;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.common.model.PageResult;
import com.xuejiai.aaf.common.model.SpecificationBuilder;
import com.xuejiai.aaf.framework.engine.workflow.WorkflowEngine;
import com.xuejiai.aaf.module.system.workflow.domain.Delegation;
import com.xuejiai.aaf.module.system.workflow.repository.DelegationRepository;
import com.xuejiai.aaf.module.system.workflow.vo.DelegationCreateDTO;
import com.xuejiai.aaf.module.system.workflow.vo.DelegationPageDTO;
import com.xuejiai.aaf.module.system.workflow.vo.DelegationVO;
import com.xuejiai.aaf.module.system.workflow.vo.WorkflowTransferDTO;

import lombok.RequiredArgsConstructor;

/**
 * 审批委托业务逻辑。
 *
 * @author AaronZZH & Kiro
 */
@Service
@RequiredArgsConstructor
public class DelegationService {

    private final DelegationRepository delegationRepository;
    private final WorkflowEngine workflowEngine;

    /** 创建委托 */
    @Transactional
    public DelegationVO create(Long userId, DelegationCreateDTO dto) {
        var delegation = new Delegation();
        delegation.setDelegatorId(userId);
        delegation.setDelegateId(dto.delegateId());
        delegation.setStartDate(dto.startDate());
        delegation.setEndDate(dto.endDate());
        delegation.setProcessKeys(dto.processKeys());
        delegation.setStatus("active");
        return toVO(delegationRepository.save(delegation));
    }

    /** 分页查询 */
    public PageResult<DelegationVO> page(Long userId, DelegationPageDTO req) {
        var pageable = req.toPageable(Sort.by("id").descending());
        Specification<Delegation> spec =
                SpecificationBuilder.<Delegation>builder()
                        .eqIfPresent("delegatorId", userId)
                        .eqIfPresent("status", req.getStatus())
                        .build();
        var page = delegationRepository.findAll(spec, pageable);
        return new PageResult<>(
                page.getContent().stream().map(this::toVO).toList(), page.getTotalElements());
    }

    /** 取消委托 */
    @Transactional
    public void cancel(Long userId, Long id) {
        var delegation =
                delegationRepository
                        .findById(id)
                        .orElseThrow(
                                () -> new BusinessException(GlobalErrorCode.NOT_FOUND, "委托不存在"));
        if (!delegation.getDelegatorId().equals(userId)) {
            throw new BusinessException(GlobalErrorCode.FORBIDDEN, "无权操作此委托");
        }
        delegation.setStatus("cancelled");
        delegationRepository.save(delegation);
    }

    /** 查询委托人当前生效的委托 */
    public Optional<Delegation> findActiveDelegation(Long delegatorId) {
        var now = LocalDateTime.now();
        return delegationRepository
                .findByDelegatorIdAndStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                        delegatorId, "active", now, now);
    }

    /** 单次转交任务 */
    @Transactional
    public void transfer(WorkflowTransferDTO dto) {
        workflowEngine.reassignTask(dto.taskId(), dto.targetUserId().toString());
    }

    private DelegationVO toVO(Delegation d) {
        return new DelegationVO(
                d.getId(),
                d.getDelegatorId(),
                d.getDelegateId(),
                d.getStartDate(),
                d.getEndDate(),
                d.getProcessKeys(),
                d.getStatus(),
                d.getCreateTime());
    }
}
