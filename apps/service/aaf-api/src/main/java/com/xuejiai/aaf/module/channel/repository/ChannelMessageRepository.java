package com.xuejiai.aaf.module.channel.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.xuejiai.aaf.module.channel.domain.ChannelMessage;

/**
 * 渠道消息记录数据访问层。
 */
public interface ChannelMessageRepository extends JpaRepository<ChannelMessage, Long> {

    Page<ChannelMessage> findByExternalUserIdAndDeletedFalse(
            String externalUserId, Pageable pageable);

    List<ChannelMessage> findByUserIdAndDeletedFalse(Long userId);

    @Query("SELECT COUNT(m) FROM ChannelMessage m WHERE m.channelType = :channelType AND m.deleted = false")
    long countByChannelType(@Param("channelType") String channelType);

    @Query("SELECT COUNT(m) FROM ChannelMessage m WHERE m.channelType = :channelType AND m.deleted = false AND m.content LIKE '%error%'")
    long countErrorsByChannelType(@Param("channelType") String channelType);

    @Query("SELECT COUNT(m) FROM ChannelMessage m WHERE m.channelType = :channelType AND m.deleted = false AND m.messageTime BETWEEN :start AND :end")
    long countByChannelTypeAndTimeBetween(
            @Param("channelType") String channelType,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    @Query("SELECT COUNT(m) FROM ChannelMessage m WHERE m.channelType = :channelType AND m.direction = :direction AND m.deleted = false AND m.messageTime BETWEEN :start AND :end")
    long countByChannelTypeAndDirectionAndTimeBetween(
            @Param("channelType") String channelType,
            @Param("direction") String direction,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);
}
