package com.xuejiai.aaf.module.billing.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.framework.engine.credit.CreditAccountRepository;
import com.xuejiai.aaf.module.billing.domain.Level;
import com.xuejiai.aaf.module.billing.repository.LevelRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 会员等级服务（成长线，免费）。
 *
 * <p>exp 成长值挂在 credit_account（复用 wallet 的 exp/level_id 列），
 * 根据 exp 区间自动升降级。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LevelService {

    private final LevelRepository levelRepository;
    private final CreditAccountRepository creditAccountRepository;

    /** 获取所有等级定义 */
    @Transactional(readOnly = true)
    public List<Level> listAll() {
        return levelRepository.findAllByOrderBySortAsc();
    }

    /** 增加经验值并自动升降级，返回当前等级 */
    @Transactional
    public Level addExp(Long userId, int expDelta) {
        var account = creditAccountRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalStateException("用户积分账户不存在: " + userId));

        var currentExp = account.getExp() + expDelta;
        account.setExp(currentExp);

        // 查找匹配等级
        var level = levelRepository.findByExpMinLessThanEqualAndExpMaxGreaterThanEqual(currentExp, currentExp)
                .orElse(null);
        if (level != null) {
            account.setLevelId(level.getId());
        }
        creditAccountRepository.save(account);

        log.info("用户 {} 经验值变更: +{}, 当前={}, 等级={}", userId, expDelta, currentExp,
                level != null ? level.getCode() : "未知");
        return level;
    }

    /** 获取用户当前等级 */
    @Transactional(readOnly = true)
    public Level getCurrentLevel(Long userId) {
        var account = creditAccountRepository.findByUserId(userId).orElse(null);
        if (account == null || account.getLevelId() == null) {
            return levelRepository.findByCode("L0").orElse(null);
        }
        return levelRepository.findById(account.getLevelId()).orElse(null);
    }

    /** 获取用户当前经验值 */
    @Transactional(readOnly = true)
    public int getExp(Long userId) {
        return creditAccountRepository.findByUserId(userId)
                .map(a -> a.getExp())
                .orElse(0);
    }
}
