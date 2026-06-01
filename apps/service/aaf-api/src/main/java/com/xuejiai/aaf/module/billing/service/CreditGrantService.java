package com.xuejiai.aaf.module.billing.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.framework.engine.credit.CreditService;
import com.xuejiai.aaf.module.billing.repository.CreditGrantRuleRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 积分发放服务——按规则 code 查配置后调用 CreditService.earnBatch()。
 *
 * <p>业务代码（注册、邀请、探索奖励等）只需调用 grant(userId, ruleCode, bizId)，
 * 不需要关心积分数量和有效期，由规则表统一配置。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CreditGrantService {

    private final CreditGrantRuleRepository ruleRepository;
    private final CreditService creditService;

    /**
     * 按规则发放积分。
     *
     * @param userId  目标用户
     * @param code    规则编码（WEEKLY/INVITE/EXPLORE/REGISTER 等）
     * @param bizId   关联业务 ID（用于幂等和溯源）
     * @return 实际发放积分数，规则不存在或已禁用时返回 0
     */
    @Transactional
    public long grant(Long userId, String code, String bizId) {
        var rule = ruleRepository.findByCodeAndStatus(code, "ENABLED").orElse(null);
        if (rule == null) {
            log.warn("积分发放规则不存在或已禁用: code={}", code);
            return 0;
        }
        creditService.earnBatch(
                userId,
                rule.getAmount(),
                code,
                code,
                bizId,
                rule.calcExpireAt());
        log.info("积分发放: userId={}, rule={}, amount={}", userId, code, rule.getAmount());
        return rule.getAmount();
    }
}
