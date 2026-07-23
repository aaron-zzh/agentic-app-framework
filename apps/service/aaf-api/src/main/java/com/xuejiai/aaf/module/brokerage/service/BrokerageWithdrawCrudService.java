package com.xuejiai.aaf.module.brokerage.service;

import java.time.LocalDateTime;
import java.util.Set;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.enums.brokerage.BrokerageWithdrawStatusEnum;
import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.framework.crud.ReadonlyCrudService;
import com.xuejiai.aaf.module.brokerage.domain.BrokerageWithdraw;
import com.xuejiai.aaf.module.brokerage.repository.BrokerageUserRepository;
import com.xuejiai.aaf.module.brokerage.repository.BrokerageWithdrawRepository;
import com.xuejiai.aaf.module.brokerage.vo.BrokerageWithdrawPageParam;
import com.xuejiai.aaf.module.brokerage.vo.BrokerageWithdrawVO;
import com.xuejiai.aaf.module.system.notify.service.NotificationService;
import com.xuejiai.aaf.module.system.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/** 佣金提现管理服务，通用 CRUD 只读，状态只能通过具名动作流转。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BrokerageWithdrawCrudService
        extends ReadonlyCrudService<
                BrokerageWithdraw, BrokerageWithdrawVO, BrokerageWithdrawPageParam> {

    private static final Set<String> SORTABLE_FIELDS =
            Set.of(
                    "id",
                    "createTime",
                    "updateTime",
                    "contactId",
                    "amount",
                    "fee",
                    "type",
                    "status",
                    "auditTime",
                    "transferTime");

    private final BrokerageWithdrawRepository brokerageWithdrawRepository;
    private final BrokerageUserRepository brokerageUserRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    @Override
    protected BrokerageWithdrawRepository getRepository() {
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

    /** 审核通过待审核提现，不执行支付。 */
    // TODO(security): 改为独立 approve 领域命令，经统一 PDP 绑定提现 ID、命令摘要和 CURRENT/PROPOSED；通知必须在授权成功后发送。
    @Transactional
    public BrokerageWithdrawVO approve(Long id) {
        var withdraw = requirePending(id);
        withdraw.setStatus(BrokerageWithdrawStatusEnum.APPROVED);
        withdraw.setAuditReason(null);
        withdraw.setAuditTime(LocalDateTime.now());
        brokerageWithdrawRepository.save(withdraw);
        sendAuditNotification(withdraw, BrokerageWithdrawStatusEnum.APPROVED, null);
        return toVO(withdraw);
    }

    /** 驳回待审核提现并解除该申请冻结的佣金。 */
    // TODO(security): 改为独立 reject 领域命令，经统一 PDP 绑定驳回原因和状态快照；余额解冻及通知必须在授权成功后执行。
    @Transactional
    public BrokerageWithdrawVO reject(Long id, String auditReason) {
        if (auditReason == null || auditReason.isBlank()) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "驳回提现必须填写原因");
        }
        var withdraw = requirePending(id);
        int updated =
                brokerageUserRepository.addBalanceAndReduceFrozen(
                        withdraw.getContactId(), withdraw.getAmount());
        if (updated != 1) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "提现申请人不存在分销账户");
        }
        withdraw.setStatus(BrokerageWithdrawStatusEnum.REJECTED);
        withdraw.setAuditReason(auditReason.trim());
        withdraw.setAuditTime(LocalDateTime.now());
        brokerageWithdrawRepository.save(withdraw);
        sendAuditNotification(
                withdraw, BrokerageWithdrawStatusEnum.REJECTED, withdraw.getAuditReason());
        return toVO(withdraw);
    }

    /** 确认已完成线下或外部转账，不创建或执行支付单。 */
    // TODO(security): 改为独立 confirm-transfer 领域命令，经统一 PDP 绑定转账单 ID 和状态快照，禁止继续使用 GET 授权执行写入。
    @Transactional
    public BrokerageWithdrawVO confirmTransfer(Long id, Long payTransferId) {
        if (payTransferId == null || payTransferId <= 0) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "转账单 ID 必须为正数");
        }
        var withdraw = requireEntity(id);
        if (withdraw.getStatus() != BrokerageWithdrawStatusEnum.APPROVED) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "仅审核通过的提现申请可确认转账");
        }
        withdraw.setPayTransferId(payTransferId);
        withdraw.setStatus(BrokerageWithdrawStatusEnum.TRANSFERRED);
        withdraw.setTransferTime(LocalDateTime.now());
        brokerageWithdrawRepository.save(withdraw);
        return toVO(withdraw);
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

    private BrokerageWithdraw requirePending(Long id) {
        var withdraw = requireEntity(id);
        if (withdraw.getStatus() != BrokerageWithdrawStatusEnum.PENDING) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "仅待审核的提现申请可审核");
        }
        return withdraw;
    }

    /** 审核通过或驳回时给申请人发站内消息。 */
    private void sendAuditNotification(
            BrokerageWithdraw withdraw, BrokerageWithdrawStatusEnum status, String auditReason) {
        var userOpt = userRepository.findByContactId(withdraw.getContactId());
        if (userOpt.isEmpty()) return;

        String amount = String.format("¥%.2f", withdraw.getAmount() / 100.0);
        String title;
        String body;
        if (status == BrokerageWithdrawStatusEnum.APPROVED) {
            title = "提现申请已通过";
            body = String.format("您的提现申请（%s）已审核通过，款项将在 1-3 个工作日内打款。", amount);
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
                withdraw.getId());
    }
}
