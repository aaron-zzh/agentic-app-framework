package com.xuejiai.aaf.module.livechat.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.common.enums.channel.ChannelTypeEnum;
import com.xuejiai.aaf.common.enums.livechat.SessionStatusEnum;
import com.xuejiai.aaf.module.livechat.domain.ChatSession;

public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {

    /** 查找用户当前活跃会话（未关闭） */
    Optional<ChatSession> findByExternalUserIdAndChannelTypeAndStatusNot(
            String externalUserId, ChannelTypeEnum channelType, SessionStatusEnum status);

    /** 待接入列表 */
    List<ChatSession> findByStatusOrderByPriorityDescCreateTimeAsc(SessionStatusEnum status);

    /** 坐席当前服务的会话 */
    List<ChatSession> findByStaffIdAndStatus(Long staffId, SessionStatusEnum status);

    /** 超时会话：用户无响应 */
    List<ChatSession> findByStatusAndLastActiveTimeBefore(
            SessionStatusEnum status, LocalDateTime before);

    /** 坐席无响应的等待会话 */
    List<ChatSession> findByStatusInAndLastActiveTimeBefore(
            List<SessionStatusEnum> statuses, LocalDateTime before);
}
