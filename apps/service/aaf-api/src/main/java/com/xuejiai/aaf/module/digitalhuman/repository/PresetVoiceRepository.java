package com.xuejiai.aaf.module.digitalhuman.repository;

import java.util.List;

import org.springframework.data.domain.Sort;

import com.xuejiai.aaf.framework.crud.CrudEntityRepository;
import com.xuejiai.aaf.module.digitalhuman.domain.PresetVoice;

/**
 * 预设音色数据访问层
 *
 * @author AaronZZH & Kiro
 */
public interface PresetVoiceRepository extends CrudEntityRepository<PresetVoice> {

    /**
     * 查询启用的预设音色
     */
    List<PresetVoice> findByIsActiveTrue(Sort sort);
}
