package com.xuejiai.aaf.module.brokerage.service;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.enums.brokerage.BrokerageWithdrawStatusEnum;
import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.module.brokerage.domain.BrokerageWithdraw;
import com.xuejiai.aaf.module.brokerage.repository.BrokerageUserRepository;
import com.xuejiai.aaf.module.brokerage.repository.BrokerageWithdrawRepository;
import com.xuejiai.aaf.module.brokerage.vo.BrokerageWithdrawDTO;
import com.xuejiai.aaf.module.brokerage.vo.BrokerageWithdrawPageParam;
import com.xuejiai.aaf.module.brokerage.vo.BrokerageWithdrawVO;

import lombok.RequiredArgsConstructor;

/** 佣金提现 CRUD 服务。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BrokerageWithdrawCrudService
        extends BaseCrudService<
                BrokerageWithdraw,
                BrokerageWithdrawVO,
                BrokerageWithdrawDTO,
                BrokerageWithdrawDTO,
                BrokerageWithdrawPageParam> {

    private final BrokerageWithdrawRepository brokerageWithdrawRepository;
    private final BrokerageUserRepository brokerageUserRepository;

    @Override
    protected JpaRepository<BrokerageWithdraw, Long> getRepository() {
        return brokerageWithdrawRepository;
    }

    @Override
    protected JpaSpecificationExecutor<BrokerageWithdraw> getSpecExecutor() {
        return brokerageWithdrawRepository;
    }

    @Override
    protected BrokerageWithdrawVO toVO(BrokerageWithdraw e) {
        return new BrokerageWithdrawVO(
                e.getId(),
                e.getContactId(),
                e.getAmount(),
                e.getFee(),
                e.getType(),
                e.getAccountName(),
                e.getAccountNo(),
                e.getQrCodeUrl(),
                e.getStatus(),
                e.getAuditReason(),
                e.getAuditTime(),
                e.getPayTransferId(),
                e.getTransferTime(),
                e.getCreateTime(),
                e.getUpdateTime());
    }

    @Override
    protected BrokerageWithdraw toEntity(BrokerageWithdrawDTO dto) {
        // 校验余额是否充足
        var bu = brokerageUserRepository.findByContactId(dto.contactId()).orElse(null);
        if (bu == null || bu.getBalance() < dto.amount()) {
            throw new com.xuejiai.aaf.common.exception.BusinessException(
                    com.xuejiai.aaf.common.exception.GlobalErrorCode.BAD_REQUEST,
                    "可用佣金余额不足，当前余额: " + (bu == null ? 0 : bu.getBalance()) + " 分");
        }
        var e = new BrokerageWithdraw();
        e.setContactId(dto.contactId());
        e.setAmount(dto.amount());
        e.setType(dto.type());
        e.setAccountName(dto.accountName());
        e.setAccountNo(dto.accountNo());
        e.setQrCodeUrl(dto.qrCodeUrl());
        return e;
    }

    @Override
    protected void updateEntity(BrokerageWithdraw e, BrokerageWithdrawDTO dto) {
        if (dto.accountName() != null) e.setAccountName(dto.accountName());
        if (dto.accountNo() != null) e.setAccountNo(dto.accountNo());
        if (dto.qrCodeUrl() != null) e.setQrCodeUrl(dto.qrCodeUrl());
        // 审核操作
        if (dto.status() != null) {
            e.setStatus(dto.status());
            if (dto.auditReason() != null) e.setAuditReason(dto.auditReason());
            if (dto.status() == BrokerageWithdrawStatusEnum.APPROVED
                    || dto.status() == BrokerageWithdrawStatusEnum.REJECTED) {
                e.setAuditTime(java.time.LocalDateTime.now());
            }
        }
    }

    @Override
    protected Specification<BrokerageWithdraw> buildSpec(BrokerageWithdrawPageParam p) {
        return (root, query, cb) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            if (p.getContactId() != null)
                predicates.add(cb.equal(root.get("contactId"), p.getContactId()));
            if (p.getStatus() != null) predicates.add(cb.equal(root.get("status"), p.getStatus()));
            if (p.getType() != null) predicates.add(cb.equal(root.get("type"), p.getType()));
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    @Override
    protected String entityName() {
        return "佣金提现";
    }
}
