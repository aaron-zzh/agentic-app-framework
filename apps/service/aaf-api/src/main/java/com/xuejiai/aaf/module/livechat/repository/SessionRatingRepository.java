package com.xuejiai.aaf.module.livechat.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.xuejiai.aaf.module.livechat.domain.SessionRating;

/** 满意度评价仓储。 */
public interface SessionRatingRepository extends JpaRepository<SessionRating, Long> {

    Optional<SessionRating> findBySessionId(Long sessionId);

    List<SessionRating> findByStaffId(Long staffId);

    @Query("SELECT AVG(r.score) FROM SessionRating r WHERE r.staffId = :staffId")
    Double avgScoreByStaffId(Long staffId);

    @Query("SELECT AVG(r.score) FROM SessionRating r WHERE r.createTime >= :since")
    Double avgScoreSince(LocalDateTime since);

    @Query(
            "SELECT r.score, COUNT(r) FROM SessionRating r WHERE r.createTime >= :since GROUP BY r.score ORDER BY r.score")
    List<Object[]> scoreDistributionSince(LocalDateTime since);

    List<SessionRating> findByScoreLessThanEqualAndCreateTimeAfter(int score, LocalDateTime since);
}
