package com.xuejiai.aaf.module.digitalhuman.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import com.xuejiai.aaf.module.digitalhuman.domain.DigitalHuman;
import com.xuejiai.aaf.module.digitalhuman.vo.DigitalHumanAppearance;
import com.xuejiai.aaf.module.digitalhuman.vo.DigitalHumanCreateDTO;
import com.xuejiai.aaf.module.digitalhuman.vo.DigitalHumanModelConfig;
import com.xuejiai.aaf.module.digitalhuman.vo.DigitalHumanVO;
import com.xuejiai.aaf.module.digitalhuman.vo.DigitalHumanVoiceConfig;

/** 数字人对象转换器；更新由 Patch 应用，状态与统计字段由 Service 维护。 */
@Mapper
public interface DigitalHumanConvert {

    DigitalHumanConvert INSTANCE = Mappers.getMapper(DigitalHumanConvert.class);

    /** 状态与统计字段使用实体默认值，助理关联只能通过专用命令设置。 */
    @Mapping(target = "assistantId", ignore = true)
    @Mapping(target = "syncModelConfig", ignore = true)
    @Mapping(target = "videoCount", ignore = true)
    @Mapping(target = "totalDuration", ignore = true)
    @Mapping(target = "creditsConsumed", ignore = true)
    @Mapping(target = "lastUsedAt", ignore = true)
    @Mapping(target = "status", ignore = true)
    DigitalHuman toEntity(DigitalHumanCreateDTO dto);

    DigitalHuman.AppearanceConfig toEntity(DigitalHumanAppearance source);

    DigitalHuman.VoiceConfig toEntity(DigitalHumanVoiceConfig source);

    DigitalHuman.ModelConfig toEntity(DigitalHumanModelConfig source);

    @Mapping(target = "ownedByCurrentUser", ignore = true)
    DigitalHumanVO toVO(DigitalHuman entity);
}
