package com.xuejiai.aaf.module.ai.aigc.voice.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.xuejiai.aaf.module.ai.aigc.voice.domain.AiClonedVoice;

/** 声音复刻记录仓储。 */
public interface AiClonedVoiceRepository
        extends JpaRepository<AiClonedVoice, Long>, JpaSpecificationExecutor<AiClonedVoice> {

    Optional<AiClonedVoice> findByVoice(String voice);
}
