package com.xuejiai.aaf.module.billing.service;

import java.time.LocalDate;
import java.time.LocalTime;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.framework.engine.credit.CreditService;
import com.xuejiai.aaf.framework.engine.credit.CreditTransaction;
import com.xuejiai.aaf.module.billing.domain.EntitlementLedger;
import com.xuejiai.aaf.module.billing.repository.EntitlementLedgerRepository;
import com.xuejiai.aaf.module.billing.vo.BillingSummaryVO;

import lombok.RequiredArgsConstructor;

/** 账单查询服务——复用 credit_transaction + entitlement_ledger。 */
@Service
@RequiredArgsConstructor
public class BillingQueryService {

    private final CreditService creditService;
    private final EntitlementLedgerRepository ledgerRepository;

    /** 积分余额 */
    @Transactional(readOnly = true)
    public long getCreditBalance(Long userId) {
        return creditService.getBalance(userId);
    }

    /** 积分流水分页查询 */
    @Transactional(readOnly = true)
    public Page<CreditTransaction> getCreditTransactions(Long userId, Pageable pageable) {
        return creditService.getTransactions(userId, pageable);
    }

    /** 权益消费流水分页查询 */
    @Transactional(readOnly = true)
    public Page<EntitlementLedger> getEntitlementLedger(Long userId, Pageable pageable) {
        return ledgerRepository.findByUserId(userId, pageable);
    }

    /** 日/月账单汇总 */
    @Transactional(readOnly = true)
    public BillingSummaryVO getSummary(Long userId, LocalDate startDate, LocalDate endDate) {
        var start = startDate.atStartOfDay();
        var end = endDate.atTime(LocalTime.MAX);

        var ledgers = ledgerRepository.findByUserIdAndCreatedAtBetween(userId, start, end);

        long totalConsumed =
                ledgers.stream()
                        .filter(l -> l.getDelta() < 0)
                        .mapToLong(l -> Math.abs(l.getDelta()))
                        .sum();

        long totalRefilled =
                ledgers.stream()
                        .filter(l -> l.getDelta() > 0 && !"RESET".equals(l.getOperation()))
                        .mapToLong(EntitlementLedger::getDelta)
                        .sum();

        long creditBalance = creditService.getBalance(userId);

        return new BillingSummaryVO(totalConsumed, totalRefilled, creditBalance, ledgers.size());
    }

    /** 导出 CSV 骨架（返回 CSV 字符串） */
    @Transactional(readOnly = true)
    public String exportCsv(Long userId, LocalDate startDate, LocalDate endDate) {
        var start = startDate.atStartOfDay();
        var end = endDate.atTime(LocalTime.MAX);
        var ledgers = ledgerRepository.findByUserIdAndCreatedAtBetween(userId, start, end);

        var sb = new StringBuilder("时间,操作,变化量,业务类型,业务ID\n");
        for (var l : ledgers) {
            sb.append(
                    "%s,%s,%d,%s,%s\n"
                            .formatted(
                                    l.getCreatedAt(),
                                    l.getOperation(),
                                    l.getDelta(),
                                    l.getBizType() != null ? l.getBizType() : "",
                                    l.getBizId() != null ? l.getBizId().toString() : ""));
        }
        return sb.toString();
    }
}
