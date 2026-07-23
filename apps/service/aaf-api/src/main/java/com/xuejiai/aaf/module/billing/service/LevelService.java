package com.xuejiai.aaf.module.billing.service;

import java.util.Set;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.framework.engine.credit.CreditAccountRepository;
import com.xuejiai.aaf.module.billing.domain.Level;
import com.xuejiai.aaf.module.billing.repository.LevelRepository;
import com.xuejiai.aaf.module.billing.vo.LevelCreateDTO;
import com.xuejiai.aaf.module.billing.vo.LevelPageParam;
import com.xuejiai.aaf.module.billing.vo.LevelUpdateDTO;
import com.xuejiai.aaf.module.billing.vo.LevelVO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 会员等级定义及经验值服务。 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LevelService
        extends BaseCrudService<Level, LevelVO, LevelCreateDTO, LevelUpdateDTO, LevelPageParam> {

    private static final Set<String> SORTABLE_FIELDS =
            Set.of("id", "code", "name", "expMin", "expMax", "sort", "createTime", "updateTime");

    private final LevelRepository levelRepository;
    private final CreditAccountRepository creditAccountRepository;

    @Override
    protected LevelRepository getRepository() {
        return levelRepository;
    }

    @Override
    protected Specification<Level> buildSpec(LevelPageParam request) {
        return (root, query, cb) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            if (StringUtils.hasText(request.getKeyword())) {
                var pattern = "%" + request.getKeyword().trim() + "%";
                predicates.add(
                        cb.or(
                                cb.like(root.get("code"), pattern),
                                cb.like(root.get("name"), pattern)));
            }
            if (StringUtils.hasText(request.getCode())) {
                predicates.add(cb.equal(root.get("code"), request.getCode().trim()));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    @Override
    protected Specification<Level> buildOptionSpec(String keyword) {
        return (root, query, cb) -> {
            if (!StringUtils.hasText(keyword)) {
                return null;
            }
            var pattern = "%" + keyword.trim() + "%";
            return cb.or(cb.like(root.get("code"), pattern), cb.like(root.get("name"), pattern));
        };
    }

    @Override
    protected LevelVO toVO(Level level) {
        return new LevelVO(
                level.getId(),
                level.getCode(),
                level.getName(),
                level.getExpMin(),
                level.getExpMax(),
                level.getPerks(),
                level.getSort(),
                level.getCreateTime(),
                level.getUpdateTime());
    }

    @Override
    protected Level toEntity(LevelCreateDTO dto) {
        levelRepository
                .findByCode(dto.code())
                .ifPresent(
                        level -> {
                            throw new BusinessException(
                                    GlobalErrorCode.BAD_REQUEST, "等级编码已存在: " + dto.code());
                        });
        validateRange(dto.expMin(), dto.expMax());
        var level = new Level();
        level.setCode(dto.code().trim());
        applyCreate(level, dto);
        return level;
    }

    @Override
    protected void updateEntity(Level level, LevelUpdateDTO dto) {
        var expMin = dto.expMin() == null ? level.getExpMin() : dto.expMin();
        var expMax = dto.expMax() == null ? level.getExpMax() : dto.expMax();
        validateRange(expMin, expMax);
        if (StringUtils.hasText(dto.name())) level.setName(dto.name().trim());
        if (dto.expMin() != null) level.setExpMin(dto.expMin());
        if (dto.expMax() != null) level.setExpMax(dto.expMax());
        if (dto.perks() != null) level.setPerks(dto.perks());
        if (dto.sort() != null) level.setSort(dto.sort());
    }

    /** 增加经验值并自动更新用户等级。 */
    @Transactional
    public Level addExp(Long userId, int expDelta) {
        var account =
                creditAccountRepository
                        .findByUserId(userId)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                GlobalErrorCode.NOT_FOUND, "用户积分账户不存在"));
        var currentExp = account.getExp() + expDelta;
        account.setExp(currentExp);
        var level =
                levelRepository
                        .findByExpMinLessThanEqualAndExpMaxGreaterThanEqual(currentExp, currentExp)
                        .orElse(null);
        if (level != null) account.setLevelId(level.getId());
        creditAccountRepository.save(account);
        log.info(
                "用户 {} 经验值变更: {}, 当前={}, 等级={}",
                userId,
                expDelta,
                currentExp,
                level == null ? "未知" : level.getCode());
        return level;
    }

    @Transactional(readOnly = true)
    public Level getCurrentLevel(Long userId) {
        var account = creditAccountRepository.findByUserId(userId).orElse(null);
        if (account == null || account.getLevelId() == null) {
            return levelRepository.findByCode("L0").orElse(null);
        }
        return levelRepository.findById(account.getLevelId()).orElse(null);
    }

    @Transactional(readOnly = true)
    public int getExp(Long userId) {
        return creditAccountRepository
                .findByUserId(userId)
                .map(account -> account.getExp())
                .orElse(0);
    }

    private void applyCreate(Level level, LevelCreateDTO dto) {
        level.setName(dto.name().trim());
        level.setExpMin(dto.expMin());
        level.setExpMax(dto.expMax());
        level.setPerks(dto.perks());
        level.setSort(dto.sort());
    }

    private void validateRange(Integer expMin, Integer expMax) {
        if (expMin == null || expMax == null || expMin > expMax) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "经验值区间无效");
        }
    }
}
