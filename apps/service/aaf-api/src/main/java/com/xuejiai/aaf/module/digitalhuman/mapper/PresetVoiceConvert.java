package com.xuejiai.aaf.module.digitalhuman.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import com.xuejiai.aaf.module.digitalhuman.domain.PresetVoice;
import com.xuejiai.aaf.module.digitalhuman.vo.PresetVoiceVO;

/** 预设音色对象转换器。 */
@Mapper
public interface PresetVoiceConvert {

    PresetVoiceConvert INSTANCE = Mappers.getMapper(PresetVoiceConvert.class);

    PresetVoiceVO toVO(PresetVoice entity);
}
