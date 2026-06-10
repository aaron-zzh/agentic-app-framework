package com.xuejiai.aaf.module.chat.livechat.rating.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import com.xuejiai.aaf.module.chat.livechat.rating.domain.SessionRating;

/**
 * 会话评价数据访问层。
 *
 * @author AaronZZH & Kiro
 */
public interface SessionRatingRepository
        extends JpaRepository<SessionRating, Long>, JpaSpecificationExecutor<SessionRating> {

    /** 按会话 ID 查询所有评价 */
    List<SessionRating> findByConversationId(Long conversationId);

    /** 按客服 ID 查询所有评价 */
    List<SessionRating> findByStaffId(Long staffId);

    /** 判断会话是否已有评价 */
    boolean existsByConversationId(Long conversationId);

    // ========== 统计查询 ==========

    @Query(
            "SELECT AVG(r.score) FROM SessionRating r WHERE r.staffId = :staffId AND r.deleted = false")
    Double avgScoreByStaffId(Long staffId);

    @Query(
            "SELECT AVG(r.score) FROM SessionRating r WHERE r.createTime >= :since AND r.deleted = false")
    Double avgScoreSince(LocalDateTime since);

    @Query(
            "SELECT r.score, COUNT(r) FROM SessionRating r WHERE r.createTime >= :since AND r.deleted = false GROUP BY r.score ORDER BY r.score")
    List<Object[]> scoreDistributionSince(LocalDateTime since);

    @Query(
            "SELECT r FROM SessionRating r WHERE r.score <= :maxScore AND r.createTime > :since AND r.deleted = false")
    List<SessionRating> findLowScoreRatings(int maxScore, LocalDateTime since);
}
