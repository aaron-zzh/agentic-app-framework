package com.xuejiai.aaf.module.channel.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.channel.domain.ChannelConfig;

/** 渠道配置数据访问层。 */
public interface ChannelConfigRepository extends JpaRepository<ChannelConfig, Long> {

    Optional<ChannelConfig> findByChannelTypeAndDeletedFalse(String channelType);

    List<ChannelConfig> findByStatusAndDeletedFalse(Integer status);
}
