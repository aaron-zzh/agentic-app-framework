package com.xuejiai.aaf.module.system.user.favorite.service;

import java.util.ArrayList;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.module.system.user.favorite.domain.UserFavorite;
import com.xuejiai.aaf.module.system.user.favorite.repository.UserFavoriteRepository;
import com.xuejiai.aaf.module.system.user.favorite.vo.UserFavoriteCreateDTO;
import com.xuejiai.aaf.module.system.user.favorite.vo.UserFavoritePageDTO;
import com.xuejiai.aaf.module.system.user.favorite.vo.UserFavoriteVO;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;

/** 用户收藏夹服务，所有操作强制绑定当前 userId。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserFavoriteService
        extends BaseCrudService<
                UserFavorite, UserFavoriteVO, UserFavoriteCreateDTO, Void, UserFavoritePageDTO> {

    private final UserFavoriteRepository favoriteRepository;
    private final OperatorContext operatorContext;

    @Override
    protected JpaRepository<UserFavorite, Long> getRepository() {
        return favoriteRepository;
    }

    @Override
    protected JpaSpecificationExecutor<UserFavorite> getSpecExecutor() {
        return favoriteRepository;
    }

    @Override
    protected String entityName() {
        return "收藏";
    }

    @Override
    protected UserFavoriteVO toVO(UserFavorite e) {
        var vo = new UserFavoriteVO();
        vo.setId(e.getId());
        vo.setTargetType(e.getTargetType());
        vo.setTargetId(e.getTargetId());
        vo.setNote(e.getNote());
        vo.setSortOrder(e.getSortOrder());
        vo.setCreateTime(e.getCreateTime());
        return vo;
    }

    @Override
    protected UserFavorite toEntity(UserFavoriteCreateDTO dto) {
        var e = new UserFavorite();
        e.setUserId(operatorContext.currentUserId().orElseThrow());
        e.setTargetType(dto.targetType());
        e.setTargetId(dto.targetId());
        e.setNote(dto.note());
        return e;
    }

    @Override
    protected void updateEntity(UserFavorite e, Void dto) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected Specification<UserFavorite> buildSpec(UserFavoritePageDTO p) {
        Long userId = operatorContext.currentUserId().orElseThrow();
        return (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            predicates.add(cb.equal(root.get("userId"), userId));
            if (p.getTargetType() != null)
                predicates.add(cb.equal(root.get("targetType"), p.getTargetType()));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /** 按目标删除（toggle 用）。 */
    @Transactional
    public void deleteByTarget(String targetType, Long targetId) {
        Long userId = operatorContext.currentUserId().orElseThrow();
        favoriteRepository.deleteByUserIdAndTargetTypeAndTargetId(userId, targetType, targetId);
    }

    /** 删除单条收藏，校验 ownership。 */
    @Transactional
    public void deleteOwn(Long id) {
        Long userId = operatorContext.currentUserId().orElseThrow();
        var entity =
                favoriteRepository
                        .findById(id)
                        .orElseThrow(
                                () -> new BusinessException(GlobalErrorCode.NOT_FOUND, "收藏不存在"));
        if (!entity.getUserId().equals(userId)) {
            throw new BusinessException(GlobalErrorCode.NOT_FOUND, "收藏不存在");
        }
        favoriteRepository.delete(entity);
    }
}
