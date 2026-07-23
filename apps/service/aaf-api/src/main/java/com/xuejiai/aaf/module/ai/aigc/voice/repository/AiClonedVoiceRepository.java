package com.xuejiai.aaf.module.ai.aigc.voice.repository;

import java.util.Optional;

import com.xuejiai.aaf.framework.crud.CrudEntityRepository;
import com.xuejiai.aaf.module.ai.aigc.voice.domain.AiClonedVoice;

/** 声音复刻记录仓储。 */
public interface AiClonedVoiceRepository extends CrudEntityRepository<AiClonedVoice> {

    Optional<AiClonedVoice> findByVoice(String voice);
}
