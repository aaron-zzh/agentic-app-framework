package com.xuejiai.aaf.module.chat.livechat.rating.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Service;

import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.module.chat.livechat.rating.domain.SessionRating;
import com.xuejiai.aaf.module.chat.livechat.rating.repository.SessionRatingRepository;
import com.xuejiai.aaf.module.chat.livechat.rating.vo.RatingCreateDTO;
import com.xuejiai.aaf.module.chat.livechat.rating.vo.RatingPageDTO;
import com.xuejiai.aaf.module.chat.livechat.rating.vo.RatingUpdateDTO;
import com.xuejiai.aaf.module.chat.livechat.rating.vo.RatingVO;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;

/**
 * 会话评价 CRUD Service。
 *
 * <p>更新操作仅允许修改评价内容（comment），评分和关联关系不可更改。
 *
 * @author AaronZZH & Kiro
 */
@Service
@RequiredArgsConstructor
public class RatingCrudService
        extends BaseCrudService<
                SessionRating, RatingVO, RatingCreateDTO, RatingUpdateDTO, RatingPageDTO> {

    private final SessionRatingRepository repository;

    @Override
    protected JpaRepository<SessionRating, Long> getRepository() {
        return repository;
    }

    @Override
    protected JpaSpecificationExecutor<SessionRating> getSpecExecutor() {
        return repository;
    }

    @Override
    protected RatingVO toVO(SessionRating entity) {
        return new RatingVO(
                entity.getId(),
                entity.getConversationId(),
                entity.getUserId(),
                entity.getStaffId(),
                entity.getScore(),
                entity.getComment(),
                entity.getCreateTime());
    }

    @Override
    protected SessionRating toEntity(RatingCreateDTO dto) {
        var entity = new SessionRating();
        entity.setConversationId(dto.conversationId());
        entity.setStaffId(dto.staffId());
        entity.setScore(dto.score());
        entity.setComment(dto.comment());
        return entity;
    }

    /** 仅允许修改 comment，其他字段不可变。 */
    @Override
    protected void updateEntity(SessionRating entity, RatingUpdateDTO dto) {
        entity.setComment(dto.comment());
    }

    @Override
    protected Specification<SessionRating> buildSpec(RatingPageDTO dto) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (dto.getConversationId() != null) {
                predicates.add(cb.equal(root.get("conversationId"), dto.getConversationId()));
            }
            if (dto.getStaffId() != null) {
                predicates.add(cb.equal(root.get("staffId"), dto.getStaffId()));
            }
            if (dto.getMinScore() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("score"), dto.getMinScore()));
            }
            if (dto.getMaxScore() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("score"), dto.getMaxScore()));
            }
            return predicates.isEmpty() ? null : cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
