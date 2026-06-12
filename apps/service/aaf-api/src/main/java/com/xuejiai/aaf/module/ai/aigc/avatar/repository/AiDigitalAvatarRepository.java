package com.xuejiai.aaf.module.ai.aigc.avatar.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.xuejiai.aaf.module.ai.aigc.avatar.domain.AiDigitalAvatar;

/** 数字人形象仓储。 */
public interface AiDigitalAvatarRepository
        extends JpaRepository<AiDigitalAvatar, Long>, JpaSpecificationExecutor<AiDigitalAvatar> {}
