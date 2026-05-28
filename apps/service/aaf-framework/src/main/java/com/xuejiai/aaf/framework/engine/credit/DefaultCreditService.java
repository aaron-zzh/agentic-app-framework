package com.xuejiai.aaf.framework.engine.credit;

import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

/** 默认积分服务——无限余额，仅记录日志。后续对接会员/充值系统时替换。 */
@Slf4j
@Service
public class DefaultCreditService implements CreditService {

    @Override
    public long getBalance(Long userId) {
        return -1;
    }

    @Override
    public boolean hasBudget(Long userId, long estimatedCost) {
        return true;
    }

    @Override
    public void deduct(Long userId, long amount, String reason) {
        log.debug("积分扣减（占位）: userId={}, amount={}, reason={}", userId, amount, reason);
    }
}
