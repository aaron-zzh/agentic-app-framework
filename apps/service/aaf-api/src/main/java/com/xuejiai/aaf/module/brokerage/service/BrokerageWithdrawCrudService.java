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
import com.xuejiai.aaf.module.system.notify.service.NotificationService;
import com.xuejiai.aaf.module.system.user.repository.UserRepository;

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
    private final UserRepository userRepository;
    private final NotificationService notificationService;

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
                sendAuditNotification(e, dto.status(), dto.auditReason());
            }
        }
    }

    /**
     * 审核通过/驳回时给申请人发站内消息。
     *
     * <p>反查 contactId → userId；用户不存在则跳过通知（提现记录通常对应有效用户，但保留容错）。
     */
    private void sendAuditNotification(
            BrokerageWithdraw e, BrokerageWithdrawStatusEnum status, String auditReason) {
        var userOpt = userRepository.findByContactId(e.getContactId());
        if (userOpt.isEmpty()) return;

        String amount = String.format("¥%.2f", e.getAmount() / 100.0);
        String title;
        String body;
        if (status == BrokerageWithdrawStatusEnum.APPROVED) {
            title = "提现申请已通过";
            body =
                    String.format(
                            "您的提现申请（%s）已审核通过，款项将在 1-3 个工作日内打款。", amount);
        } else {
            title = "提现申请已驳回";
            String reason =
                    auditReason != null && !auditReason.isBlank()
                            ? "原因：" + auditReason
                            : "请联系客服了解详情。";
            body = String.format("您的提现申请（%s）未通过审核。%s", amount, reason);
        }
        notificationService.send(
                userOpt.get().getId(),
                "BROKERAGE_WITHDRAW",
                title,
                body,
                "/settings/withdraw",
                "BROKERAGE_WITHDRAW",
                e.getId());
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
