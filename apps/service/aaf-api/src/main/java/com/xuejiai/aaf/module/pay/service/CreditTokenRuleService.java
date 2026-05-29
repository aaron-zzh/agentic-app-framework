package com.xuejiai.aaf.module.pay.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.module.pay.domain.CreditTokenRule;
import com.xuejiai.aaf.module.pay.repository.CreditTokenRuleRepository;
import com.xuejiai.aaf.module.pay.vo.CreditTokenRuleDTO;
import com.xuejiai.aaf.module.pay.vo.CreditTokenRuleVO;

import lombok.RequiredArgsConstructor;

/** 积分转 Token 规则服务 */
@Service
@RequiredArgsConstructor
public class CreditTokenRuleService {

    private final CreditTokenRuleRepository ruleRepository;

    /** 创建规则 */
    @Transactional
    public CreditTokenRuleVO create(CreditTokenRuleDTO dto) {
        var rule = new CreditTokenRule();
        applyDto(rule, dto);
        ruleRepository.save(rule);
        return toVO(rule);
    }

    /** 更新规则 */
    @Transactional
    public CreditTokenRuleVO update(Long id, CreditTokenRuleDTO dto) {
        var rule =
                ruleRepository
                        .findById(id)
                        .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND, "规则不存在"));
        applyDto(rule, dto);
        ruleRepository.save(rule);
        return toVO(rule);
    }

    /** 删除规则 */
    @Transactional
    public void delete(Long id) {
        ruleRepository.deleteById(id);
    }

    /** 查询所有规则 */
    @Transactional(readOnly = true)
    public List<CreditTokenRuleVO> list() {
        return ruleRepository.findAll().stream().map(this::toVO).toList();
    }

    /** 根据积分计算可兑换 Token 数量（使用最高优先级的生效规则） */
    @Transactional(readOnly = true)
    public long calculateTokens(long creditAmount) {
        var rules = ruleRepository.findEffectiveRules(LocalDateTime.now());
        if (rules.isEmpty()) {
            return 0;
        }
        var rule = rules.getFirst();
        return creditAmount * rule.getTokenAmount() / rule.getCreditAmount();
    }

    private void applyDto(CreditTokenRule rule, CreditTokenRuleDTO dto) {
        rule.setName(dto.name());
        rule.setCreditAmount(dto.creditAmount());
        rule.setTokenAmount(dto.tokenAmount());
        if (dto.status() != null) {
            rule.setStatus(dto.status());
        }
        if (dto.priority() != null) {
            rule.setPriority(dto.priority());
        }
        rule.setEffectiveFrom(dto.effectiveFrom());
        rule.setEffectiveTo(dto.effectiveTo());
    }

    private CreditTokenRuleVO toVO(CreditTokenRule r) {
        return new CreditTokenRuleVO(
                r.getId(),
                r.getName(),
                r.getCreditAmount(),
                r.getTokenAmount(),
                r.getStatus(),
                r.getPriority(),
                r.getEffectiveFrom(),
                r.getEffectiveTo(),
                r.getCreateTime());
    }
}
