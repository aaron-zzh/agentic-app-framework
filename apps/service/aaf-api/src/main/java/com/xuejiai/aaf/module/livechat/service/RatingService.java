package com.xuejiai.aaf.module.livechat.service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.module.livechat.domain.SessionRating;
import com.xuejiai.aaf.module.livechat.repository.SessionRatingRepository;
import com.xuejiai.aaf.module.livechat.vo.RatingStatVO;
import com.xuejiai.aaf.module.livechat.vo.RatingSubmitDTO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 满意度评价服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RatingService {

    private final SessionRatingRepository ratingRepository;
    private final ChatSessionService sessionService;

    /** 差评阈值 */
    private static final int LOW_SCORE_THRESHOLD = 2;

    /**
     * 提交评价。
     */
    @Transactional
    public SessionRating submit(RatingSubmitDTO dto) {
        // 防止重复评价
        ratingRepository.findBySessionId(dto.sessionId()).ifPresent(r -> {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "该会话已评价");
        });
        var rating = new SessionRating();
        rating.setSessionId(dto.sessionId());
        rating.setUserId(dto.userId());
        rating.setStaffId(dto.staffId());
        rating.setScore(dto.score());
        rating.setComment(dto.comment());
        var saved = ratingRepository.save(rating);
        // 差评预警
        if (dto.score() <= LOW_SCORE_THRESHOLD) {
            handleLowScore(saved);
        }
        return saved;
    }

    /**
     * 获取评价统计。
     */
    public RatingStatVO getStatistics(LocalDateTime since) {
        var avgScore = ratingRepository.avgScoreSince(since);
        var distribution = ratingRepository.scoreDistributionSince(since);
        Map<Integer, Long> scoreMap = new LinkedHashMap<>();
        for (int i = 1; i <= 5; i++) {
            scoreMap.put(i, 0L);
        }
        long total = 0;
        for (var row : distribution) {
            int score = (Integer) row[0];
            long count = (Long) row[1];
            scoreMap.put(score, count);
            total += count;
        }
        return new RatingStatVO(
                avgScore != null ? avgScore : 0.0,
                total,
                scoreMap);
    }

    /**
     * 按坐席统计平均分。
     */
    public Double getStaffAvgScore(Long staffId) {
        return ratingRepository.avgScoreByStaffId(staffId);
    }

    /**
     * 查询差评列表（供主管查看）。
     */
    public List<SessionRating> getLowScoreRatings(LocalDateTime since) {
        return ratingRepository.findByScoreLessThanEqualAndCreateTimeAfter(LOW_SCORE_THRESHOLD, since);
    }

    /**
     * 差评预警处理。
     */
    private void handleLowScore(SessionRating rating) {
        log.warn("差评预警: sessionId={}, staffId={}, score={}, comment={}",
                rating.getSessionId(), rating.getStaffId(), rating.getScore(), rating.getComment());
        // 后续可对接 messaging.MessageService 通知主管
    }
}
