package com.xuejiai.aaf.module.channel.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.common.enums.channel.ChannelTypeEnum;
import com.xuejiai.aaf.module.channel.domain.ChannelPlatform;

/** 渠道平台配置数据访问层。 */
public interface ChannelPlatformRepository extends JpaRepository<ChannelPlatform, Long> {

    List<ChannelPlatform> findByStatusAndDeletedFalse(Integer status);

    Optional<ChannelPlatform> findByTypeAndDeletedFalse(ChannelTypeEnum type);

    List<ChannelPlatform> findByTypeAndStatusAndDeletedFalse(ChannelTypeEnum type, Integer status);
}
