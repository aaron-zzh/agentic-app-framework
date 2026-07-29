package com.xuejiai.aaf.module.channel.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.channel.domain.BotBinding;

/** 机器人绑定数据访问层。 */
public interface BotBindingRepository extends JpaRepository<BotBinding, Long> {

    List<BotBinding> findByPlatformIdAndStatusAndDeletedFalse(Long platformId, Integer status);
}
